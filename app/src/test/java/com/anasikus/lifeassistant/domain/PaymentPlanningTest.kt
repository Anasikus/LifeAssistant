package com.anasikus.lifeassistant.domain

import com.anasikus.lifeassistant.data.CalendarEventEntity
import com.anasikus.lifeassistant.data.CalendarEventOverrideEntity
import com.anasikus.lifeassistant.data.PaymentPlanEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentPlanningTest {
    @Test fun singleHourlyOverrideDoesNotReplaceFixedSeriesRate() {
        val event = CalendarEventEntity(
            id = 1, title = "Ozon", startDate = "2026-11-02", startMinute = 480,
            durationMinutes = 720, repeatDaysMask = 31, payMode = "SHIFT", payAmountCents = 160000
        )
        val override = CalendarEventOverrideEntity(
            event.id, "2026-11-03", "2026-11-03", 480, 480,
            payMode = "HOURLY", payAmountCents = 13500
        )
        val source = FinanceRules.sources(
            listOf(event), listOf(override), null, emptyList(), "2026-11-03", 960
        ).single()
        assertEquals(268000L, source.accrued)
    }

    @Test fun overnightDailySleepIncludesBothPartsOfSecondDay() {
        val event = CalendarEventEntity(
            id = 1, title = "Сон", startDate = "2026-11-01", startMinute = 1350,
            durationMinutes = 450, repeatDaysMask = 127
        )
        val second = CalendarEventRules.expand(listOf(event), "2026-11-02", "2026-11-02")
        assertEquals(listOf(0, 1350), second.map { it.startMinute })
        assertEquals(listOf("2026-11-01", "2026-11-02"), second.map { it.occurrenceDate })
    }

    @Test fun shortMonthsDoNotMoveFuturePaymentDatesAwayFromAnchor() {
        val plan = PaymentPlanEntity(title = "Подписка", amountCents = 10000,
            startDate = "2026-01-31", everyMonths = 1, reminderDays = 3)
        assertEquals(
            listOf("2026-02-28", "2026-03-31", "2026-04-30"),
            PaymentPlanRules.between(plan, "2026-02-01", "2026-04-30").map { it.date }
        )
        val quarterly = plan.copy(everyMonths = 3)
        assertEquals(
            listOf("2026-04-30", "2026-07-31"),
            PaymentPlanRules.between(quarterly, "2026-02-01", "2026-07-31").map { it.date }
        )
    }
}
