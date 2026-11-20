package com.anasikus.lifeassistant.domain

import com.anasikus.lifeassistant.data.PaymentPlanEntity
import java.util.Calendar

/** One delivery per local calendar day; repeats stop at the payment date. */
object PaymentReminderRules {
    fun latestDate(plan: PaymentPlanEntity, paymentDate: String, today: String): String? {
        val first = CalendarDateUtils.addDays(paymentDate, -plan.reminderDays)
        if (today < first) return null
        if (plan.reminderRepeatDays <= 0) return first
        val elapsed = CalendarDateUtils.daysBetween(first, minOf(today, paymentDate)).toInt()
        return CalendarDateUtils.addDays(first, elapsed / plan.reminderRepeatDays * plan.reminderRepeatDays)
    }

    fun at(plan: PaymentPlanEntity, date: String): Long = CalendarDateUtils.parseCalendar(date).apply {
        set(Calendar.HOUR_OF_DAY, plan.reminderMinute.coerceIn(0, 1439) / 60)
        set(Calendar.MINUTE, plan.reminderMinute.coerceIn(0, 1439) % 60)
    }.timeInMillis

    fun nextDate(plan: PaymentPlanEntity, paymentDate: String, deliveredDate: String): String? {
        if (plan.reminderRepeatDays <= 0) return null
        return CalendarDateUtils.addDays(deliveredDate, plan.reminderRepeatDays).takeIf { it <= paymentDate }
    }
}
