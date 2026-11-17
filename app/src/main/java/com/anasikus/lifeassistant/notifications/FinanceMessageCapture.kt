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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CancellationException

object FinanceCaptureSettings {
    private fun preferences(context: Context) = context.getSharedPreferences("finance_capture", Context.MODE_PRIVATE)
    fun enabled(context: Context, source: String): Boolean = preferences(context).getBoolean(source, false)
    fun setEnabled(context: Context, source: String, enabled: Boolean) {
        preferences(context).edit().putBoolean(source, enabled).apply()
    }
    fun observe(context: Context, onChange: () -> Unit): () -> Unit {
        val preferences = preferences(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun processed(context: Context, source: String) {
        preferences(context).edit().putLong("last_$source", System.currentTimeMillis()).remove("error_$source").apply()
    }
    fun failed(context: Context, source: String, error: Exception) {
        preferences(context).edit().putString("error_$source", error.javaClass.simpleName).apply()
    }
    fun lastProcessed(context: Context, source: String): Long = preferences(context).getLong("last_$source", 0L)
    fun lastError(context: Context, source: String): String? = preferences(context).getString("error_$source", null)
    fun setConnected(context: Context, connected: Boolean) { preferences(context).edit().putBoolean("listener_connected", connected).apply() }
    fun isConnected(context: Context): Boolean = preferences(context).getBoolean("listener_connected", false)
    fun reconnect(context: Context) {
        if (!hasNotificationAccess(context)) return
        try { NotificationListenerService.requestRebind(ComponentName(context, FinanceNotificationListener::class.java)) }
        catch (error: Exception) {
            failed(context, "NOTIFICATION", error)
            Log.w("FinanceCapture", "Unable to request listener reconnection", error)
        }
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
        val heading = (if (source == "EMAIL") FinanceMessageParser.emailSubject(title, body) else title).trim().take(300)
        val text = (if (source == "EMAIL") "" else body.trim().ifBlank { title.trim() }).take(8_000)
        if (text.isBlank() && heading.isBlank()) return
        val author = sender.trim().take(300)
        AppDatabase.getDatabase(context).financeInboxDao().capture(FinanceInboxEntity(
            eventKey = FinanceMessageParser.hash(eventKey), source = source, sender = author,
            title = heading, body = text, receivedAt = minOf(at, System.currentTimeMillis()),
            ruleKey = FinanceMessageParser.ruleKey(source, author, heading, text)
        ))
        FinanceCaptureSettings.processed(context, source)
    }
}

class FinanceNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var blockedKeys: Set<String> = emptySet()

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            try {
                AppDatabase.getDatabase(applicationContext).financeInboxDao().blockedSenders().collect { rules ->
                    blockedKeys = rules.map { it.key }.toSet()
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // DAO checks the rules again, even if the optional in-memory cache fails.
                Log.w("FinanceCapture", "Unable to refresh sender filters", error)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || FinanceMessageParser.senderKey("APP", sbn.packageName) in blockedKeys) return
        scope.launch { captureNotification(sbn) }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        FinanceCaptureSettings.setConnected(this, true)
        // Android can disconnect the listener while the app is closed. Recover only
        // notifications still visible; eventKey prevents importing the same event twice.
        try {
            val outstanding = activeNotifications?.sortedBy { it.postTime }.orEmpty()
            scope.launch { outstanding.forEach { captureNotification(it) } }
        } catch (error: Exception) {
            FinanceCaptureSettings.failed(this, "NOTIFICATION", error)
            Log.w("FinanceCapture", "Unable to recover active notifications", error)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        FinanceCaptureSettings.setConnected(this, false)
        try { FinanceCaptureSettings.reconnect(this) }
        catch (error: Exception) { Log.w("FinanceCapture", "Unable to reconnect notification listener", error) }
    }

    private suspend fun captureNotification(sbn: StatusBarNotification) {
        var source = "NOTIFICATION"
        try {
            if (sbn.packageName == packageName || FinanceMessageParser.senderKey("APP", sbn.packageName) in blockedKeys) return
            val notification = sbn.notification
            if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
            val isMail = notification.category == Notification.CATEGORY_EMAIL || sbn.packageName in setOf(
                "com.google.android.gm", "com.mail.mobile.android.mail", "ru.mail.mailapp",
                "com.microsoft.office.outlook", "com.samsung.android.email.provider"
            )
            source = if (isMail) "EMAIL" else "NOTIFICATION"
            if (!FinanceCaptureSettings.enabled(this, source)) return
            val extras = notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
                ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString().orEmpty()
            val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            val short = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")
            val body = big?.takeIf { it.isNotBlank() } ?: lines?.takeIf { it.isNotBlank() }
                ?: short?.takeIf { it.isNotBlank() } ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
            val author = if (isMail && FinanceMessageParser.parse(title).amounts.isEmpty())
                "${sbn.packageName} · $title" else sbn.packageName
            if (FinanceMessageParser.senderKey(source, author) in blockedKeys) return
            val at = sbn.postTime
            FinanceMessageCapture.receive(
                applicationContext, source, author, title, body, at,
                "${sbn.key}:${notification.`when`.takeIf { it > 0 } ?: at}:${FinanceMessageParser.hash(title + body)}"
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            FinanceCaptureSettings.failed(this, source, error)
            Log.w("FinanceCapture", "Unable to save an incoming notification", error)
        }
    }

    override fun onDestroy() {
        FinanceCaptureSettings.setConnected(this, false)
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
                if (error is CancellationException) throw error
                FinanceCaptureSettings.failed(context.applicationContext, "SMS", error)
                Log.w("FinanceCapture", "Unable to save an incoming SMS", error)
            } finally { pending.finish() }
        }
    }
}
