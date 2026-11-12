package com.anasikus.lifeassistant.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarPlanningDao {
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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFinanceTransaction(value: FinanceTransactionEntity)

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
