package com.anasikus.lifeassistant.domain

import com.anasikus.lifeassistant.data.TaskEntity

data class TaskProductivityDay(val date: String, val completed: Int, val estimatedMinutes: Int, val late: Int)

object TaskStatistics {
    /** Archive membership does not change completion history. No tracked-time claim is made. */
    fun days(tasks: List<TaskEntity>, start: String, end: String): List<TaskProductivityDay> {
        val finished = tasks.filter { it.completed && it.completedAt != null && !it.isProject }.groupBy { it.completedAt }
        return CalendarDateUtils.datesBetween(start, end).map { date ->
            val rows = finished[date].orEmpty()
            TaskProductivityDay(date, rows.size, rows.sumOf { it.estimatedDurationMinutes.coerceIn(15, TaskSessionPlanner.MAX_BUDGET) },
                rows.count { it.deadline.isNotBlank() && date > it.deadline })
        }
    }
}
