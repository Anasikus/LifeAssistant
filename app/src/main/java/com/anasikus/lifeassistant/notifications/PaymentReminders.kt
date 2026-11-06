package com.anasikus.lifeassistant.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.anasikus.lifeassistant.MainActivity
import com.anasikus.lifeassistant.data.AppDatabase
import com.anasikus.lifeassistant.data.FinanceTransactionEntity
import com.anasikus.lifeassistant.data.PaymentPlanEntity
import com.anasikus.lifeassistant.domain.CalendarDateUtils
import com.anasikus.lifeassistant.domain.PaymentPlanRules
import com.anasikus.lifeassistant.domain.ShiftRules
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object PaymentReminders {
    private const val action = "com.anasikus.lifeassistant.PAYMENT_REMINDER"
    private const val channel = "payment_reminders"

    private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, PaymentReminderReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    suspend fun refresh(context: Context) {
        val dao = AppDatabase.getDatabase(context).calendarPlanningDao()
        update(context, dao.paymentPlans().first(), dao.financeTransactions().first(), deliver = false)
    }

    @Synchronized
    fun update(
        context: Context,
        plans: List<PaymentPlanEntity>,
        transactions: List<FinanceTransactionEntity>,
        deliver: Boolean = false
    ) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = alarmIntent(context)
        alarm.cancel(pending)
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) return
        val prefs = context.getSharedPreferences("payment_reminders", Context.MODE_PRIVATE)
        val paid = transactions.mapNotNull { it.planKey }.toSet()
        val today = CalendarDateUtils.today()
        val now = System.currentTimeMillis()
        var next: Long? = null
        for (plan in plans) {
            val horizon = CalendarDateUtils.addMonths(maxOf(today, plan.startDate), plan.everyMonths + 2)
            val notifiedDate = prefs.getString("plan_${plan.id}", "").orEmpty()
            for (payment in PaymentPlanRules.between(plan, today, horizon)) {
                if (payment.key in paid || payment.date <= notifiedDate) continue
                val reminderDate = CalendarDateUtils.addDays(payment.date, -plan.reminderDays)
                val at = CalendarDateUtils.parseCalendar(reminderDate).apply {
                    set(Calendar.HOUR_OF_DAY, 9)
                }.timeInMillis
                if (deliver && at <= now) {
                    if (Build.VERSION.SDK_INT >= 26) {
                        manager.createNotificationChannel(
                            NotificationChannel(channel, "Регулярные платежи", NotificationManager.IMPORTANCE_DEFAULT)
                        )
                    }
                    val openApp = PendingIntent.getActivity(
                        context, 0, Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    @Suppress("DEPRECATION")
                    val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, channel)
                        else Notification.Builder(context)
                    manager.notify(payment.key, plan.id, builder
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle(plan.title)
                        .setContentText("${ShiftRules.money(plan.amountCents)} · ${CalendarDateUtils.formatDate(payment.date)}")
                        .setContentIntent(openApp)
                        .setAutoCancel(true)
                        .build())
                    prefs.edit().putString("plan_${plan.id}", payment.date).apply()
                } else {
                    val trigger = maxOf(at, now + 1_000L)
                    next = minOf(next ?: trigger, trigger)
                    break
                }
            }
        }
        next?.let { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it, pending) }
    }
}

class PaymentReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getDatabase(app).calendarPlanningDao()
                PaymentReminders.update(
                    app, dao.paymentPlans().first(), dao.financeTransactions().first(),
                    deliver = intent.action == "com.anasikus.lifeassistant.PAYMENT_REMINDER"
                )
            } catch (error: Exception) {
                Log.w("PaymentReminders", "Unable to update reminders", error)
            } finally {
                pending.finish()
            }
        }
    }
}
