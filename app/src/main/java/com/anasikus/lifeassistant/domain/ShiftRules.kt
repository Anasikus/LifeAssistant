package com.anasikus.lifeassistant.domain

import com.anasikus.lifeassistant.data.ShiftDayEntity
import com.anasikus.lifeassistant.data.ShiftPatternEntity
import com.anasikus.lifeassistant.data.SpecialDateEntity
import java.math.BigDecimal
import java.math.RoundingMode

object ShiftRules {
    fun days(start: String, end: String, pattern: ShiftPatternEntity?, overrides: List<ShiftDayEntity>): List<ShiftDayEntity> {
        val manual = overrides.associateBy { it.date }
        return CalendarDateUtils.datesBetween(start, end).mapNotNull { date ->
            manual[date] ?: pattern?.takeIf { it.enabled && date >= it.anchorDate }?.let { p ->
                val work = if (p.mode == "WEEKDAYS") CalendarDateUtils.weekdayIndex(date) < 5 else {
                    val cycle = (p.workDays + p.offDays).coerceAtLeast(1)
                    CalendarDateUtils.daysBetween(p.anchorDate, date) % cycle < p.workDays
                }
                ShiftDayEntity(date, if (work) "WORK" else "OFF", p.startMinute, p.durationMinutes, p.hourlyRateCents)
            }
        }
    }
    fun payment(day: ShiftDayEntity): Long = if (day.status == "WORK")
        BigDecimal.valueOf(day.hourlyRateCents).multiply(BigDecimal.valueOf(day.durationMinutes.toLong()))
            .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP).longValueExact() else 0

    data class Forecast(val period: Int, val matchPercent: Int, val days: List<ShiftDayEntity>)

    /** Ищем повторение подтверждённых рабочих/выходных; пробелы не считаем выходными. */
    fun forecast(history: List<ShiftDayEntity>, start: String, end: String): Forecast? {
        val known = history.filter { !it.predicted && it.date < start && it.status in listOf("WORK", "OFF") }
            .sortedBy { it.date }.associateBy { it.date }
        if (known.size < 28 || known.keys.map { it.take(7) }.distinct().size < 2 || known.values.map { it.status }.distinct().size < 2) return null
        data class Candidate(val period: Int, val score: Int, val compared: Int)
        val candidates = (2..31).mapNotNull { period ->
            val pairs = known.values.mapNotNull { day -> known[CalendarDateUtils.addDays(day.date, -period)]?.let { day to it } }
            if (pairs.size < maxOf(14, period) || pairs.takeLast(minOf(14, pairs.size)).count { it.first.status == it.second.status } < minOf(14, pairs.size) * 0.85) null
            else Candidate(period, pairs.count { it.first.status == it.second.status } * 100 / pairs.size, pairs.size)
        }.filter { it.score >= 90 }
        val best = candidates.sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.period }).firstOrNull() ?: return null
        val generated = known.toMutableMap()
        val last = known.keys.maxOrNull() ?: return null
        CalendarDateUtils.datesBetween(CalendarDateUtils.addDays(last, 1), end).forEach { date ->
            generated[CalendarDateUtils.addDays(date, -best.period)]?.let { source ->
                generated[date] = source.copy(date = date, predicted = true, confidence = best.score)
            }
        }
        return Forecast(best.period, best.score, generated.values.filter { it.predicted && it.date in start..end }.sortedBy { it.date })
            .takeIf { it.days.isNotEmpty() }
    }
    fun specialDatesFor(date: String, values: List<SpecialDateEntity>): List<SpecialDateEntity> = values.filter {
        if (it.yearly) date.substring(5) == it.date.substring(5) && date >= it.date else date == it.date
    }
    fun cents(text: String): Long? = runCatching {
        val amount = BigDecimal(text.trim().replace(',', '.'))
        require(amount >= BigDecimal.ZERO && amount <= BigDecimal("1000000000"))
        amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
    }.getOrNull()
    fun money(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString() + " ₽"
}
