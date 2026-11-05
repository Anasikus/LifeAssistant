package com.anasikus.lifeassistant.domain

import com.anasikus.lifeassistant.data.*
import com.anasikus.lifeassistant.repository.HolidayImporter
import org.junit.Assert.*
import org.junit.Test

class CalendarPlanningTest {
    @Test fun sleepAcrossMidnightHasFullDurationAndTwoDailySegments() {
        assertEquals(450, CalendarDisplay.endToDuration(1350, 360))
        val event = CalendarEventEntity(id = 1, title = "Сон", startDate = "2026-11-05", startMinute = 1350, durationMinutes = 450)
        val result = CalendarEventRules.expand(listOf(event), "2026-11-05", "2026-11-06")
        assertEquals(listOf(90, 360), result.map { it.durationMinutes })
        assertEquals(listOf(1350, 0), result.map { it.startMinute })
        assertTrue(result.all { it.instanceDurationMinutes == 450 && it.instanceDate == "2026-11-05" })
    }

    @Test fun firstDayOfMonthIncludesPreviousNightsContinuation() {
        val event = CalendarEventEntity(id = 1, title = "Сон", startDate = "2026-10-31", startMinute = 1350, durationMinutes = 450, repeatDaysMask = 127)
        val result = CalendarEventRules.expand(listOf(event), "2026-11-01", "2026-11-01")
        assertEquals(2, result.size)
        assertEquals(360, result.first().durationMinutes)
        assertEquals("2026-10-31", result.first().occurrenceDate)
    }

    @Test fun movingContinuationMovesWholeInstanceWithoutLosingDuration() {
        val event = CalendarEventEntity(id = 1, title = "Сон", startDate = "2026-11-05", startMinute = 1350, durationMinutes = 450)
        val continuation = CalendarEventRules.expand(listOf(event), "2026-11-06", "2026-11-06").single()
        val moved = continuation.withSegmentRange(TimelineRange(120, 360))
        assertEquals("2026-11-06", moved.instanceDate)
        assertEquals(30, moved.instanceStartMinute)
        assertEquals(450, moved.instanceDurationMinutes)
    }

    @Test fun listUsesHoursAndMinutesWithoutChangingStoredValue() {
        assertEquals("8 ч", CalendarDisplay.duration(480))
        assertEquals("12 ч", CalendarDisplay.duration(720))
        assertEquals("7 ч 30 мин", CalendarDisplay.duration(450))
        assertEquals("22:30–06:00 (+1 день) · 7 ч 30 мин", CalendarDisplay.interval(1350, 450))
    }

    @Test fun rotationDoesNotRestartOnFirstDayOfMonth() {
        val pattern = ShiftPatternEntity(mode = "CYCLE", anchorDate = "2026-10-30", workDays = 2, offDays = 2)
        val result = ShiftRules.days("2026-10-30", "2026-11-04", pattern, emptyList())
        assertEquals(listOf("WORK", "WORK", "OFF", "OFF", "WORK", "WORK"), result.map { it.status })
    }

    @Test fun sickDayOverridesWeekdayPatternAndHasNoInventedSalary() {
        val pattern = ShiftPatternEntity(anchorDate = "2026-11-01", hourlyRateCents = 20000)
        val result = ShiftRules.days("2026-11-02", "2026-11-03", pattern, listOf(ShiftDayEntity("2026-11-02", "SICK")))
        assertEquals("SICK", result[0].status)
        assertEquals(0L, ShiftRules.payment(result[0]))
        assertEquals(240000L, ShiftRules.payment(result[1]))
    }

    @Test fun forecastRequiresHistoryAndDoesNotLearnFromItsOwnGuesses() {
        val history = CalendarDateUtils.datesBetween("2026-09-01", "2026-10-31").map { date ->
            ShiftDayEntity(date, if (CalendarDateUtils.weekdayIndex(date) < 5) "WORK" else "OFF")
        }
        val result = ShiftRules.forecast(history, "2026-11-01", "2026-11-30")!!
        assertEquals(7, result.period)
        assertEquals(100, result.matchPercent)
        assertEquals(30, result.days.size)
        assertTrue(result.days.all { it.predicted })
        assertNull(ShiftRules.forecast(history.map { it.copy(predicted = true) }, "2026-11-01", "2026-11-30"))
        assertNull(ShiftRules.forecast(history.take(10), "2026-11-01", "2026-11-30"))
    }

    @Test fun familyDateRepeatsInFollowingYears() {
        val birthday = SpecialDateEntity(title = "День рождения", date = "2001-11-05")
        assertEquals(listOf(birthday), ShiftRules.specialDatesFor("2035-11-05", listOf(birthday)))
        assertTrue(ShiftRules.specialDatesFor("2035-11-06", listOf(birthday)).isEmpty())
    }

    @Test fun moneyUsesExactCentsAndRejectsSubCentAmounts() {
        assertEquals(12345L, ShiftRules.cents("123,45")!!)
        assertNull(ShiftRules.cents("1.999"))
        assertNull(ShiftRules.cents("-1"))
    }

    @Test fun icsParserUnfoldsNamesAndDoesNotInventComplexRecurrences() {
        val text = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            DTSTART;VALUE=DATE:20261105
            SUMMARY:Семейный
             праздник
            RRULE:FREQ=YEARLY
            END:VEVENT
            BEGIN:VEVENT
            DTSTART;VALUE=DATE:20261106
            SUMMARY:Сложная дата
            RRULE:FREQ=MONTHLY;BYDAY=FR
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val result = HolidayImporter.parse(text, "https://example.test/dates.ics").single()
        assertEquals("Семейныйпраздник", result.title)
        assertEquals("2026-11-05", result.date)
        assertTrue(result.yearly)
    }
}
