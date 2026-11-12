package com.anasikus.lifeassistant.calendar

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.anasikus.lifeassistant.data.*
import com.anasikus.lifeassistant.domain.FinanceMessageParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FinanceInboxPersistenceTest {
    private fun incoming(key: String, cents: Int, at: Long): FinanceInboxEntity {
        val body = "Покупка $cents,00 RUB в SHOP. Баланс 900,00 RUB"
        return FinanceInboxEntity(eventKey = key, source = "SMS", sender = "Bank", title = "Bank", body = body,
            receivedAt = at, ruleKey = FinanceMessageParser.ruleKey("SMS", "Bank", "Bank", body))
    }

    @Test fun confirmationIsIdempotentAndNextMessageUsesItsOwnAmount() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val dao = db.financeInboxDao()
            db.calendarPlanningDao().saveExpenseCategory(ExpenseCategoryEntity(1, "Продукты"))
            val first = incoming("one", 100, System.currentTimeMillis() - 600_000L)
            dao.capture(first)
            val id = dao.byEvent(first.eventKey)!!.id
            repeat(2) { dao.confirm(id, first.title, first.body, false, 10000L, com.anasikus.lifeassistant.domain.CalendarDateUtils.today(), 1, null, null, true) }
            assertEquals(1, db.calendarPlanningDao().financeTransactions().first().size)
            val next = incoming("two", 200, first.receivedAt + 180_000L)
            dao.capture(next)
            dao.capture(next)
            assertEquals(2, db.calendarPlanningDao().financeTransactions().first().size)
            assertEquals(20000L, dao.transactionForMessage(dao.byEvent("two")!!.id)!!.amountCents)
            assertTrue(dao.byEvent("two")!!.automatic)
            dao.deleteMessage(id)
            dao.capture(first)
            assertEquals("DELETED", dao.byEvent("one")!!.status)
            assertEquals("REVIEW", dao.rule(first.ruleKey)!!.action)
        } finally { db.close() }
    }

    @Test fun ignoringTemplateStopsNewCaptureAndCategoryCorrectionUpdatesRule() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val dao = db.financeInboxDao()
            val first = incoming("one", 100, System.currentTimeMillis() - 600_000L)
            dao.capture(first)
            val id = dao.byEvent("one")!!.id
            db.calendarPlanningDao().saveExpenseCategory(ExpenseCategoryEntity(2, "Кафе"))
            dao.confirm(id, first.title, first.body, false, 10000L, com.anasikus.lifeassistant.domain.CalendarDateUtils.today(), null, null, null, true)
            dao.setFinancialCategory(dao.transactionForMessage(id)!!.id, 2)
            assertEquals(2, dao.rule(first.ruleKey)!!.expenseCategoryId!!)
            dao.ignore(id, true)
            dao.capture(incoming("two", 200, first.receivedAt + 180_000L))
            assertNull(dao.byEvent("two"))
            assertTrue(db.calendarPlanningDao().financeTransactions().first().isEmpty())
            dao.forgetRule(first.ruleKey)
            val second = incoming("two", 200, first.receivedAt + 180_000L)
            dao.capture(second)
            dao.keepMessage(dao.byEvent("two")!!.id, second.title, second.body)
            dao.capture(incoming("three", 300, first.receivedAt + 360_000L))
            assertEquals("SAVED", dao.byEvent("three")!!.status)
            assertTrue(db.calendarPlanningDao().financeTransactions().first().isEmpty())
        } finally { db.close() }
    }
}
