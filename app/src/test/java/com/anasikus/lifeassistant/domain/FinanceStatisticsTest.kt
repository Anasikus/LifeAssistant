package com.anasikus.lifeassistant.domain

import com.anasikus.lifeassistant.data.ExpenseCategoryEntity
import com.anasikus.lifeassistant.data.FinanceTransactionEntity
import org.junit.Assert.*
import org.junit.Test

class FinanceStatisticsTest {
    private fun row(date: String, income: Boolean, cents: Long, category: Int? = null, transfer: Boolean = false) =
        FinanceTransactionEntity(date = date, income = income, amountCents = cents, note = "", expenseCategoryId = category, transfer = transfer)

    @Test fun categoriesExcludeTransfersAndOutsideDatesAndCombineUnassignedExpenses() {
        val rows = listOf(
            row("2026-11-01", false, 300, 1), row("2026-11-02", false, 200, 1),
            row("2026-11-01", false, 1000, 2), row("2026-11-01", false, 50),
            row("2026-11-01", false, 25, 99), row("2026-11-01", true, 5000),
            row("2026-11-01", false, 9000, 1, true), row("2026-12-01", false, 9000, 1)
        )
        val values = FinanceStatistics.expenseCategories(rows,
            listOf(ExpenseCategoryEntity(1, "Продукты"), ExpenseCategoryEntity(2, "Квартира")), "2026-11-01", "2026-11-17")
        assertEquals(listOf("Квартира", "Продукты", "Без категории"), values.map { it.name })
        assertEquals(listOf(1000L, 500L, 75L), values.map { it.amount })
    }

    @Test fun dailySeriesStartsAtZeroIncludesEmptyDaysAndExcludesTransfersAndFutureRows() {
        val rows = listOf(
            row("2026-10-31", true, 9000), row("2026-11-01", true, 1000),
            row("2026-11-03", false, 300), row("2026-11-02", true, 70000, transfer = true),
            row("2026-11-04", false, 5000)
        )
        val series = FinanceStatistics.cashSeries(rows, "2026-11-01", "2026-11-03")
        assertEquals(listOf("2026-10-31", "2026-11-01", "2026-11-02", "2026-11-03"), series.map { it.date })
        assertEquals(listOf(0L, 1000L, 1000L, 1000L), series.map { it.income })
        assertEquals(listOf(0L, 0L, 0L, 300L), series.map { it.expense })
    }

    @Test fun monthlySeriesHandlesYearBoundaryAndPartialLastMonth() {
        val series = FinanceStatistics.cashSeries(listOf(
            row("2025-12-20", true, 5000), row("2026-01-01", false, 1000),
            row("2026-02-17", true, 2000), row("2026-02-18", false, 9000)
        ), "2025-12-01", "2026-02-17")
        assertEquals(listOf("2025-11-30", "2025-12-31", "2026-01-31", "2026-02-17"), series.map { it.date })
        assertEquals(7000L, series.last().income)
        assertEquals(1000L, series.last().expense)
    }

    @Test fun fullMonthKeepsDailyResolutionAndLeapDay() {
        val ends = FinanceStatistics.bucketEnds("2024-02-01", "2024-02-29")
        assertEquals(29, ends.size)
        assertEquals("2024-02-29", ends.last())
        assertEquals(31, FinanceStatistics.bucketEnds("2026-01-01", "2026-01-31").size)
    }

    @Test fun invalidRangeAndEmptyTransactionsDoNotInventValues() {
        assertTrue(FinanceStatistics.cashSeries(emptyList(), "2026-12-01", "2026-11-17").isEmpty())
        assertTrue(FinanceStatistics.expenseCategories(emptyList(), emptyList(), "2026-11-01", "2026-11-17").isEmpty())
        assertTrue(FinanceStatistics.cashSeries(emptyList(), "2026-11-01", "2026-11-03").all { it.income == 0L && it.expense == 0L })
    }
}
