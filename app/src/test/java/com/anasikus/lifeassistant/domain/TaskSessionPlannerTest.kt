package com.anasikus.lifeassistant.domain

import com.anasikus.lifeassistant.data.*
import org.junit.Assert.*
import org.junit.Test

class TaskSessionPlannerTest {
    private val today = "2026-11-20"
    private fun task() = TaskEntity(id = 1, title = "Приложение", estimatedDurationMinutes = 48 * 60)

    @Test fun fortyEightHoursUseSeveralFreeWindowsWithoutLosingMinutes() {
        val blocked = CalendarEventRules.expand(listOf(
            CalendarEventEntity(id = 1, title = "Работа", startDate = today, startMinute = 600, durationMinutes = 120),
            CalendarEventEntity(id = 2, title = "Занято", startDate = today, startMinute = 780, durationMinutes = 600)
        ), today, today)
        val slots = TaskSessionPlanner.plan(listOf(task()), blocked, emptyList(), today, 600, "2026-11-25")
        assertEquals(48 * 60, slots.sumOf { it.durationMinutes })
        assertEquals(60, slots.filter { it.date == today }.sumOf { it.durationMinutes })
        assertTrue(slots.map { it.date }.distinct().size > 1)
        assertTrue(slots.all { it.durationMinutes in 15..240 })
        slots.forEach { slot ->
            assertFalse(blocked.any { it.date == slot.date && slot.startMinute < it.startMinute + it.durationMinutes && slot.startMinute + slot.durationMinutes > it.startMinute })
        }
    }

    @Test fun runningSlotStaysFixedAndDoesNotConsumeItsBudgetTwice() {
        val running = TaskSessionEntity(1, 1, today, 500, 120, automatic = true)
        val result = TaskSessionPlanner.plan(listOf(task()), emptyList(), listOf(running), today, 550, "2026-11-25")
        assertEquals(running, result.first())
        assertEquals(2880, result.sumOf { it.durationMinutes })
        assertTrue(result.drop(1).all { it.date > today || it.startMinute >= 620 })
        assertFalse(TaskSessionPlanner.ended(running, today, 619))
        assertTrue(TaskSessionPlanner.ended(running, today, 620))
    }

    @Test fun exhaustedCompletedFrozenAndProjectTasksAreNeverAdded() {
        val items = listOf(task().copy(spentMinutes = 2880), task().copy(id = 2, completed = true),
            task().copy(id = 3, frozen = true), task().copy(id = 4, isProject = true))
        assertTrue(TaskSessionPlanner.plan(items, emptyList(), emptyList(), today, 500, "2026-11-30").isEmpty())
    }

    @Test fun urgentLowPriorityAlwaysGetsEarlierWindowsThanOrdinaryHighPriority() {
        val urgent = task().copy(id = 1, deadline = today, priorityLevel = 0, estimatedDurationMinutes = 60)
        val high = task().copy(id = 2, deadline = "2026-12-20", priorityLevel = 2, estimatedDurationMinutes = 60)
        val result = TaskSessionPlanner.plan(listOf(high, urgent), emptyList(), emptyList(), today, 600, today)
        assertEquals(listOf(1, 2), result.map { it.taskId })
        assertEquals(listOf(601, 661), result.map { it.startMinute })
    }

    @Test fun decompositionKeepsExactRemainingBudgetAndUsesExplicitChecklist() {
        val source = task().copy(spentMinutes = 121, description = "- Модель данных\n- Форма задачи\n- Проверки")
        val steps = TaskDecomposer.suggest(source)
        assertEquals(listOf("Модель данных", "Форма задачи", "Проверки"), steps.map { it.title })
        assertEquals(2759, steps.sumOf { it.minutes })
        assertTrue(steps.all { it.minutes >= 15 })
    }

    @Test fun projectProgressCountsSpentBeforeSplitAndChildBudgetExtensions() {
        val parent = task().copy(isProject = true, spentMinutes = 60)
        val child = task().copy(id = 2, parentId = 1, estimatedDurationMinutes = 3000, spentMinutes = 120)
        val value = TaskProgress.of(parent, listOf(parent, child))
        assertEquals(3060, value.total)
        assertEquals(180, value.spent)
        assertEquals(2880, value.remaining)
    }
}
