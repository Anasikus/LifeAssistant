package com.anasikus.lifeassistant.notifications

import android.app.Notification
import androidx.core.app.NotificationCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class FinanceNotificationReaderTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun readsFinancialSummaryAndDoesNotTreatBankAsMailBasedOnCategory() {
        val notification = NotificationCompat.Builder(context, "test")
            .setContentTitle("Банк").setContentText("Перевод +50р")
            .setCategory(Notification.CATEGORY_EMAIL).setGroup("transfers").setGroupSummary(true).build()
        val payload = FinanceNotificationReader.read("com.bank", notification)
        assertEquals("NOTIFICATION", payload.source)
        assertEquals("Перевод +50р", payload.body)
    }

    @Suppress("DEPRECATION")
    @Test fun messagingStyleUsesMostRecentMessage() {
        val style = NotificationCompat.MessagingStyle("Я")
            .addMessage("Покупка 100р", 1L, "Банк")
            .addMessage("Зачисление +50р", 2L, "Банк")
        val notification = NotificationCompat.Builder(context, "test").setStyle(style).build()
        assertEquals("Зачисление +50р", FinanceNotificationReader.read("com.bank", notification).body)
    }

    @Test fun unreadableNotificationIsStillRepresentedWithoutInventingAmount() {
        val notification = NotificationCompat.Builder(context, "test").build()
        val payload = FinanceNotificationReader.read("com.bank", notification)
        assertTrue(payload.title.isNotBlank())
        assertTrue(payload.body.isBlank())
    }

    @Test fun identicalTextWithDifferentPostTimeIsAnotherEvent() {
        val first = FinanceNotificationReader.eventKey("bank:1", 1000, "Банк", "Перевод +50р")
        assertNotEquals(first, FinanceNotificationReader.eventKey("bank:1", 2000, "Банк", "Перевод +50р"))
        assertEquals(first, FinanceNotificationReader.eventKey("bank:1", 1000, "Банк", "Перевод +50р"))
    }
}
