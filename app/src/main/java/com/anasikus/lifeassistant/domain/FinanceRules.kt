package com.anasikus.lifeassistant.domain

import com.anasikus.lifeassistant.data.CalendarEventEntity
import com.anasikus.lifeassistant.data.CalendarEventOverrideEntity
import com.anasikus.lifeassistant.data.FinanceTransactionEntity
import com.anasikus.lifeassistant.data.ShiftDayEntity
import com.anasikus.lifeassistant.data.ShiftPatternEntity

data class FinanceSource(val key: String, val name: String, val accrued: Long)

data class FinanceTotals(val earned: Long, val income: Long, val expense: Long) {
    val cash: Long get() = income - expense
}

object FinanceRules {
    fun sources(
        events: List<CalendarEventEntity>,
        overrides: List<CalendarEventOverrideEntity>,
        pattern: ShiftPatternEntity?,
        days: List<ShiftDayEntity>,
        today: String,
        minute: Int
    ): List<FinanceSource> = buildList {
        val firstShift = (days.map { it.date } + listOfNotNull(pattern?.anchorDate)).minOrNull()
        if (firstShift != null) {
            val accrued = ShiftRules.days(firstShift, today, pattern, days)
                .filter { !it.predicted && ended(it.date, it.startMinute, it.durationMinutes, today, minute) }
                .sumOf(ShiftRules::payment)
            add(FinanceSource("schedule", "График смен", accrued))
        }

        val paidEvents = events.filter { it.hourlyRateCents != null || it.oneTimePaymentCents != null }
        val firstEvent = (paidEvents.map { it.startDate } +
            overrides.filter { override -> paidEvents.any { it.id == override.eventId } }.map { it.date }).minOrNull()
        if (firstEvent != null) {
            val accruedByEvent = CalendarEventRules.expand(paidEvents, firstEvent, today, overrides)
                .distinctBy { it.event.id to it.occurrenceDate }
                .filter { ended(it.instanceDate, it.instanceStartMinute, it.instanceDurationMinutes, today, minute) }
                .groupBy { it.event.id }
                .mapValues { (_, occurrences) ->
                    occurrences.sumOf { occurrence ->
                        occurrence.event.oneTimePaymentCents ?: ShiftRules.payment(
                            ShiftDayEntity(
                                date = occurrence.instanceDate,
                                status = "WORK",
                                durationMinutes = occurrence.instanceDurationMinutes,
                                hourlyRateCents = occurrence.event.hourlyRateCents ?: 0L
                            )
                        )
                    }
                }
            paidEvents.forEach { event ->
                add(FinanceSource("event:${event.id}", event.title, accruedByEvent[event.id] ?: 0L))
            }
        }
    }

    private fun ended(date: String, start: Int, duration: Int, today: String, minute: Int): Boolean {
        val end = start + duration
        val endDate = if (end >= 1440) CalendarDateUtils.addDays(date, 1) else date
        return endDate < today || (endDate == today && end % 1440 <= minute)
    }

    fun outstanding(source: FinanceSource, payments: List<FinanceTransactionEntity>): Long {
        val paid = payments.filter { it.income && it.sourceKey == source.key }.sumOf { it.amountCents }
        return (source.accrued - paid).coerceAtLeast(0L)
    }

    fun totals(sources: List<FinanceSource>, payments: List<FinanceTransactionEntity>): FinanceTotals =
        FinanceTotals(
            earned = sources.sumOf { outstanding(it, payments) },
            income = payments.filter { it.income }.sumOf { it.amountCents },
            expense = payments.filterNot { it.income }.sumOf { it.amountCents }
        )
}
