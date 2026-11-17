package com.anasikus.lifeassistant.notifications

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
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
    const val REFRESH_ACTION = "com.anasikus.lifeassistant.REFRESH_FINANCE_NOTIFICATIONS"
    private fun preferences(context: Context) = context.getSharedPreferences("finance_capture", Context.MODE_PRIVATE)
    fun enabled(context: Context, source: String): Boolean = preferences(context).getBoolean(source, false)
    fun setEnabled(context: Context, source: String, enabled: Boolean) {
        preferences(context).edit().putBoolean(source, enabled).apply()
        if (enabled && source in listOf("NOTIFICATION", "EMAIL")) reconnect(context)
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
    fun pushSeen(context: Context, packageName: String) {
        preferences(context).edit().putLong("push_seen_at", System.currentTimeMillis())
            .putString("push_seen_app", packageName).putString("push_result", "Получен от Android; ожидает обработки").apply()
    }
    fun pushResult(context: Context, packageName: String, result: String) {
        preferences(context).edit().putLong("push_seen_at", System.currentTimeMillis())
            .putString("push_seen_app", packageName).putString("push_result", result).apply()
    }
    fun pushDetails(context: Context): String {
        val values = preferences(context)
        val at = values.getLong("push_seen_at", 0)
        if (at == 0L) return "Пуши ещё не поступали в обработчик Android"
        val time = java.text.SimpleDateFormat("dd.MM HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(at))
        return "$time · ${values.getString("push_seen_app", "")}\n${values.getString("push_result", "")}"
    }
    fun lastProcessed(context: Context, source: String): Long = preferences(context).getLong("last_$source", 0L)
    fun lastError(context: Context, source: String): String? = preferences(context).getString("error_$source", null)
    fun setConnected(context: Context, connected: Boolean) { preferences(context).edit().putBoolean("listener_connected", connected).apply() }
    fun isConnected(context: Context): Boolean = preferences(context).getBoolean("listener_connected", false)
    fun reconnect(context: Context) {
        // Permission alone does not prove that Android is still delivering events.
        // Clear the cached status and let the live service acknowledge the probe.
        setConnected(context, false)
        if (!hasNotificationAccess(context)) return
        try {
            context.sendBroadcast(Intent(REFRESH_ACTION).setPackage(context.packageName))
            NotificationListenerService.requestRebind(ComponentName(context, FinanceNotificationListener::class.java))
        }
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
    suspend fun receive(context: Context, source: String, sender: String, title: String, body: String, at: Long, eventKey: String, legacyEventKey: String? = null): String {
        if (source != "MANUAL" && !FinanceCaptureSettings.enabled(context, source)) return "SOURCE_DISABLED"
        val heading = (if (source == "EMAIL") FinanceMessageParser.emailSubject(title, body) else title).trim().take(300)
        val text = (if (source == "EMAIL") "" else body.trim().ifBlank { title.trim() }).take(8_000)
        if (text.isBlank() && heading.isBlank()) return "EMPTY_TEXT"
        val author = sender.trim().take(300)
        val dao = AppDatabase.getDatabase(context).financeInboxDao()
        // Avoid duplicating old entries after switching from app-provided `when`
        // to Android's postTime. New identical transfers at a later time remain distinct.
        if (legacyEventKey != null) {
            val prior = dao.byEvent(FinanceMessageParser.hash(legacyEventKey))
            if (prior != null && prior.receivedAt == minOf(at, System.currentTimeMillis())) {
                FinanceCaptureSettings.processed(context, source)
                return "ALREADY_STORED"
            }
        }
        val outcome = dao.capture(FinanceInboxEntity(
            eventKey = FinanceMessageParser.hash(eventKey), source = source, sender = author,
            title = heading, body = text, receivedAt = minOf(at, System.currentTimeMillis()),
            ruleKey = FinanceMessageParser.ruleKey(source, author, heading, text)
        ))
        FinanceCaptureSettings.processed(context, source)
        return outcome
    }
}

class FinanceNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var blockedKeys: Set<String> = emptySet()
    private var connected = false
    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == FinanceCaptureSettings.REFRESH_ACTION && connected) recoverActiveNotifications()
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(this, refreshReceiver, IntentFilter(FinanceCaptureSettings.REFRESH_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
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
        if (sbn.packageName == packageName) return
        connected = true
        FinanceCaptureSettings.setConnected(this, true)
        FinanceCaptureSettings.pushSeen(this, sbn.packageName)
        scope.launch { captureNotification(sbn) }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        FinanceCaptureSettings.setConnected(this, true)
        recoverActiveNotifications()
    }

    private fun recoverActiveNotifications() {
        // Android can disconnect the listener while the app is closed. Recover only
        // notifications still visible; eventKey prevents importing the same event twice.
        try {
            val outstanding = activeNotifications?.sortedBy { it.postTime }.orEmpty()
            connected = true
            FinanceCaptureSettings.setConnected(this, true)
            scope.launch { outstanding.forEach { captureNotification(it) } }
        } catch (error: Exception) {
            connected = false
            FinanceCaptureSettings.setConnected(this, false)
            FinanceCaptureSettings.failed(this, "NOTIFICATION", error)
            FinanceCaptureSettings.reconnect(this)
            Log.w("FinanceCapture", "Unable to recover active notifications", error)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connected = false
        FinanceCaptureSettings.setConnected(this, false)
        try { FinanceCaptureSettings.reconnect(this) }
        catch (error: Exception) { Log.w("FinanceCapture", "Unable to reconnect notification listener", error) }
    }

    private suspend fun captureNotification(sbn: StatusBarNotification) {
        var source = "NOTIFICATION"
        try {
            if (sbn.packageName == packageName) return
            FinanceCaptureSettings.pushSeen(this, sbn.packageName)
            if (FinanceMessageParser.senderKey("APP", sbn.packageName) in blockedKeys) {
                FinanceCaptureSettings.pushResult(this, sbn.packageName, "Приложение заблокировано в отправителях")
                return
            }
            // Summary notifications can be the only visible financial push on some devices.
            // Capture their text too; the ledger duplicate guard keeps them on review.
            val payload = FinanceNotificationReader.read(sbn.packageName, sbn.notification)
            source = payload.source
            val at = sbn.postTime
            val oldTitle = sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
                ?.takeIf { it.isNotBlank() } ?: sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE_BIG)?.toString().orEmpty()
            val oldBig = sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()
            val oldShort = sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
            val oldLines = sbn.notification.extras.getCharSequenceArray(android.app.Notification.EXTRA_TEXT_LINES)?.joinToString("\n")
            val oldBody = oldBig?.takeIf { it.isNotBlank() } ?: oldLines?.takeIf { it.isNotBlank() }
                ?: oldShort?.takeIf { it.isNotBlank() } ?: sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
            val result = FinanceMessageCapture.receive(
                applicationContext, source, payload.sender, payload.title, payload.body, at,
                FinanceNotificationReader.eventKey(sbn.key, at, payload.title, payload.body),
                "${sbn.key}:${sbn.notification.`when`.takeIf { it > 0 } ?: at}:${FinanceMessageParser.hash(oldTitle + oldBody)}"
            )
            FinanceCaptureSettings.pushResult(this, sbn.packageName, when (result) {
                "SOURCE_DISABLED" -> "Сбор ${if (source == "EMAIL") "почты" else "пушей"} выключен в Источниках"
                "SENDER_BLOCKED" -> "Отправитель заблокирован"
                "TEMPLATE_IGNORED" -> "Пропущено по правилу «Не нужно»"
                "ALREADY_STORED" -> "Это событие уже обрабатывалось: оно сохранено либо было удалено"
                "POSTED" -> "Добавлено во вкладку «Учтено»"
                "SAVED" -> "Добавлено во вкладку «Сохранено»"
                "PENDING" -> "Добавлено во вкладку «В обработке»"
                else -> "Android не предоставил текст уведомления"
            })
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            FinanceCaptureSettings.failed(this, source, error)
            FinanceCaptureSettings.pushResult(this, sbn.packageName, "Ошибка обработки: ${error.javaClass.simpleName}")
            Log.w("FinanceCapture", "Unable to save an incoming notification", error)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(refreshReceiver)
        connected = false
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
