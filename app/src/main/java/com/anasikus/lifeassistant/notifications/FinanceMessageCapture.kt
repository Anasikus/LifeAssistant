package com.anasikus.lifeassistant.notifications

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.anasikus.lifeassistant.data.AppDatabase
import com.anasikus.lifeassistant.data.FinanceInboxEntity
import com.anasikus.lifeassistant.domain.FinanceMessageParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

object FinanceCaptureSettings {
    private fun preferences(context: Context) = context.getSharedPreferences("finance_capture", Context.MODE_PRIVATE)
    fun enabled(context: Context, source: String): Boolean = preferences(context).getBoolean(source, false)
    fun setEnabled(context: Context, source: String, enabled: Boolean) {
        preferences(context).edit().putBoolean(source, enabled).apply()
    }
    fun hasNotificationAccess(context: Context): Boolean {
        val component = ComponentName(context, FinanceNotificationListener::class.java)
        return Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            .orEmpty().split(':').any { ComponentName.unflattenFromString(it) == component }
    }
}

object FinanceMessageCapture {
    suspend fun receive(context: Context, source: String, sender: String, title: String, body: String, at: Long, eventKey: String) {
        if (source != "MANUAL" && !FinanceCaptureSettings.enabled(context, source)) return
        val text = body.trim().ifBlank { title.trim() }.take(8_000)
        if (text.isBlank()) return
        val heading = title.trim().take(300)
        val author = sender.trim().take(300)
        AppDatabase.getDatabase(context).financeInboxDao().capture(FinanceInboxEntity(
            eventKey = FinanceMessageParser.hash(eventKey), source = source, sender = author,
            title = heading, body = text, receivedAt = minOf(at, System.currentTimeMillis()),
            ruleKey = FinanceMessageParser.ruleKey(source, author, heading, text)
        ))
    }
}

class FinanceNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 || sbn.isOngoing) return
        val isMail = notification.category == Notification.CATEGORY_EMAIL || sbn.packageName in setOf(
            "com.google.android.gm", "com.mail.mobile.android.mail", "ru.mail.mailapp",
            "com.microsoft.office.outlook", "com.samsung.android.email.provider"
        )
        val source = if (isMail) "EMAIL" else "NOTIFICATION"
        if (!FinanceCaptureSettings.enabled(this, source)) return
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val short = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")
        val body = big?.takeIf { it.isNotBlank() } ?: lines?.takeIf { it.isNotBlank() } ?: short.orEmpty()
        val at = sbn.postTime
        scope.launch {
            try {
                FinanceMessageCapture.receive(
                    applicationContext, source, sbn.packageName, title, body, at,
                    "${sbn.key}:${notification.`when`.takeIf { it > 0 } ?: at}:${FinanceMessageParser.hash(title + body)}"
                )
            } catch (error: Exception) {
                Log.w("FinanceCapture", "Unable to save an incoming notification", error)
            }
        }
    }
    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

class FinanceSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION || !FinanceCaptureSettings.enabled(context, "SMS")) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isNullOrEmpty()) return
        val sender = parts.first().displayOriginatingAddress.orEmpty()
        val at = parts.first().timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
        val body = parts.joinToString("") { it.displayMessageBody.orEmpty() }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                FinanceMessageCapture.receive(context.applicationContext, "SMS", sender, sender, body, at,
                    "sms:$sender:$at:${FinanceMessageParser.hash(body)}")
            } catch (error: Exception) {
                Log.w("FinanceCapture", "Unable to save an incoming SMS", error)
            } finally { pending.finish() }
        }
    }
}
