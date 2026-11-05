package com.anasikus.lifeassistant.domain

import com.anasikus.lifeassistant.data.CalendarEventEntity
import com.anasikus.lifeassistant.data.CalendarEventOverrideEntity
import com.anasikus.lifeassistant.data.CalendarTaskPlacementEntity
import com.anasikus.lifeassistant.data.TaskEntity

/**
 * Конкретный экземпляр мероприятия в календаре.
 */
data class CalendarEventOccurrence(
    val event: CalendarEventEntity,
    /** Дата фактического отображения: yyyy-MM-dd. */
    val date: String,
    /** Дата исходного повторения. Нужна для исключений повторяющейся серии. */
    val occurrenceDate: String = date,
    val startMinute: Int = event.startMinute,
    val durationMinutes: Int = event.durationMinutes,
    /** Полное событие; date/start/duration выше описывают видимую часть дня. */
    val instanceDate: String = date,
    val instanceStartMinute: Int = startMinute,
    val instanceDurationMinutes: Int = durationMinutes
) {
    fun withSegmentRange(range: TimelineRange): CalendarEventOccurrence {
        val totalStart = instanceStartMinute + range.startMinute - startMinute
        val dayOffset = Math.floorDiv(totalStart, 1440)
        return copy(
            startMinute = range.startMinute,
            durationMinutes = range.durationMinutes,
            instanceDate = CalendarDateUtils.addDays(instanceDate, dayOffset),
            instanceStartMinute = Math.floorMod(totalStart, 1440),
            instanceDurationMinutes = (instanceDurationMinutes + range.durationMinutes - durationMinutes).coerceIn(15, 1440)
        )
    }
}

/** Задача, размещённая планировщиком в определённом временном слоте. */
data class ScheduledTask(
    val task: TaskEntity,
    val date: String,
    val startMinute: Int,
    val durationMinutes: Int = 60,
    val priority: Int
)

object CalendarEventRules {

    /**
     * Разворачивает одноразовые и повторяющиеся события в заданном периоде.
     * События из eventOverrides могут быть перенесены или отменены по одной дате.
     */
    fun expand(
        events: List<CalendarEventEntity>,
        rangeStart: String,
        rangeEnd: String,
        overrides: List<CalendarEventOverrideEntity> = emptyList()
    ): List<CalendarEventOccurrence> {
        if (rangeStart > rangeEnd) return emptyList()

        val result = mutableListOf<CalendarEventOccurrence>()

        events.forEach eventLoop@ { event ->
            val eventOverrides = overrides
                .filter { it.eventId == event.id }
                .associateBy { it.occurrenceDate }

            val candidateSourceDates = linkedSetOf<String>()

            if (event.repeatDaysMask == 0) {
                candidateSourceDates.add(event.startDate)
            } else {
                val firstDate = maxOf(event.startDate, CalendarDateUtils.addDays(rangeStart, -1))
                if (firstDate <= rangeEnd) {
                    CalendarDateUtils.datesBetween(firstDate, rangeEnd).forEach { date ->
                        val dayBit = 1 shl CalendarDateUtils.weekdayIndex(date)
                        if ((event.repeatDaysMask and dayBit) != 0) {
                            candidateSourceDates.add(date)
                        }
                    }
                }
            }

            // Добавляем исключения, перенесённые в диапазон из исходных дат за его пределами.
            eventOverrides.values
                .filter { it.date >= CalendarDateUtils.addDays(rangeStart, -1) && it.date <= rangeEnd }
                .forEach { candidateSourceDates.add(it.occurrenceDate) }

            candidateSourceDates.forEach sourceDateLoop@ { sourceDate ->
                val override = eventOverrides[sourceDate]
                if (override?.deleted == true) return@sourceDateLoop

                val actualDate = override?.date ?: sourceDate
                val start = override?.startMinute ?: event.startMinute
                val duration = override?.durationMinutes ?: event.durationMinutes
                var remaining = duration.coerceIn(15, 1440)
                var segmentDate = actualDate
                var segmentStart = start.coerceIn(0, 1439)
                while (remaining > 0) {
                    val segmentDuration = minOf(remaining, 1440 - segmentStart)
                    if (segmentDate in rangeStart..rangeEnd) {
                        result.add(
                            CalendarEventOccurrence(
                                event = event, date = segmentDate, occurrenceDate = sourceDate,
                                startMinute = segmentStart, durationMinutes = segmentDuration,
                                instanceDate = actualDate, instanceStartMinute = start,
                                instanceDurationMinutes = duration
                            )
                        )
                    }
                    remaining -= segmentDuration
                    segmentDate = CalendarDateUtils.addDays(segmentDate, 1)
                    segmentStart = 0
                }
            }
        }

        return result
            .distinctBy { "${it.event.id}_${it.occurrenceDate}_${it.date}" }
            .sortedWith(
                compareBy<CalendarEventOccurrence> { it.date }
                    .thenBy { it.startMinute }
            )
    }
}

object CalendarTaskScheduler {

    private const val DAY_START_MINUTE = 7 * 60
    private const val DAY_END_MINUTE = 23 * 60
    private const val DEFAULT_TASK_DURATION_MINUTES = 60

    // Планировщик проверяет каждую минуту, а не только четверть часа.
    private const val SEARCH_STEP_MINUTES = 1


    /** Приоритет 5 — максимальный, 1 — минимальный. */
    fun priority(
        task: TaskEntity,
        today: String = CalendarDateUtils.today()
    ): Int {
        if (task.deadline.isBlank()) return 1
        if (task.deadline < today) return 5

        val daysRemaining = CalendarDateUtils.daysBetween(today, task.deadline)
        return when {
            daysRemaining <= 0 -> 5
            daysRemaining <= 1 -> 4
            daysRemaining <= 3 -> 3
            daysRemaining <= 7 -> 2
            else -> 1
        }
    }

    /**
     * Размещает незавершённые задачи в свободное время.
     * Ручное размещение сохраняется, включая пересечения с мероприятиями. Если оно скрыто для
     * конкретного дня, задача не появится в этот день и будет искать другое окно.
     *
     * Автоматические слоты не пересекаются с мероприятиями и другими задачами.
     * Ручные слоты могут пересекаться: например, работа и программирование.
     */
    fun schedule(
        tasks: List<TaskEntity>,
        events: List<CalendarEventOccurrence>,
        rangeStart: String,
        rangeEnd: String,
        placements: List<CalendarTaskPlacementEntity> = emptyList()
    ): List<ScheduledTask> {
        if (rangeStart > rangeEnd) return emptyList()

        val today = CalendarDateUtils.today()
        val effectiveStart = maxOf(rangeStart, today)
        val dates = CalendarDateUtils.datesBetween(effectiveStart, rangeEnd)
        val placementsByTaskId = placements.associateBy { it.taskId }
        val result = mutableListOf<ScheduledTask>()

        fun conflictsWithEvent(date: String, start: Int, end: Int): Boolean =
            events.any { occurrence ->
                occurrence.date == date &&
                        start < occurrence.startMinute + occurrence.durationMinutes &&
                        end > occurrence.startMinute
            }

        fun canPlace(date: String, start: Int, duration: Int): Boolean {
            val end = start + duration
            if (
                start < DAY_START_MINUTE ||
                end > DAY_END_MINUTE ||
                conflictsWithEvent(date, start, end) ||
                result.any { it.date == date && start < it.startMinute + it.durationMinutes && end > it.startMinute }
            ) {
                return false
            }

            return true
        }

        val pendingTasks = tasks
            .filterNot { it.completed }
            .sortedWith(
                compareByDescending<TaskEntity> { priority(it, today) }
                    .thenBy { it.deadline.ifBlank { "9999-12-31" } }
                    .thenBy { it.id }
            )

        // Сначала резервируем ВСЕ ручные слоты: автоматическая задача более
        // высокого приоритета не должна занять место ещё не обработанной ручной.
        val automaticTasks = pendingTasks.filter { task ->
            val saved = placementsByTaskId[task.id]
            if (saved == null || saved.hidden) {
                true
            } else {
                if (saved.date in rangeStart..rangeEnd) {
                    val duration = saved.durationMinutes.coerceIn(15, 24 * 60)
                    result.add(
                        ScheduledTask(
                            task = task,
                            date = saved.date,
                            startMinute = saved.startMinute.coerceIn(0, 24 * 60 - duration),
                            durationMinutes = duration,
                            priority = priority(task, today)
                        )
                    )
                }
                false
            }
        }

        automaticTasks.forEach { task ->
            val savedPlacement = placementsByTaskId[task.id]
            val duration = (savedPlacement?.durationMinutes ?: DEFAULT_TASK_DURATION_MINUTES)
                .coerceIn(15, 24 * 60)
            val excludedDate = savedPlacement?.takeIf { it.hidden }?.date

            // Иначе ищем ранний доступный слот, кроме скрытой для задачи даты.
            var placed = false
            for (date in dates) {
                if (date == excludedDate || (task.startDate.isNotBlank() && date < task.startDate)) continue

                var minute = DAY_START_MINUTE
                while (minute + duration <= DAY_END_MINUTE) {
                    if (canPlace(date, minute, duration)) {
                        result.add(
                            ScheduledTask(
                                task = task,
                                date = date,
                                startMinute = minute,
                                durationMinutes = duration,
                                priority = priority(task, today)
                            )
                        )
                        placed = true
                        break
                    }
                    minute += SEARCH_STEP_MINUTES
                }
                if (placed) break
            }
        }

        return result.sortedWith(
            compareBy<ScheduledTask> { it.date }
                .thenBy { it.startMinute }
                .thenByDescending { it.priority }
        )
    }
}
