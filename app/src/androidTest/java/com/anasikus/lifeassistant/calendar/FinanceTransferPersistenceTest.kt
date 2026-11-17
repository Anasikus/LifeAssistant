package com.anasikus.lifeassistant.calendar

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.anasikus.lifeassistant.data.*
import com.anasikus.lifeassistant.domain.CalendarDateUtils
import com.anasikus.lifeassistant.domain.FinanceMessageParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FinanceTransferPersistenceTest {
    @Test fun neutralTransferLearnsWithoutPayrollLinkAndManualTypeOverridesDefault() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val dao = db.financeInboxDao()
            val at = System.currentTimeMillis() - 900000
            fun sms(key: String, sender: String, amount: Int, offset: Long) : FinanceInboxEntity {
                val body = "Счет карты 1234 перевод +${amount}р от $sender Баланс: 10000р"
                return FinanceInboxEntity(eventKey = key, source = "SMS", sender = "900", title = "900", body = body,
                    receivedAt = at + offset, ruleKey = FinanceMessageParser.ruleKey("SMS", "900", "900", body))
            }
            val first = sms("self", "АНАСТАСИЯ Д.", 50, 0)
            dao.capture(first)
            val message = dao.byEvent("self")!!
            assertTrue(message.proposedTransfer)
            dao.confirm(message.id, first.title, first.body, true, 5000, CalendarDateUtils.today(), null,
                "job", "Работа", true, transfer = true)
            val neutral = dao.transactionForMessage(message.id)!!
            assertTrue(neutral.transfer)
            assertFalse(neutral.income)
            assertNull(neutral.sourceKey)
            assertNull(neutral.expenseCategoryId)
            dao.capture(sms("self-next", "АНАСТАСИЯ Д.", 60, 180000))
            val next = dao.byEvent("self-next")!!
            assertTrue(next.automatic)
            assertTrue(dao.transactionForMessage(next.id)!!.transfer)
            assertEquals(6000L, dao.transactionForMessage(next.id)!!.amountCents)
            dao.capture(sms("alim", "АЛИМ Г.", 70, 360000))
            val alim = dao.byEvent("alim")!!
            assertFalse(alim.proposedTransfer)
            assertEquals(true, alim.proposedIncome)
            assertNull(dao.transactionForMessage(alim.id))
            dao.confirm(message.id, first.title, first.body, true, 5000, CalendarDateUtils.today(), null,
                "job", "Работа", true, transfer = false)
            dao.capture(sms("self-override", "АНАСТАСИЯ Д.", 80, 540000))
            val changed = dao.transactionForMessage(dao.byEvent("self-override")!!.id)!!
            assertFalse(changed.transfer)
            assertTrue(changed.income)
            assertEquals("job", changed.sourceKey)
        } finally { db.close() }
    }
}
