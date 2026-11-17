package com.anasikus.lifeassistant.calendar

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.anasikus.lifeassistant.data.AppDatabase
import com.anasikus.lifeassistant.data.FinanceInboxEntity
import com.anasikus.lifeassistant.domain.CalendarDateUtils
import com.anasikus.lifeassistant.domain.FinanceMessageParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FinanceInboxTabsTest {
    private fun incoming(key: String, source: String = "SMS", sender: String = "900", at: Long = System.currentTimeMillis()): FinanceInboxEntity {
        val body = "Покупка 50р в $key Баланс: 1000р"
        return FinanceInboxEntity(eventKey = key, source = source, sender = sender, title = "Банк", body = body,
            receivedAt = at, ruleKey = FinanceMessageParser.ruleKey(source, sender, "Банк", body))
    }

    @Test fun allHistoryTabsRemainAvailableAfterReopeningDatabase() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "finance-tabs-test.db"
        context.deleteDatabase(name)
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            val dao = db.financeInboxDao()
            val posted = incoming("posted")
            dao.capture(posted)
            dao.confirm(dao.byEvent("posted")!!.id, posted.title, posted.body, false, 5000,
                CalendarDateUtils.today(), null, null, null, false)
            val saved = incoming("saved")
            dao.capture(saved)
            dao.keepMessage(dao.byEvent("saved")!!.id, saved.title, saved.body)
            dao.capture(incoming("ignored"))
            dao.ignore(dao.byEvent("ignored")!!.id, true)
        } finally { db.close() }
        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            val dao = reopened.financeInboxDao()
            assertEquals("posted", dao.messagesWithStatus("POSTED").first().single().eventKey)
            assertEquals("saved", dao.messagesWithStatus("SAVED").first().single().eventKey)
            assertEquals("ignored", dao.messagesWithStatus("IGNORED").first().single().eventKey)
            assertTrue(dao.messagesWithStatus("PENDING").first().isEmpty())
        } finally { reopened.close(); context.deleteDatabase(name) }
    }

    @Test fun smsDoesNotSuppressSameTransactionPush() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val dao = db.financeInboxDao()
            val sms = incoming("sms")
            dao.capture(sms)
            dao.confirm(dao.byEvent("sms")!!.id, sms.title, sms.body, false, 5000,
                CalendarDateUtils.today(), null, null, null, false)
            val push = incoming("push", "NOTIFICATION", "com.bank", sms.receivedAt + 1000)
            assertEquals("PENDING", dao.capture(push))
            val captured = dao.messagesWithStatus("PENDING").first().single()
            assertEquals("NOTIFICATION", captured.source)
            assertTrue(captured.possibleDuplicate)
            assertEquals(2, dao.messages().first().size)
        } finally { db.close() }
    }
}
