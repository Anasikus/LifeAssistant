package com.anasikus.lifeassistant.notifications

import android.app.Notification
import androidx.core.app.NotificationCompat
import com.anasikus.lifeassistant.domain.FinanceMessageParser

/** Reads public notification text without attempting to inflate another app's custom views. */
object FinanceNotificationReader {
    data class Payload(val source: String, val sender: String, val title: String, val body: String)
    private val mailApps = setOf(
        "com.google.android.gm", "com.mail.mobile.android.mail", "ru.mail.mailapp",
        "com.microsoft.office.outlook", "com.samsung.android.email.provider"
    )

    fun read(packageName: String, notification: Notification): Payload {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString().orEmpty()
        val messagingText = try {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
                ?.messages?.lastOrNull()?.text?.toString()
        } catch (_: RuntimeException) { null }
        val body = listOf(
            messagingText,
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n"),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
        // An arbitrary app can set CATEGORY_EMAIL. Only known mail clients should
        // be subject to the separate mail toggle and the subject-only parser.
        val mail = packageName in mailApps
        val sender = if (mail && title.isNotBlank() && FinanceMessageParser.parse(title).amounts.isEmpty())
            "$packageName · $title" else packageName
        return Payload(if (mail) "EMAIL" else "NOTIFICATION", sender,
            title.ifBlank { if (body.isBlank()) "Уведомление без доступного текста" else "" }, body)
    }

    fun eventKey(notificationKey: String, postedAt: Long, title: String, body: String): String =
        "$notificationKey:$postedAt:${FinanceMessageParser.hash(title + "\n" + body)}"
}
