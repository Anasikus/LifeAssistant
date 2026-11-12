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

    @Test fun senderCanBeBlockedAndEnabledWithoutDeletingTransactions() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val dao = db.financeInboxDao()
            val first = incoming("one", 100, System.currentTimeMillis() - 600_000L)
            dao.capture(first)
            val id = dao.byEvent("one")!!.id
            dao.confirm(id, first.title, first.body, false, 10000L, com.anasikus.lifeassistant.domain.CalendarDateUtils.today(), null, null, null, true)
            dao.configureSender("SMS", "Bank", "Мой банк", false)
            dao.capture(incoming("two", 200, first.receivedAt + 180_000L))
            assertNull(dao.byEvent("two"))
            assertNotNull(dao.transactionForMessage(id))
            dao.configureSender("SMS", "Bank", "Мой банк", true)
            dao.capture(incoming("three", 300, first.receivedAt + 360_000L))
            assertTrue(dao.byEvent("three")!!.automatic)
            assertEquals("Мой банк", dao.rule(FinanceMessageParser.senderKey("SMS", "Bank"))!!.bankName)
            val third = dao.byEvent("three")!!
            dao.keepMessage(third.id, third.title, third.body)
            assertNull(dao.transactionForMessage(third.id))
            dao.capture(incoming("four", 400, first.receivedAt + 540_000L))
            assertEquals("SAVED", dao.byEvent("four")!!.status)
            assertEquals(1, db.calendarPlanningDao().financeTransactions().first().size)
        } finally { db.close() }
    }

    @Test fun bankTextRuleRequiresSameSenderAndDirectionAndUsesNewAmount() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val dao = db.financeInboxDao()
            val at = System.currentTimeMillis() - 900_000L
            fun sms(key: String, text: String, sender: String = "900", offset: Long = 0L) = FinanceInboxEntity(
                eventKey = key, source = "SMS", sender = sender, title = sender, body = text, receivedAt = at + offset,
                ruleKey = FinanceMessageParser.ruleKey("SMS", sender, sender, text))
            val first = sms("one", "Счет карты 1234 перевод из Т-Банка +50р от Иванова Баланс: 100р")
            dao.configureSender("SMS", "900", "Сбербанк", true)
            dao.capture(first)
            dao.confirm(dao.byEvent("one")!!.id, first.title, first.body, true, 5000L,
                com.anasikus.lifeassistant.domain.CalendarDateUtils.today(), null, "job:1", "Подработка", true, textFilter = "из Т-Банка")
            dao.capture(sms("two", "Счет карты 5678 перевод из Т-Банка +70р от Петрова Баланс: 170р", offset = 180_000L))
            val second = dao.byEvent("two")!!
            assertTrue(second.automatic)
            assertEquals(7000L, dao.transactionForMessage(second.id)!!.amountCents)
            assertEquals("job:1", dao.transactionForMessage(second.id)!!.sourceKey)
            dao.capture(sms("outgoing", "Счет карты 5678 перевод из Т-Банка 80р Баланс: 90р", offset = 360_000L))
            assertFalse(dao.byEvent("outgoing")!!.automatic)
            dao.capture(sms("otherBank", "Счет карты 5678 перевод из Альфа-Банка +90р Баланс: 180р", offset = 540_000L))
            assertFalse(dao.byEvent("otherBank")!!.automatic)
            dao.capture(sms("otherSender", first.body, sender = "Other", offset = 720_000L))
            assertFalse(dao.byEvent("otherSender")!!.automatic)
            assertEquals(2, db.calendarPlanningDao().financeTransactions().first().size)
            dao.ignore(second.id, true)
            dao.capture(sms("ignoredFilter", "Счет карты 1111 перевод из Т-Банка +100р от Сидорова Баланс: 280р", offset = 800_000L))
            assertNull(dao.byEvent("ignoredFilter"))
        } finally { db.close() }
    }
}
