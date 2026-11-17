package com.anasikus.lifeassistant.calendar

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.anasikus.lifeassistant.data.*
import com.anasikus.lifeassistant.domain.CalendarDateUtils
import com.anasikus.lifeassistant.domain.FinanceMessageParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FinanceInboxBacklogTest {
    private fun incoming(key: String, sender: String = "com.xiaomi.hm.health", source: String = "NOTIFICATION", merchant: String = "SHOP") : FinanceInboxEntity {
        val body = "Покупка 50р $merchant Баланс: 1000р"
        return FinanceInboxEntity(eventKey = key, source = source, sender = sender, title = "Операция", body = body,
            receivedAt = System.currentTimeMillis(), ruleKey = FinanceMessageParser.ruleKey(source, sender, "Операция", body))
    }

    @Test fun ignoresAndPurges1900SimilarMessagesButPreservesOtherSendersAndPostedTransactions() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val dao = db.financeInboxDao()
            db.withTransaction { repeat(1900) { dao.insertMessage(incoming("spam:$it")) } }
            val posted = incoming("posted")
            dao.insertMessage(posted)
            val postedId = dao.byEvent("posted")!!.id
            dao.confirm(postedId, posted.title, posted.body, false, 5000, CalendarDateUtils.today(), null, null, null, false)
            dao.insertMessage(incoming("other-sender", "com.bank"))
            dao.insertMessage(incoming("other-template", merchant = "CAFE"))
            dao.ignore(dao.byEvent("spam:0")!!.id, true)
            assertEquals(3, dao.messages().first().size)
            assertNull(dao.byEvent("spam:1899"))
            assertNotNull(dao.transactionForMessage(postedId))
            assertNotNull(dao.byEvent("other-sender"))
            assertNotNull(dao.byEvent("other-template"))
            dao.capture(incoming("future"))
            assertNull(dao.byEvent("future"))
        } finally { db.close() }
    }

    @Test fun appBlockPurgesAllContentAndMailAuthorsWithoutTouchingSmsOrSimilarPackageNames() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val dao = db.financeInboxDao()
            dao.insertMessage(incoming("push"))
            dao.insertMessage(incoming("email", "com.xiaomi.hm.health · Any sender", "EMAIL"))
            dao.insertMessage(incoming("sms", "com.xiaomi.hm.health", "SMS"))
            dao.insertMessage(incoming("other", "com.xiaomi.hm.health.extra"))
            val posted = incoming("posted")
            dao.insertMessage(posted)
            val id = dao.byEvent("posted")!!.id
            dao.confirm(id, posted.title, posted.body, false, 5000, CalendarDateUtils.today(), null, null, null, false)
            dao.configureSender("APP", "com.xiaomi.hm.health", "", false)
            assertNull(dao.byEvent("push"))
            assertNull(dao.byEvent("email"))
            assertNotNull(dao.byEvent("sms"))
            assertNotNull(dao.byEvent("other"))
            assertNotNull(dao.transactionForMessage(id))
            dao.capture(incoming("future-changed", merchant = "NEW CONTENT"))
            dao.capture(incoming("future-email", "com.xiaomi.hm.health · Another sender", "EMAIL"))
            assertNull(dao.byEvent("future-changed"))
            assertNull(dao.byEvent("future-email"))
            dao.capture(incoming("bank-sms", "900", "SMS"))
            assertNotNull(dao.byEvent("bank-sms"))
            dao.configureSender("APP", "com.xiaomi.hm.health", "", true)
            dao.capture(incoming("allowed-again"))
            assertNotNull(dao.byEvent("allowed-again"))
        } finally { db.close() }
    }

    @Test fun ignoreOnlyThisDoesNotPurgeOrBlockOtherMessages() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val dao = db.financeInboxDao()
            dao.insertMessage(incoming("one"))
            dao.insertMessage(incoming("two"))
            dao.ignore(dao.byEvent("one")!!.id, false)
            assertEquals("PENDING", dao.byEvent("two")!!.status)
            dao.capture(incoming("three"))
            assertNotNull(dao.byEvent("three"))
            assertNull(dao.rule(incoming("one").ruleKey))
        } finally { db.close() }
    }
}
