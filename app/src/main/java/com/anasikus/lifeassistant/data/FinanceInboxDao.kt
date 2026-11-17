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

    @Query("SELECT * FROM finance_inbox WHERE status = :status ORDER BY receivedAt DESC, id DESC")
    fun messagesWithStatus(status: String): Flow<List<FinanceInboxEntity>>

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

    @Query("SELECT * FROM finance_import_rules WHERE action = 'BLOCK_SENDER'")
    fun blockedSenders(): Flow<List<FinanceImportRuleEntity>>

    @Query("""
        DELETE FROM finance_inbox
        WHERE status IN ('PENDING', 'SAVED', 'IGNORED')
        AND NOT EXISTS (SELECT 1 FROM finance_transactions WHERE inboxMessageId = finance_inbox.id)
        AND ((source = :source AND sender = :sender)
            OR (:source = 'APP' AND source IN ('EMAIL', 'NOTIFICATION')
                AND (sender = :sender OR substr(sender, 1, length(:sender) + 3) = :sender || ' · ')))
    """)
    suspend fun purgeSender(source: String, sender: String): Int

    @Query("SELECT * FROM finance_inbox WHERE source = :source AND sender = :sender AND status IN ('PENDING', 'SAVED', 'IGNORED')")
    suspend fun unpostedFromSender(source: String, sender: String): List<FinanceInboxEntity>

    @Query("DELETE FROM finance_inbox WHERE id IN (:ids) AND status IN ('PENDING', 'SAVED', 'IGNORED') AND NOT EXISTS (SELECT 1 FROM finance_transactions WHERE inboxMessageId = finance_inbox.id)")
    suspend fun purgeUnposted(ids: List<Long>): Int

    suspend fun isSenderBlocked(source: String, sender: String): Boolean {
        if (source == "MANUAL") return false
        val app = FinanceMessageParser.appPackage(source, sender)
        if (app != null && rule(FinanceMessageParser.senderKey("APP", app))?.action == "BLOCK_SENDER") return true
        return rule(FinanceMessageParser.senderKey(source, sender))?.action == "BLOCK_SENDER"
    }

    @Transaction
    suspend fun configureSender(source: String, sender: String, bank: String, financial: Boolean) {
        saveRule(FinanceImportRuleEntity(
            key = FinanceMessageParser.senderKey(source, sender), source = source, sender = sender,
            template = "Все сообщения отправителя", action = if (financial) "FINANCIAL" else "BLOCK_SENDER",
            bankName = bank.trim().takeIf { it.isNotEmpty() }
        ))
        if (!financial) purgeSender(source, sender)
        else if (source == "APP") {
            rule(FinanceMessageParser.senderKey("NOTIFICATION", sender))?.let {
                if (it.action == "BLOCK_SENDER") saveRule(it.copy(action = "FINANCIAL"))
            }
        }
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
    suspend fun capture(value: FinanceInboxEntity): String {
        if (byEvent(value.eventKey) != null) return "ALREADY_STORED"
        if (isSenderBlocked(value.source, value.sender)) return "SENDER_BLOCKED"
        val exact = rule(value.ruleKey)
        if (exact?.action == "IGNORE" && value.source != "MANUAL") return "TEMPLATE_IGNORED"
        val parsed = FinanceMessageParser.message(value.title, value.body, value.source)
        // A broad text rule must also agree with the detected direction. Two matches require review.
        val matching = filteredRules(value.source, value.sender).filter {
            it.action in listOf("AUTO", "IGNORE", "KEEP") &&
                ((it.action == "IGNORE" && it.income == null) || it.transfer == true || (it.income == parsed.income && parsed.income != null)) &&
                FinanceMessageParser.matchesFilter(it.textFilter.orEmpty(), value.title, value.body)
        }
        val learned = when {
            exact?.action == "IGNORE" -> exact
            matching.any { it.action == "IGNORE" } -> matching.first { it.action == "IGNORE" }
            exact?.action == "KEEP" -> exact
            matching.size == 1 -> matching.single()
            matching.isEmpty() -> exact
            else -> null
        }
        if (learned?.action == "IGNORE" && value.source != "MANUAL") return "TEMPLATE_IGNORED"
        val transfer = learned?.transfer ?: parsed.transfer
        val income = if (transfer) false else if (learned?.transfer == null && parsed.counterparty == "АЛИМ Г") true else learned?.income ?: parsed.income
        val automaticAmount = if (learned?.textFilter != null) parsed.preferredAmount else
            learned?.amountOrdinal?.let { parsed.amounts.getOrNull(it) }
                ?.takeIf { !it.balance && it.cents > 0L }?.cents
        val amount = automaticAmount ?: parsed.preferredAmount
        val possibleDuplicate = amount?.let {
            nearMessages(it, value.receivedAt - 120_000L, value.receivedAt + 120_000L)
                .any { prior -> prior.proposedTransfer == transfer && prior.proposedIncome == income }
        } ?: false
        val id = insertMessage(value.copy(
            ruleKey = learned?.key ?: value.ruleKey,
            status = if (learned?.action == "KEEP") "SAVED" else "PENDING",
            proposedIncome = income, proposedTransfer = transfer,
            proposedAmountCents = amount,
            expenseCategoryId = learned?.expenseCategoryId,
            incomeSourceKey = learned?.incomeSourceKey,
            incomeSourceName = learned?.incomeSourceName,
            possibleDuplicate = possibleDuplicate
        ))
        if (id <= 0L) return "ALREADY_STORED"
        val categoryValid = transfer || income == true || (learned?.expenseCategoryId?.let { categoryExists(it) == 1 } ?: true)
        if (value.source != "MANUAL" && learned?.action == "AUTO" && income != null && automaticAmount != null &&
            !possibleDuplicate && categoryValid
        ) {
            confirm(
                id, value.title, value.body, income, automaticAmount,
                FinanceMessageParser.date(value.receivedAt), learned.expenseCategoryId,
                learned.incomeSourceKey, learned.incomeSourceName, learn = false, automatic = true, transfer = transfer
            )
            return "POSTED"
        }
        return if (learned?.action == "KEEP") "SAVED" else "PENDING"
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
        textFilter: String? = null,
        transfer: Boolean = false
    ) {
        require(amount > 0L)
        require(date.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
        require(date <= com.anasikus.lifeassistant.domain.CalendarDateUtils.today())
        com.anasikus.lifeassistant.domain.CalendarDateUtils.parseCalendar(date)
        val old = message(id) ?: return
        if (old.status == "DELETED") return
        val isIncome = income && !transfer
        val filter = textFilter?.trim()?.takeIf { it.isNotEmpty() }
        require(filter == null || FinanceMessageParser.matchesFilter(filter, title, body))
        val priorRule = rule(old.ruleKey)
        val key = when {
            automatic -> old.ruleKey
            filter != null -> "filter:" + FinanceMessageParser.hash(old.source + "\n" + old.sender + "\n" + (if (transfer) "TRANSFER" else isIncome.toString()) + "\n" + filter.lowercase(java.util.Locale.ROOT))
            else -> FinanceMessageParser.ruleKey(old.source, old.sender, title, body)
        }
        val existing = transactionForMessage(id)
        val category = categoryId?.takeIf { categoryExists(it) == 1 }
        val entry = FinanceTransactionEntity(
            id = existing?.id ?: 0,
            date = date,
            income = isIncome, transfer = transfer,
            amountCents = amount,
            note = title.ifBlank { old.sender } + " · " + body.take(500),
            sourceKey = if (isIncome) sourceKey else null,
            sourceName = if (isIncome) sourceName else null,
            expenseCategoryId = if (isIncome || transfer) null else category,
            inboxMessageId = id
        )
        if (existing == null) insertTransaction(entry) else updateTransaction(entry)
        updateMessage(old.copy(
            title = title, body = body, ruleKey = key, status = "POSTED",
            proposedIncome = isIncome, proposedTransfer = transfer, proposedAmountCents = amount,
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
                income = isIncome, transfer = transfer, amountOrdinal = ordinal,
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
        if (learn) {
            val ignoredRule = learned?.copy(action = "IGNORE") ?: FinanceImportRuleEntity(
                key = old.ruleKey, source = old.source, sender = old.sender,
                template = FinanceMessageParser.template(old.title, old.body), action = "IGNORE"
            )
            saveRule(ignoredRule)
            val ids = unpostedFromSender(old.source, old.sender).filter { candidate ->
                candidate.id != id && (candidate.ruleKey == old.ruleKey ||
                    FinanceMessageParser.template(candidate.title, candidate.body) == ignoredRule.template ||
                    (ignoredRule.textFilter != null &&
                        (ignoredRule.income == null || ignoredRule.transfer == true || ignoredRule.income == FinanceMessageParser.message(candidate.title, candidate.body, candidate.source).income) &&
                        FinanceMessageParser.matchesFilter(ignoredRule.textFilter, candidate.title, candidate.body)))
            }.map { it.id }
            // Stay below SQLite's bind parameter limit even for thousands of messages.
            ids.chunked(400).forEach { purgeUnposted(it) }
        } else if (learned?.action == "AUTO") saveRule(learned.copy(action = "REVIEW"))
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
        val entry = financialTransaction(id)?.takeUnless { it.income || it.transfer } ?: return
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
