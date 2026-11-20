package com.anasikus.lifeassistant.domain

import com.anasikus.lifeassistant.data.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class FinancePlanningRevisionTest {
    private fun plan(unit: String = "MONTH", every: Int = 1, start: String = "2026-11-01") =
        PaymentPlanEntity(id = 1, title = "Платёж", amountCents = 10000, startDate = start,
            everyMonths = 1, reminderDays = 7, repeatUnit = unit, repeatEvery = every)

    @Test fun weeklyAndDailyOccurrencesInOneMonthHaveDifferentKeys() {
        val weekly = PaymentPlanRules.between(plan("WEEK"), "2026-11-01", "2026-11-30")
        assertEquals(listOf("2026-11-01", "2026-11-08", "2026-11-15", "2026-11-22", "2026-11-29"), weekly.map { it.date })
        assertEquals(weekly.size, weekly.map { it.key }.toSet().size)
        val daily = PaymentPlanRules.between(plan("DAY", 3), "2026-11-05", "2026-11-12")
        assertEquals(listOf("2026-11-07", "2026-11-10"), daily.map { it.date })
    }

    @Test fun yearlyRecurrenceKeepsLeapAnchorAndMonthlyPaidKeysStayCompatible() {
        val yearly = PaymentPlanRules.between(plan("YEAR", start = "2024-02-29"), "2025-01-01", "2028-12-31")
        assertEquals(listOf("2025-02-28", "2026-02-28", "2027-02-28", "2028-02-29"), yearly.map { it.date })
        assertEquals("1:2026-11", PaymentPlanRules.between(plan(), "2026-11-01", "2026-11-30").single().key)
        assertEquals(3, PaymentPlanRules.interval(plan().copy(repeatEvery = 0, everyMonths = 3)))
    }

    @Test fun remindersUseSelectedTimeRepeatOnlyUntilPaymentAndNeverBeforeLeadDate() {
        val value = plan().copy(reminderMinute = 20 * 60 + 30, reminderRepeatDays = 2)
        assertNull(PaymentReminderRules.latestDate(value, "2026-11-20", "2026-11-12"))
        assertEquals("2026-11-17", PaymentReminderRules.latestDate(value, "2026-11-20", "2026-11-18"))
        assertEquals("2026-11-19", PaymentReminderRules.nextDate(value, "2026-11-20", "2026-11-17"))
        assertNull(PaymentReminderRules.nextDate(value, "2026-11-20", "2026-11-19"))
        val clock = Calendar.getInstance().apply { timeInMillis = PaymentReminderRules.at(value, "2026-11-17") }
        assertEquals(20, clock.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, clock.get(Calendar.MINUTE))
        assertNull(PaymentReminderRules.nextDate(value.copy(reminderRepeatDays = 0), "2026-11-20", "2026-11-13"))
    }

    @Test fun reservationBecomesExpenseOnceAndAvailableBalanceDoesNotJump() {
        val income = FinanceTransactionEntity(date = "2026-11-01", income = true, amountCents = 100000, note = "")
        val planned = FinanceTransactionEntity(date = "2026-11-21", income = false, amountCents = 27000, note = "Квартира")
        val before = FinanceRules.monthTotals(emptyList(), listOf(income, planned), "2026-11-01", "2026-11-20")
        assertEquals(27000L, before.reserved)
        assertEquals(0L, before.expense)
        assertEquals(73000L, before.available)
        val after = FinanceRules.monthTotals(emptyList(), listOf(income, planned), "2026-11-01", "2026-11-21")
        assertEquals(0L, after.reserved)
        assertEquals(27000L, after.expense)
        assertEquals(before.available, after.available)
        assertEquals(100000L, FinanceRules.monthTotals(emptyList(), listOf(income), "2026-11-01", "2026-11-20").available)
    }

    @Test fun futureTransfersAndIncomeNeverReserveAndFutureExpenseSurvivesMonthRollover() {
        val rows = listOf(
            FinanceTransactionEntity(date = "2026-11-20", income = true, amountCents = 100000, note = ""),
            FinanceTransactionEntity(date = "2026-12-02", income = false, amountCents = 20000, note = ""),
            FinanceTransactionEntity(date = "2026-12-02", income = true, amountCents = 30000, note = ""),
            FinanceTransactionEntity(date = "2026-12-02", income = false, transfer = true, amountCents = 50000, note = "")
        )
        val before = FinanceRules.monthTotals(emptyList(), rows, "2026-11-01", "2026-11-30")
        val after = FinanceRules.monthTotals(emptyList(), rows, "2026-12-01", "2026-12-01")
        assertEquals(20000L, before.reserved)
        assertEquals(before.available, after.available)
        assertEquals(100000L, after.openingCash)
    }

    @Test fun incomeCategoryDoesNotReduceWorkAccrualWithoutWorkSourceLink() {
        val source = FinanceSource("event:1", "Работа", 100000)
        val gift = FinanceTransactionEntity(date = "2026-11-20", income = true, amountCents = 30000,
            note = "Помощь родителей", incomeCategoryId = 1)
        assertEquals(100000L, FinanceRules.outstanding(source, listOf(gift)))
        assertEquals(70000L, FinanceRules.outstanding(source, listOf(gift.copy(sourceKey = source.key))))
    }

    @Test fun frozenTasksNeverScheduleEvenWithExplicitManualPlacement() {
        val frozen = TaskEntity(id = 1, title = "Идея", frozen = true, priorityLevel = 2, deadline = "2026-11-01")
        val slot = CalendarTaskPlacementEntity(1, "2026-11-20", 600, 60)
        assertTrue(CalendarTaskScheduler.schedule(listOf(frozen), emptyList(), "2026-11-20", "2026-11-20",
            listOf(slot), today = "2026-11-20", earliestStartMinute = 500).isEmpty())
        assertEquals("Заморожена", TaskRules.status(frozen))
    }
}
