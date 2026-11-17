package com.anasikus.lifeassistant.domain

import com.anasikus.lifeassistant.data.ExpenseCategoryEntity
import com.anasikus.lifeassistant.data.FinanceTransactionEntity

/** Cumulative paid income and spending, in kopecks, with a zero baseline. */
data class FinanceChartPoint(val date: String, val income: Long, val expense: Long)
data class FinanceCategoryTotal(val name: String, val amount: Long)

object FinanceStatistics {
    fun bucketEnds(start: String, end: String): List<String> {
        if (start > end) return emptyList()
        if (CalendarDateUtils.daysBetween(start, end) < 31) return CalendarDateUtils.datesBetween(start, end)
        return buildList {
            var month = CalendarDateUtils.startOfMonth(start)
            while (month <= end) {
                add(minOf(CalendarDateUtils.endOfMonth(month), end))
                month = CalendarDateUtils.addMonths(month, 1)
            }
        }
    }

    fun cashSeries(transactions: List<FinanceTransactionEntity>, start: String, end: String): List<FinanceChartPoint> {
        val ends = bucketEnds(start, end)
        if (ends.isEmpty()) return emptyList()
        val rows = transactions.filter { !it.transfer && it.date in start..end }.sortedBy { it.date }
        var income = 0L
        var expense = 0L
        var cursor = 0
        return buildList {
            add(FinanceChartPoint(CalendarDateUtils.addDays(start, -1), 0, 0))
            ends.forEach { date ->
                while (cursor < rows.size && rows[cursor].date <= date) {
                    val entry = rows[cursor++]
                    if (entry.income) income += entry.amountCents else expense += entry.amountCents
                }
                add(FinanceChartPoint(date, income, expense))
            }
        }
    }

    fun expenseCategories(
        transactions: List<FinanceTransactionEntity>, categories: List<ExpenseCategoryEntity>, start: String, end: String
    ): List<FinanceCategoryTotal> {
        val names = categories.associate { it.id to it.name }
        return transactions.filter { !it.transfer && !it.income && it.date in start..end && it.amountCents > 0 }
            .groupBy { names[it.expenseCategoryId] ?: "Без категории" }
            .map { (name, rows) -> FinanceCategoryTotal(name, rows.sumOf { it.amountCents }) }
            .sortedWith(compareByDescending<FinanceCategoryTotal> { it.amount }.thenBy { it.name })
    }
}
