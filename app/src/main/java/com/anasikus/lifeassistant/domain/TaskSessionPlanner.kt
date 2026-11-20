package com.anasikus.lifeassistant.domain

import com.anasikus.lifeassistant.data.*

object TaskSessionPlanner {
    const val MAX_BUDGET = 600_000
    fun remaining(task: TaskEntity): Int = (task.estimatedDurationMinutes - task.spentMinutes).coerceAtLeast(0)
    fun ended(session: TaskSessionEntity, today: String, minute: Int): Boolean =
        session.date < today || session.date == today && session.startMinute + session.durationMinutes <= minute

    /** Future automatic blocks are replaceable; begun and manually placed blocks are stable. */
    fun plan(tasks: List<TaskEntity>, events: List<CalendarEventOccurrence>, retained: List<TaskSessionEntity>,
             today: String, minute: Int, end: String, hidden: List<CalendarTaskPlacementEntity> = emptyList()): List<TaskSessionEntity> {
        val rows = retained.toMutableList()
        val parents = tasks.filter { it.isProject }.map { it.id }.toSet()
        val pending = tasks.filter { !it.completed && !it.archived && !it.frozen && it.id !in parents }
            .sortedWith(TaskRules.comparator(today))
        for (task in pending) {
            var left = (remaining(task) - retained.filter { it.taskId == task.id && !it.accounted }.sumOf { it.durationMinutes }).coerceAtLeast(0)
            for (date in CalendarDateUtils.datesBetween(maxOf(today, task.startDate.ifBlank { today }), end)) {
                if (left == 0) break
                if (hidden.any { it.taskId == task.id && it.hidden && it.date == date }) continue
                var start = if (date == today) maxOf(420, minute + 1) else 420
                val blocked = (events.filter { it.date == date }.map { it.startMinute to it.startMinute + it.durationMinutes } +
                    rows.filter { it.date == date && !it.accounted }.map { it.startMinute to it.startMinute + it.durationMinutes }).sortedBy { it.first }
                for ((from, to) in blocked + listOf(1380 to 1440)) {
                    val gapEnd = minOf(from, 1380)
                    while (left > 0 && gapEnd - start >= minOf(15, left)) {
                        val duration = minOf(left, gapEnd - start, 240)
                        rows.add(TaskSessionEntity(taskId = task.id, date = date, startMinute = start, durationMinutes = duration))
                        start += duration; left -= duration
                    }
                    start = maxOf(start, to)
                    if (start >= 1380 || left == 0) break
                }
            }
        }
        return rows
    }
}
