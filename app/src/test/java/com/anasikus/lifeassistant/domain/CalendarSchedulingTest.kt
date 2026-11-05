package com.anasikus.lifeassistant.domain

import com.anasikus.lifeassistant.data.CalendarEventEntity
import com.anasikus.lifeassistant.data.CalendarEventOverrideEntity
import com.anasikus.lifeassistant.data.CalendarTaskPlacementEntity
import com.anasikus.lifeassistant.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarSchedulingTest {
    private val date = CalendarDateUtils.addDays(CalendarDateUtils.today(), 1)
    private fun task(id: Int) = TaskEntity(id = id, title = "Задача $id", startDate = date)
    private fun placement(id: Int, start: Int, duration: Int) =
        CalendarTaskPlacementEntity(id, date, start, duration)
    private val shift get() = CalendarEventOccurrence(
        CalendarEventEntity(id = 1, title = "Работа", startDate = date, startMinute = 480, durationMinutes = 720), date
    )

    @Test fun manualProgrammingStaysInsideTwelveHourShift() {
        val result = CalendarTaskScheduler.schedule(
            listOf(task(1)), listOf(shift), date, date, listOf(placement(1, 720, 240))
        ).single()
        assertEquals(720, result.startMinute)
        assertEquals(240, result.durationMinutes)
    }

    @Test fun twoManuallyPlacedTasksCanOverlapEachOtherAndEvent() {
        val result = CalendarTaskScheduler.schedule(
            listOf(task(1), task(2)), listOf(shift), date, date,
            listOf(placement(1, 720, 240), placement(2, 840, 240))
        )
        assertEquals(listOf(720, 840), result.map { it.startMinute })
    }

    @Test fun automaticTasksUseSeparateFreeSlotsAroundShift() {
        val result = CalendarTaskScheduler.schedule(listOf(task(1), task(2)), listOf(shift), date, date)
        assertEquals(listOf(420, 1200), result.map { it.startMinute })
    }

    @Test fun manualSlotIsReservedBeforeHigherPriorityAutomaticTask() {
        val urgent = task(1).copy(deadline = CalendarDateUtils.today())
        val result = CalendarTaskScheduler.schedule(
            listOf(urgent, task(2)), emptyList(), date, date, listOf(placement(2, 420, 60))
        )
        assertEquals(480, result.single { it.task.id == 1 }.startMinute)
        assertEquals(420, result.single { it.task.id == 2 }.startMinute)
    }

    @Test fun manualTaskIsNotMovedWhenAnotherDateIsViewed() {
        val result = CalendarTaskScheduler.schedule(
            listOf(task(1)), emptyList(), date, date,
            listOf(placement(1, 720, 240).copy(date = CalendarDateUtils.addDays(date, 1)))
        )
        assertTrue(result.isEmpty())
    }

    @Test fun hiddenTaskDoesNotReappearOnExcludedDay() {
        val result = CalendarTaskScheduler.schedule(
            listOf(task(1)), emptyList(), date, date,
            listOf(placement(1, 720, 60).copy(hidden = true))
        )
        assertTrue(result.isEmpty())
    }

    @Test fun automaticTaskDoesNotAppearBeforeItsStartDate() {
        val result = CalendarTaskScheduler.schedule(
            listOf(task(1).copy(startDate = CalendarDateUtils.addDays(date, 1))), emptyList(), date, date
        )
        assertTrue(result.isEmpty())
    }

    @Test fun manualTaskKeepsSavedTimeInPastInsteadOfDisappearing() {
        val yesterday = CalendarDateUtils.addDays(CalendarDateUtils.today(), -1)
        val result = CalendarTaskScheduler.schedule(
            listOf(task(1).copy(startDate = yesterday)), emptyList(), yesterday, yesterday,
            listOf(placement(1, 60, 720).copy(date = yesterday))
        ).single()
        assertEquals(60, result.startMinute)
        assertEquals(720, result.durationMinutes)
    }

    @Test fun movingOneRepeatedOccurrenceKeepsOtherOccurrencesUnchanged() {
        val next = CalendarDateUtils.addDays(date, 1)
        val event = shift.event.copy(repeatDaysMask = 127)
        val result = CalendarEventRules.expand(
            listOf(event), date, next,
            listOf(CalendarEventOverrideEntity(event.id, date, date, 600, 240))
        )
        assertEquals(600, result.single { it.date == date }.startMinute)
        assertEquals(240, result.single { it.date == date }.durationMinutes)
        assertEquals(480, result.single { it.date == next }.startMinute)
        assertEquals(720, result.single { it.date == next }.durationMinutes)
    }
}
