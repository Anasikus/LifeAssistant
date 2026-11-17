package com.anasikus.lifeassistant.domain

import com.anasikus.lifeassistant.data.*
import org.junit.Assert.*
import org.junit.Test

class TaskPlanningTest {
    private val today = "2026-11-17"
    private fun task(id: Int, deadline: String = "", level: Int = 1, duration: Int = 60) =
        TaskEntity(id = id, title = "Task $id", startDate = today, deadline = deadline,
            priorityLevel = level, estimatedDurationMinutes = duration)

    @Test fun automaticTaskUsesEstimateAndOnlyFutureFreeTime() {
        val event = CalendarEventEntity(id = 1, title = "Work", startDate = today, startMinute = 600, durationMinutes = 180)
        val rows = CalendarTaskScheduler.schedule(listOf(task(1, duration = 90)),
            CalendarEventRules.expand(listOf(event), today, today), today, today,
            today = today, earliestStartMinute = 631)
        assertEquals(780, rows.single().startMinute)
        assertEquals(90, rows.single().durationMinutes)
    }

    @Test fun taskDoesNotGoBackIntoPastWhenTodayIsFull() {
        val next = CalendarDateUtils.addDays(today, 1)
        val rows = CalendarTaskScheduler.schedule(listOf(task(1)), emptyList(), today, next,
            today = today, earliestStartMinute = 1351)
        assertEquals(next, rows.single().date)
        assertEquals(420, rows.single().startMinute)
        assertTrue(CalendarTaskScheduler.schedule(listOf(task(1)), emptyList(), today, today,
            today = today, earliestStartMinute = 1351).isEmpty())
    }

    @Test fun overdueAndUrgentLowPriorityAlwaysPrecedeOrdinaryHighPriority() {
        val tasks = listOf(task(3, "2026-12-20", 2), task(2, "2026-11-20", 0), task(1, "2026-11-16", 0))
        assertEquals(listOf(1, 2, 3), tasks.sortedWith(TaskRules.comparator(today)).map { it.id })
        val rows = CalendarTaskScheduler.schedule(tasks, emptyList(), today, today,
            today = today, earliestStartMinute = 600)
        assertEquals(listOf(1, 2, 3), rows.map { it.task.id })
        assertEquals(listOf(600, 660, 720), rows.map { it.startMinute })
    }

    @Test fun manualPriorityOrdersTasksInsideSameDeadlineGroup() {
        val tasks = listOf(task(1, level = 0), task(2, level = 2))
        val rows = CalendarTaskScheduler.schedule(tasks, emptyList(), today, today,
            today = today, earliestStartMinute = 600)
        assertEquals(listOf(2, 1), rows.map { it.task.id })
    }

    @Test fun manualPastSlotIsPreservedButArchiveNeverSchedules() {
        val manual = CalendarTaskPlacementEntity(1, today, 600, 120)
        val rows = CalendarTaskScheduler.schedule(listOf(task(1), task(2).copy(archived = true)),
            emptyList(), today, today, listOf(manual), today = today, earliestStartMinute = 901)
        assertEquals(1, rows.size)
        assertEquals(600, rows.single().startMinute)
        assertEquals(120, rows.single().durationMinutes)
    }

    @Test fun productivityCountsArchivesButNeverCountsUnfinishedArchiveAsCompleted() {
        val rows = listOf(
            task(1, "2026-11-16", duration = 90).copy(completed = true, completedAt = today, archived = true),
            task(2, "2026-11-18", duration = 30).copy(completed = true, completedAt = today),
            task(3, duration = 180).copy(archived = true),
            task(4).copy(completed = true, completedAt = "2026-11-16")
        )
        val days = TaskStatistics.days(rows, "2026-11-16", "2026-11-18")
        assertEquals(listOf(1, 2, 0), days.map { it.completed })
        assertEquals(120, days[1].estimatedMinutes)
        assertEquals(1, days[1].late)
    }
}
