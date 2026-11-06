package com.anasikus.lifeassistant.domain

import com.anasikus.lifeassistant.data.CalendarEventEntity
import com.anasikus.lifeassistant.data.CalendarEventOverrideEntity
import com.anasikus.lifeassistant.data.FinanceTransactionEntity
import com.anasikus.lifeassistant.data.ShiftDayEntity
import com.anasikus.lifeassistant.data.ShiftPatternEntity

data class FinanceSource(val key: String, val name: String, val accrued: Long, val plannedAdvance: Long? = null)

data class FinanceTotals(
    val earned: Long,
    val income: Long,
    val expense: Long,
    val openingCash: Long = 0
) {
    val cash: Long get() = openingCash + income - expense
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

        val paidEvents = events.filter {
            it.hourlyRateCents != null || it.oneTimePaymentCents != null || it.payAmountCents != null
        }
        val overridesByEvent = overrides.groupBy { it.eventId }
        paidEvents.forEach { event ->
            val exceptions = overridesByEvent[event.id].orEmpty()
            val firstEvent = minOf(event.startDate, exceptions.minOfOrNull { it.date } ?: event.startDate)
            val byOccurrence = exceptions.associateBy { it.occurrenceDate }
            val instances = CalendarEventRules.expand(listOf(event), firstEvent, CalendarDateUtils.endOfMonth(today), exceptions)
                .distinctBy { it.occurrenceDate }
                .groupBy { it.instanceDate.take(7) }
            val accrued = instances.values.sumOf { month ->
                month.withIndex().sumOf { (index, occurrence) ->
                    if (!ended(occurrence.instanceDate, occurrence.instanceStartMinute, occurrence.instanceDurationMinutes, today, minute)) {
                        0L
                    } else {
                        val exception = byOccurrence[occurrence.occurrenceDate]
                        val mode = WorkPay.from(exception?.payMode ?: event.payMode)
                        val amount = exception?.payAmountCents ?: event.payAmountCents ?: event.hourlyRateCents ?: 0L
                        event.oneTimePaymentCents ?: when (mode) {
                            WorkPay.SHIFT -> amount
                            WorkPay.ADVANCE -> 0L
                            WorkPay.SALARY -> amount / month.size + if (index < amount % month.size) 1L else 0L
                            WorkPay.HOURLY -> ShiftRules.payment(
                                ShiftDayEntity(
                                    date = occurrence.instanceDate,
                                    status = "WORK",
                                    durationMinutes = occurrence.instanceDurationMinutes,
                                    hourlyRateCents = amount
                                )
                            )
                        }
                    }
                }
            }
            val advance = event.payAmountCents.takeIf { WorkPay.from(event.payMode) == WorkPay.ADVANCE }
            add(FinanceSource("event:${event.id}", event.title, accrued, advance))
        }
    }

    private fun ended(date: String, start: Int, duration: Int, today: String, minute: Int): Boolean {
        val end = start + duration
        val endDate = if (end >= 1440) CalendarDateUtils.addDays(date, 1) else date
        return endDate < today || (endDate == today && end % 1440 <= minute)
    }

    fun outstanding(source: FinanceSource, payments: List<FinanceTransactionEntity>): Long {
        val paid = payments.filter { it.income && it.sourceKey == source.key }.sumOf { it.amountCents }
        return source.accrued - paid
    }

    fun monthTotals(
        sources: List<FinanceSource>,
        payments: List<FinanceTransactionEntity>,
        monthStart: String,
        cutoffDate: String
    ): FinanceTotals {
        val throughCutoff = payments.filter { it.date <= cutoffDate }
        val (previous, current) = throughCutoff.partition { it.date < monthStart }
        return totals(sources, current).copy(
            earned = sources.sumOf { outstanding(it, throughCutoff) },
            openingCash = previous.sumOf { if (it.income) it.amountCents else -it.amountCents }
        )
    }

    fun totals(sources: List<FinanceSource>, payments: List<FinanceTransactionEntity>): FinanceTotals =
        FinanceTotals(
            earned = sources.sumOf { outstanding(it, payments) },
            income = payments.filter { it.income }.sumOf { it.amountCents },
            expense = payments.filterNot { it.income }.sumOf { it.amountCents }
        )
}
