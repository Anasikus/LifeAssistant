package com.anasikus.lifeassistant.domain

import com.anasikus.lifeassistant.data.CalendarEventEntity
import com.anasikus.lifeassistant.data.FinanceTransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class FinanceRulesTest {
    @Test fun workSeriesAccruesOnlyEndedWeekdayShifts() {
        val work = CalendarEventEntity(
            id = 1, title = "Ozon", startDate = "2026-11-02",
            startMinute = 480, durationMinutes = 720, repeatDaysMask = 31,
            hourlyRateCents = 20000
        )
        val sources = FinanceRules.sources(listOf(work), emptyList(), null, emptyList(), "2026-11-06", 719)
        assertEquals(960000L, sources.single().accrued)
        val saturday = FinanceRules.sources(listOf(work), emptyList(), null, emptyList(), "2026-11-07", 1439)
        assertEquals(1200000L, saturday.single().accrued)
    }

    @Test fun linkedPaymentReducesOnlyItsOwnJobAndCashIncludesAllTransactions() {
        val sources = listOf(FinanceSource("event:1", "Ozon", 800000), FinanceSource("event:2", "Подработка", 200000))
        val payments = listOf(
            FinanceTransactionEntity(date = "2026-11-06", income = true, amountCents = 500000, note = "", sourceKey = "event:1"),
            FinanceTransactionEntity(date = "2026-11-06", income = true, amountCents = 40000, note = ""),
            FinanceTransactionEntity(date = "2026-11-06", income = false, amountCents = 125000, note = "")
        )
        val totals = FinanceRules.totals(sources, payments)
        assertEquals(500000L, totals.earned)
        assertEquals(540000L, totals.income)
        assertEquals(125000L, totals.expense)
        assertEquals(415000L, totals.cash)
    }
}
