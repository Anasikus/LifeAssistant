package com.anasikus.lifeassistant.calendar

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.anasikus.lifeassistant.data.*
import com.anasikus.lifeassistant.domain.FinanceRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlanningRevisionPersistenceTest {
    @Test fun frozenDraftCanBeEditedAndResumedWithoutLosingItsContent() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val dao = db.taskDao()
            dao.insertTask(TaskEntity(id = 1, title = "Сайт смен", description = "Идея", estimatedDurationMinutes = 90))
            db.calendarTaskPlacementDao().save(CalendarTaskPlacementEntity(1, "2026-11-19", 600, 90))
            dao.freeze(1, true, "2026-11-20")
            assertTrue(dao.task(1)!!.frozen)
            dao.editTask(dao.task(1)!!.copy(description = "Уточнённая идея"))
            assertTrue(dao.task(1)!!.frozen)
            dao.freeze(1, false, "2026-11-20")
            val restored = dao.task(1)!!
            assertFalse(restored.frozen)
            assertFalse(restored.archived)
            assertFalse(restored.completed)
            assertEquals("Уточнённая идея", restored.description)
            assertEquals(90, restored.estimatedDurationMinutes)
            assertTrue(db.calendarTaskPlacementDao().getAll().first().isEmpty())
        } finally { db.close() }
    }

    @Test fun incomeCategoriesRenameAndDeleteWithoutRemovingTransactionsOrChangingWorkLink() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val dao = db.calendarPlanningDao()
            dao.saveIncomeCategory(IncomeCategoryEntity(1, "Помощь родителей"))
            dao.addFinanceTransaction(FinanceTransactionEntity(date = "2026-11-20", income = true,
                amountCents = 50000, note = "", sourceKey = "event:1", incomeCategoryId = 1))
            dao.saveIncomeCategory(IncomeCategoryEntity(1, "Подарок"))
            assertEquals("Подарок", dao.incomeCategories().first().single().name)
            dao.deleteIncomeCategory(1)
            val transaction = dao.financeTransactions().first().single()
            assertNull(transaction.incomeCategoryId)
            assertEquals("event:1", transaction.sourceKey)
            assertEquals(50000L, transaction.amountCents)
        } finally { db.close() }
    }

    @Test fun futureExpenseCanBeRescheduledAndCancelledAndPlanRetainsCustomReminder() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val dao = db.calendarPlanningDao()
            dao.addFinanceTransaction(FinanceTransactionEntity(id = 1, date = "2026-11-21", income = false, amountCents = 2700000, note = "Аренда"))
            var rows = dao.financeTransactions().first()
            assertEquals(2700000L, FinanceRules.monthTotals(emptyList(), rows, "2026-11-01", "2026-11-20").reserved)
            dao.updateFinanceTransaction(rows.single().copy(date = "2026-12-01", amountCents = 2600000))
            rows = dao.financeTransactions().first()
            assertEquals(2600000L, FinanceRules.monthTotals(emptyList(), rows, "2026-11-01", "2026-11-21").reserved)
            db.financeInboxDao().deleteFinancialTransaction(1)
            assertTrue(dao.financeTransactions().first().isEmpty())
            dao.savePaymentPlan(PaymentPlanEntity(title = "VPN", amountCents = 50000, startDate = "2026-11-20",
                everyMonths = 1, reminderDays = 14, repeatUnit = "WEEK", repeatEvery = 2,
                reminderMinute = 1230, reminderRepeatDays = 3))
            val saved = dao.paymentPlans().first().single()
            assertEquals("WEEK", saved.repeatUnit)
            assertEquals(2, saved.repeatEvery)
            assertEquals(1230, saved.reminderMinute)
            assertEquals(3, saved.reminderRepeatDays)
        } finally { db.close() }
    }
}
