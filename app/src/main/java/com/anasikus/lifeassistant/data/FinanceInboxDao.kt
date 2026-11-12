package com.anasikus.lifeassistant.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.anasikus.lifeassistant.domain.FinanceMessageParser
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceInboxDao {
    @Query("SELECT * FROM finance_inbox WHERE status != 'DELETED' ORDER BY receivedAt DESC, id DESC")
    fun messages(): Flow<List<FinanceInboxEntity>>

    @Query("SELECT * FROM finance_import_rules ORDER BY source, sender")
    fun rules(): Flow<List<FinanceImportRuleEntity>>

    @Query("SELECT * FROM finance_inbox WHERE id = :id")
    suspend fun message(id: Long): FinanceInboxEntity?

    @Query("SELECT * FROM finance_inbox WHERE eventKey = :key LIMIT 1")
    suspend fun byEvent(key: String): FinanceInboxEntity?

    @Query("SELECT * FROM finance_import_rules WHERE `key` = :key")
    suspend fun rule(key: String): FinanceImportRuleEntity?

    @Query("SELECT * FROM finance_import_rules WHERE source = :source AND sender = :sender AND textFilter IS NOT NULL")
    suspend fun filteredRules(source: String, sender: String): List<FinanceImportRuleEntity>

    @Transaction
    suspend fun configureSender(source: String, sender: String, bank: String, financial: Boolean) {
        saveRule(FinanceImportRuleEntity(
            key = FinanceMessageParser.senderKey(source, sender), source = source, sender = sender,
            template = "Все сообщения отправителя", action = if (financial) "FINANCIAL" else "BLOCK_SENDER",
            bankName = bank.trim().takeIf { it.isNotEmpty() }
        ))
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(value: FinanceInboxEntity): Long

    @Update
    suspend fun updateMessage(value: FinanceInboxEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRule(value: FinanceImportRuleEntity)

    @Query("DELETE FROM finance_import_rules WHERE `key` = :key")
    suspend fun forgetRule(key: String)

    @Query("SELECT * FROM finance_transactions WHERE inboxMessageId = :id LIMIT 1")
    suspend fun transactionForMessage(id: Long): FinanceTransactionEntity?

    @Query("SELECT * FROM finance_transactions WHERE id = :id")
    suspend fun financialTransaction(id: Int): FinanceTransactionEntity?

    @Query("SELECT * FROM finance_inbox WHERE status = 'POSTED' AND proposedAmountCents = :amount AND receivedAt BETWEEN :start AND :end")
    suspend fun nearMessages(amount: Long, start: Long, end: Long): List<FinanceInboxEntity>

    @Query("SELECT COUNT(*) FROM expense_categories WHERE id = :id")
    suspend fun categoryExists(id: Int): Int

    @Insert
    suspend fun insertTransaction(value: FinanceTransactionEntity): Long

    @Update
    suspend fun updateTransaction(value: FinanceTransactionEntity)

    @Query("DELETE FROM finance_transactions WHERE id = :id")
    suspend fun removeTransaction(id: Int)

    /** Обе точки входа используют одну транзакцию и уникальный eventKey. */
    @Transaction
    suspend fun capture(value: FinanceInboxEntity) {
        if (byEvent(value.eventKey) != null) return
        val senderRule = rule(FinanceMessageParser.senderKey(value.source, value.sender))
        if (senderRule?.action == "BLOCK_SENDER" && value.source != "MANUAL") return
        val parsed = FinanceMessageParser.message(value.title, value.body, value.source)
        // A broad text rule must also agree with the detected direction. Two matches require review.
        val matching = filteredRules(value.source, value.sender).filter {
            it.action in listOf("AUTO", "IGNORE", "KEEP") && it.income == parsed.income && parsed.income != null &&
                FinanceMessageParser.matchesFilter(it.textFilter.orEmpty(), value.title, value.body)
        }
        val exact = rule(value.ruleKey)
        val learned = when {
            exact?.action in listOf("IGNORE", "KEEP") -> exact
            matching.size == 1 -> matching.single()
            matching.isEmpty() -> exact
            else -> null
        }
        if (learned?.action == "IGNORE" && value.source != "MANUAL") return
        val automaticAmount = if (learned?.textFilter != null) parsed.preferredAmount else
            learned?.amountOrdinal?.let { parsed.amounts.getOrNull(it) }
                ?.takeIf { !it.balance && it.cents > 0L }?.cents
        val amount = automaticAmount ?: parsed.preferredAmount
        val possibleDuplicate = amount?.let {
            nearMessages(it, value.receivedAt - 120_000L, value.receivedAt + 120_000L)
                .any { prior -> prior.proposedIncome == (learned?.income ?: parsed.income) }
        } ?: false
        val id = insertMessage(value.copy(
            ruleKey = learned?.key ?: value.ruleKey,
            status = if (learned?.action == "KEEP") "SAVED" else "PENDING",
            proposedIncome = learned?.income ?: parsed.income,
            proposedAmountCents = amount,
            expenseCategoryId = learned?.expenseCategoryId,
            incomeSourceKey = learned?.incomeSourceKey,
            incomeSourceName = learned?.incomeSourceName,
            possibleDuplicate = possibleDuplicate
        ))
        if (id <= 0L) return
        val categoryValid = learned?.expenseCategoryId?.let { categoryExists(it) == 1 } ?: true
        if (value.source != "MANUAL" && learned?.action == "AUTO" && learned.income != null && automaticAmount != null &&
            !possibleDuplicate && categoryValid
        ) {
            confirm(
                id, value.title, value.body, learned.income, automaticAmount,
                FinanceMessageParser.date(value.receivedAt), learned.expenseCategoryId,
                learned.incomeSourceKey, learned.incomeSourceName, learn = false, automatic = true
            )
        }
    }

    @Transaction
    suspend fun confirm(
        id: Long,
        title: String,
        body: String,
        income: Boolean,
        amount: Long,
        date: String,
        categoryId: Int?,
        sourceKey: String?,
        sourceName: String?,
        learn: Boolean,
        automatic: Boolean = false,
        textFilter: String? = null
    ) {
        require(amount > 0L)
        require(date.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
        require(date <= com.anasikus.lifeassistant.domain.CalendarDateUtils.today())
        com.anasikus.lifeassistant.domain.CalendarDateUtils.parseCalendar(date)
        val old = message(id) ?: return
        if (old.status == "DELETED") return
        val filter = textFilter?.trim()?.takeIf { it.isNotEmpty() }
        require(filter == null || FinanceMessageParser.matchesFilter(filter, title, body))
        val priorRule = rule(old.ruleKey)
        val key = when {
            automatic -> old.ruleKey
            filter != null -> "filter:" + FinanceMessageParser.hash(old.source + "\n" + old.sender + "\n" + income + "\n" + filter.lowercase(java.util.Locale.ROOT))
            else -> FinanceMessageParser.ruleKey(old.source, old.sender, title, body)
        }
        val existing = transactionForMessage(id)
        val category = categoryId?.takeIf { categoryExists(it) == 1 }
        val entry = FinanceTransactionEntity(
            id = existing?.id ?: 0,
            date = date,
            income = income,
            amountCents = amount,
            note = title.ifBlank { old.sender } + " · " + body.take(500),
            sourceKey = if (income) sourceKey else null,
            sourceName = if (income) sourceName else null,
            expenseCategoryId = if (income) null else category,
            inboxMessageId = id
        )
        if (existing == null) insertTransaction(entry) else updateTransaction(entry)
        updateMessage(old.copy(
            title = title, body = body, ruleKey = key, status = "POSTED",
            proposedIncome = income, proposedAmountCents = amount,
            expenseCategoryId = entry.expenseCategoryId, incomeSourceKey = entry.sourceKey,
            incomeSourceName = entry.sourceName, automatic = automatic, transactionDate = date
        ))
        if (key != old.ruleKey) rule(old.ruleKey)?.let { saveRule(it.copy(action = "REVIEW")) }
        if (!learn && !automatic) rule(key)?.let { if (it.action == "AUTO") saveRule(it.copy(action = "REVIEW")) }
        if (learn) {
            val amounts = FinanceMessageParser.message(title, body, old.source).amounts
            val ordinal = amounts.indices.singleOrNull { !amounts[it].balance && amounts[it].cents == amount }
            saveRule(FinanceImportRuleEntity(
                key = key, source = old.source, sender = old.sender,
                template = FinanceMessageParser.template(title, body),
                action = if (ordinal == null || old.source == "MANUAL") "REVIEW" else "AUTO",
                income = income, amountOrdinal = ordinal,
                expenseCategoryId = entry.expenseCategoryId,
                incomeSourceKey = entry.sourceKey, incomeSourceName = entry.sourceName,
                bankName = rule(FinanceMessageParser.senderKey(old.source, old.sender))?.bankName ?: priorRule?.bankName,
                textFilter = filter
            ))
        }
    }

    @Transaction
    suspend fun keepMessage(id: Long, title: String, body: String) {
        val old = message(id)?.takeUnless { it.status == "DELETED" } ?: return
        transactionForMessage(id)?.let { removeTransaction(it.id) }
        val key = FinanceMessageParser.ruleKey(old.source, old.sender, title, body)
        if (key != old.ruleKey) rule(old.ruleKey)?.let { saveRule(it.copy(action = "REVIEW")) }
        updateMessage(old.copy(title = title, body = body, ruleKey = key, status = "SAVED", automatic = false))
        saveRule(FinanceImportRuleEntity(key, old.source, old.sender,
            FinanceMessageParser.template(title, body), action = "KEEP"))
    }

    @Transaction
    suspend fun ignore(id: Long, learn: Boolean) {
        val old = message(id) ?: return
        if (old.status == "DELETED") return
        transactionForMessage(id)?.let { removeTransaction(it.id) }
        updateMessage(old.copy(status = "IGNORED", automatic = false))
        val learned = rule(old.ruleKey)
        if (learn) saveRule(learned?.copy(action = "IGNORE") ?: FinanceImportRuleEntity(
            key = old.ruleKey, source = old.source, sender = old.sender,
            template = FinanceMessageParser.template(old.title, old.body), action = "IGNORE"
        )) else if (learned?.action == "AUTO") saveRule(learned.copy(action = "REVIEW"))
    }

    @Transaction
    suspend fun deleteMessage(id: Long) {
        val old = message(id) ?: return
        transactionForMessage(id)?.let { removeTransaction(it.id) }
        rule(old.ruleKey)?.let { if (it.action == "AUTO") saveRule(it.copy(action = "REVIEW")) }
        updateMessage(old.copy(status = "DELETED", title = "", body = "", automatic = false))
    }

    @Transaction
    suspend fun deleteFinancialTransaction(id: Int) {
        val entry = financialTransaction(id) ?: return
        entry.inboxMessageId?.let { inboxId ->
            message(inboxId)?.let { old ->
                rule(old.ruleKey)?.let { if (it.action == "AUTO") saveRule(it.copy(action = "REVIEW")) }
                updateMessage(old.copy(status = "DELETED", title = "", body = "", automatic = false))
            }
        }
        removeTransaction(id)
    }

    @Transaction
    suspend fun setFinancialCategory(id: Int, categoryId: Int?) {
        val entry = financialTransaction(id)?.takeUnless { it.income } ?: return
        val category = categoryId?.takeIf { categoryExists(it) == 1 }
        updateTransaction(entry.copy(expenseCategoryId = category))
        entry.inboxMessageId?.let { inboxId ->
            message(inboxId)?.let { old ->
                updateMessage(old.copy(expenseCategoryId = category))
                rule(old.ruleKey)?.let { saveRule(it.copy(expenseCategoryId = category)) }
            }
        }
    }
}
