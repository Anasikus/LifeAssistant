package com.anasikus.lifeassistant.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarPlanningDao {
    @Query("SELECT * FROM income_categories ORDER BY name")
    fun incomeCategories(): Flow<List<IncomeCategoryEntity>>

    @Query("SELECT * FROM income_categories WHERE id = :id")
    suspend fun incomeCategory(id: Int): IncomeCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun writeIncomeCategory(value: IncomeCategoryEntity)

    @Query("UPDATE finance_import_rules SET incomeSourceName = :name WHERE incomeSourceKey LIKE 'income:%' AND incomeSourceName = :oldName")
    suspend fun renameIncomeRules(oldName: String, name: String)

    @Query("UPDATE finance_import_rules SET action = 'REVIEW', incomeSourceKey = NULL, incomeSourceName = NULL WHERE incomeSourceKey LIKE 'income:%' AND incomeSourceName = :name")
    suspend fun resetIncomeRules(name: String)

    @Transaction
    suspend fun saveIncomeCategory(value: IncomeCategoryEntity) {
        incomeCategory(value.id)?.let { renameIncomeRules(it.name, value.name) }
        writeIncomeCategory(value)
    }

    @Query("UPDATE finance_transactions SET incomeCategoryId = :categoryId WHERE id = :id AND income = 1 AND transfer = 0")
    suspend fun setIncomeCategory(id: Int, categoryId: Int?)

    @Query("UPDATE finance_transactions SET incomeCategoryId = NULL WHERE incomeCategoryId = :id")
    suspend fun detachIncomeCategory(id: Int)

    @Query("DELETE FROM income_categories WHERE id = :id")
    suspend fun removeIncomeCategory(id: Int)

    @Transaction
    suspend fun deleteIncomeCategory(id: Int) {
        incomeCategory(id)?.let { resetIncomeRules(it.name) }
        detachIncomeCategory(id)
        removeIncomeCategory(id)
    }

    @Query("SELECT * FROM expense_categories ORDER BY name")
    fun expenseCategories(): Flow<List<ExpenseCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveExpenseCategory(value: ExpenseCategoryEntity)

    @Query("UPDATE finance_transactions SET expenseCategoryId = NULL WHERE expenseCategoryId = :id")
    suspend fun detachExpenseCategory(id: Int)

    @Query("UPDATE payment_plans SET categoryId = NULL WHERE categoryId = :id")
    suspend fun detachPlanCategory(id: Int)

    @Query("DELETE FROM expense_categories WHERE id = :id")
    suspend fun removeExpenseCategory(id: Int)

    @Query("UPDATE finance_inbox SET expenseCategoryId = NULL WHERE expenseCategoryId = :id")
    suspend fun detachInboxCategory(id: Int)

    @Query("UPDATE finance_import_rules SET expenseCategoryId = NULL, action = 'REVIEW' WHERE expenseCategoryId = :id")
    suspend fun resetImportCategory(id: Int)

    @Transaction
    suspend fun deleteExpenseCategory(id: Int) {
        detachExpenseCategory(id)
        detachPlanCategory(id)
        detachInboxCategory(id)
        resetImportCategory(id)
        removeExpenseCategory(id)
    }

    @Query("SELECT * FROM payment_plans ORDER BY title")
    fun paymentPlans(): Flow<List<PaymentPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePaymentPlan(value: PaymentPlanEntity)

    @Query("DELETE FROM payment_plans WHERE id = :id")
    suspend fun deletePaymentPlan(id: Int)

    @Query("SELECT * FROM finance_transactions ORDER BY date DESC, id DESC")
    fun financeTransactions(): Flow<List<FinanceTransactionEntity>>

    @Query("SELECT id FROM income_categories WHERE name = :name LIMIT 1")
    suspend fun incomeCategoryByName(name: String): Int?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFinanceTransaction(value: FinanceTransactionEntity)

    suspend fun categorizeIncome(value: FinanceTransactionEntity): FinanceTransactionEntity {
        if (!value.income || value.transfer) return value
        if (value.sourceKey == null && value.incomeCategoryId != null) {
            val category = incomeCategory(value.incomeCategoryId) ?: return value.copy(incomeCategoryId = null)
            return value.copy(sourceKey = "income:" + com.anasikus.lifeassistant.domain.FinanceMessageParser.hash(category.name.lowercase(java.util.Locale.ROOT)), sourceName = category.name)
        }
        if (value.sourceKey == null || value.sourceName.isNullOrBlank()) return value
        val name = checkNotNull(value.sourceName)
        var id = incomeCategoryByName(name)
        if (id == null) { writeIncomeCategory(IncomeCategoryEntity(name = name)); id = incomeCategoryByName(name) }
        return value.copy(incomeCategoryId = id)
    }

    @Transaction
    suspend fun addFinanceTransaction(value: FinanceTransactionEntity) { insertFinanceTransaction(categorizeIncome(value)) }

    @Update
    suspend fun writeFinanceTransaction(value: FinanceTransactionEntity)

    @Transaction
    suspend fun updateFinanceTransaction(value: FinanceTransactionEntity) {
        val result = categorizeIncome(value)
        writeFinanceTransaction(result)
        result.inboxMessageId?.let { id ->
            updateInboxTransaction(id, result.date, result.amountCents, result.expenseCategoryId, result.sourceKey, result.sourceName)
            updateInboxRule(id, result.expenseCategoryId, result.sourceKey, result.sourceName)
        }
    }

    @Query("UPDATE finance_inbox SET transactionDate=:date, proposedAmountCents=:amount, expenseCategoryId=:categoryId, incomeSourceKey=:sourceKey, incomeSourceName=:sourceName WHERE id=:id")
    suspend fun updateInboxTransaction(id: Long, date: String, amount: Long, categoryId: Int?, sourceKey: String?, sourceName: String?)

    @Query("UPDATE finance_import_rules SET expenseCategoryId=:categoryId, incomeSourceKey=:sourceKey, incomeSourceName=:sourceName WHERE key=(SELECT ruleKey FROM finance_inbox WHERE id=:id)")
    suspend fun updateInboxRule(id: Long, categoryId: Int?, sourceKey: String?, sourceName: String?)

    @Query("UPDATE finance_transactions SET expenseCategoryId = :categoryId WHERE id = :id AND income = 0")
    suspend fun setExpenseCategory(id: Int, categoryId: Int?)

    @Query("DELETE FROM finance_transactions WHERE id = :id")
    suspend fun deleteFinanceTransaction(id: Int)


    @Query("SELECT * FROM event_categories ORDER BY id")
    fun categories(): Flow<List<EventCategoryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCategory(value: EventCategoryEntity)
    @Query("SELECT COUNT(*) FROM event_categories")
    suspend fun categoryCount(): Int
    @Query("UPDATE calendar_events SET categoryId = NULL WHERE categoryId = :id")
    suspend fun detachCategory(id: Int)
    @Query("DELETE FROM event_categories WHERE id = :id")
    suspend fun removeCategory(id: Int)
    @Transaction
    suspend fun deleteCategory(id: Int) { detachCategory(id); removeCategory(id) }

    @Query("SELECT * FROM shift_patterns WHERE id = 1")
    fun pattern(): Flow<ShiftPatternEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePattern(value: ShiftPatternEntity)
    @Query("SELECT * FROM shift_days ORDER BY date")
    fun shifts(): Flow<List<ShiftDayEntity>>
    @Query("SELECT * FROM shift_days WHERE date = :date")
    suspend fun shift(date: String): ShiftDayEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveShift(value: ShiftDayEntity)
    @Query("DELETE FROM shift_days WHERE date = :date")
    suspend fun deleteShift(date: String)
    @Transaction
    suspend fun saveForecast(values: List<ShiftDayEntity>) {
        values.forEach { value ->
            val existing = shift(value.date)
            if (existing == null || existing.predicted) saveShift(value.copy(predicted = true))
        }
    }
    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteEvent(id: Int)
    @Query("DELETE FROM calendar_event_overrides WHERE eventId = :id")
    suspend fun deleteEventOverrides(id: Int)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun writeOverride(value: CalendarEventOverrideEntity)
    @Transaction
    suspend fun moveEventToShift(day: ShiftDayEntity, event: CalendarEventEntity?, occurrenceDate: String) {
        saveShift(day.copy(predicted = false))
        if (event != null) {
            if (event.repeatDaysMask == 0) { deleteEvent(event.id); deleteEventOverrides(event.id) }
            else writeOverride(CalendarEventOverrideEntity(event.id, occurrenceDate, day.date, day.startMinute, day.durationMinutes, true))
        }
    }

    @Query("SELECT * FROM special_dates ORDER BY date, title")
    fun specialDates(): Flow<List<SpecialDateEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSpecialDate(value: SpecialDateEntity)
    @Query("DELETE FROM special_dates WHERE id = :id")
    suspend fun deleteSpecialDate(id: Int)
    @Query("SELECT COUNT(*) FROM special_dates WHERE title = :title AND date = :date AND source = :source")
    suspend fun specialDateCount(title: String, date: String, source: String): Int
    @Transaction
    suspend fun importSpecialDates(values: List<SpecialDateEntity>) {
        values.forEach { if (specialDateCount(it.title, it.date, it.source) == 0) saveSpecialDate(it) }
    }
}
