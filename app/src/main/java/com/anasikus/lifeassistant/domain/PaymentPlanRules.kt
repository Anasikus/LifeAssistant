package com.anasikus.lifeassistant.domain

import com.anasikus.lifeassistant.settings.AppText
import com.anasikus.lifeassistant.data.PaymentPlanEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Month keys stay compatible with already paid monthly subscriptions. */
data class PlannedPayment(val plan: PaymentPlanEntity, val date: String) {
    val key: String get() = "${plan.id}:${if (plan.repeatUnit in listOf("DAY", "WEEK")) date else date.take(7)}"
}

object PaymentPlanRules {
    fun interval(plan: PaymentPlanEntity): Int =
        (if (plan.repeatEvery > 0) plan.repeatEvery else plan.everyMonths).coerceIn(1, 365)

    fun label(plan: PaymentPlanEntity): String {
        val count = interval(plan)
        return when {
            plan.repeatUnit == "WEEK" && count == 1 -> AppText.get("Каждую неделю")
            plan.repeatUnit == "MONTH" && count == 1 -> AppText.get("Каждый месяц")
            plan.repeatUnit == "MONTH" && count == 3 -> AppText.get("Каждые 3 месяца")
            plan.repeatUnit == "YEAR" && count == 1 -> AppText.get("Каждый год")
            else -> AppText.get("Повтор: {arg0} {arg1}", count, when (plan.repeatUnit) { "DAY" -> AppText.get("дн."); "WEEK" -> AppText.get("нед."); "YEAR" -> AppText.get("г."); else -> AppText.get("мес.") })
        }
    }

    private fun field(plan: PaymentPlanEntity): Int = when (plan.repeatUnit) {
        "DAY", "WEEK" -> Calendar.DAY_OF_MONTH
        "YEAR" -> Calendar.YEAR
        else -> Calendar.MONTH
    }

    private fun step(plan: PaymentPlanEntity) = interval(plan) * if (plan.repeatUnit == "WEEK") 7 else 1

    fun nextHorizon(plan: PaymentPlanEntity, today: String): String {
        val anchor = CalendarDateUtils.parseCalendar(maxOf(plan.startDate, today))
        anchor.add(field(plan), step(plan) + if (plan.repeatUnit == "YEAR") 1 else 2)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(anchor.time)
    }

    fun between(plan: PaymentPlanEntity, start: String, end: String): List<PlannedPayment> {
        if (start > end || end < plan.startDate) return emptyList()
        val anchor = CalendarDateUtils.parseCalendar(plan.startDate)
        val first = CalendarDateUtils.parseCalendar(maxOf(start, plan.startDate))
        val field = field(plan)
        val step = step(plan)
        val elapsed = when (field) {
            Calendar.DAY_OF_MONTH -> CalendarDateUtils.daysBetween(plan.startDate, maxOf(start, plan.startDate)).toInt()
            Calendar.YEAR -> first.get(Calendar.YEAR) - anchor.get(Calendar.YEAR)
            else -> (first.get(Calendar.YEAR) - anchor.get(Calendar.YEAR)) * 12 + first.get(Calendar.MONTH) - anchor.get(Calendar.MONTH)
        }
        var offset = elapsed / step * step
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return buildList {
            while (true) {
                // Always calculate from the anchor: Jan 31 → Feb 28 → Mar 31, not Mar 28.
                val current = (anchor.clone() as Calendar).apply { add(field, offset) }
                val date = format.format(current.time)
                if (date > end) break
                if (date >= start) add(PlannedPayment(plan, date))
                offset += step
            }
        }
    }
}
