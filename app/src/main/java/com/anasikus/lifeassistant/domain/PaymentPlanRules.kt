package com.anasikus.lifeassistant.domain

import com.anasikus.lifeassistant.data.PaymentPlanEntity
import java.util.Calendar
import java.util.Locale

data class PlannedPayment(val plan: PaymentPlanEntity, val date: String) {
    val key: String get() = "${plan.id}:${date.take(7)}"
}

object PaymentPlanRules {
    fun between(plan: PaymentPlanEntity, start: String, end: String): List<PlannedPayment> {
        if (start > end || end < plan.startDate) return emptyList()
        val interval = plan.everyMonths.coerceAtLeast(1)
        val anchor = CalendarDateUtils.parseCalendar(plan.startDate)
        val first = CalendarDateUtils.parseCalendar(maxOf(start, plan.startDate))
        val elapsed = (first.get(Calendar.YEAR) - anchor.get(Calendar.YEAR)) * 12 +
            first.get(Calendar.MONTH) - anchor.get(Calendar.MONTH)
        var offset = elapsed / interval * interval
        return buildList {
            while (true) {
                val date = (anchor.clone() as Calendar).apply { add(Calendar.MONTH, offset) }
                val iso = String.format(Locale.US, "%04d-%02d-%02d",
                    date.get(Calendar.YEAR), date.get(Calendar.MONTH) + 1, date.get(Calendar.DAY_OF_MONTH))
                if (iso > end) break
                if (iso >= start) add(PlannedPayment(plan, iso))
                offset += interval
            }
        }
    }
}
