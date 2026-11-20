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
    val instanceDurationMinutes: Int = durationMinutes,
    val plannedPayment: PlannedPayment? = null
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
    val priority: Int,
    val sessionId: Long = 0,
    val accounted: Boolean = false,
    val parentTitle: String? = null
) {
    val displayTitle: String get() = parentTitle?.let { "$it · ${task.title}" } ?: task.title
}

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
                .filter {
                    it.date != it.occurrenceDate &&
                        it.date >= CalendarDateUtils.addDays(rangeStart, -1) && it.date <= rangeEnd
                }
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

    // Планировщик проверяет каждую минуту, а не только четверть часа.
    private const val SEARCH_STEP_MINUTES = 1


    /** Urgency groups dominate manual priority: overdue > due soon > ordinary. */
    fun priority(task: TaskEntity, today: String = CalendarDateUtils.today()): Int =
        TaskRules.urgency(task, today) * 10 + task.priorityLevel.coerceIn(0, 2)

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
        placements: List<CalendarTaskPlacementEntity> = emptyList(),
        now: java.util.Calendar = java.util.Calendar.getInstance(),
        today: String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(now.time),
        earliestStartMinute: Int = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE) + 1
    ): List<ScheduledTask> {
        if (rangeStart > rangeEnd) return emptyList()

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
                (date == today && start < earliestStartMinute) ||
                end > DAY_END_MINUTE ||
                conflictsWithEvent(date, start, end) ||
                result.any { it.date == date && start < it.startMinute + it.durationMinutes && end > it.startMinute }
            ) {
                return false
            }

            return true
        }

        val pendingTasks = tasks
            .filterNot { it.completed || it.archived || it.frozen }
            .sortedWith(TaskRules.comparator(today))

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
            val duration = (savedPlacement?.durationMinutes ?: task.estimatedDurationMinutes)
                .coerceIn(15, 24 * 60)
            val excludedDate = savedPlacement?.takeIf { it.hidden }?.date

            // Иначе ищем ранний доступный слот, кроме скрытой для задачи даты.
            var placed = false
            for (date in dates) {
                if (date == excludedDate || (task.startDate.isNotBlank() && date < task.startDate)) continue

                var minute = if (date == today) maxOf(DAY_START_MINUTE, earliestStartMinute) else DAY_START_MINUTE
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
