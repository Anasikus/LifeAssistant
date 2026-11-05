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
    val durationMinutes: Int = event.durationMinutes
)

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
                val firstDate = maxOf(event.startDate, rangeStart)
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
                .filter { it.date >= rangeStart && it.date <= rangeEnd }
                .forEach { candidateSourceDates.add(it.occurrenceDate) }

            candidateSourceDates.forEach sourceDateLoop@ { sourceDate ->
                val override = eventOverrides[sourceDate]
                if (override?.deleted == true) return@sourceDateLoop

                val actualDate = override?.date ?: sourceDate
                if (actualDate < rangeStart || actualDate > rangeEnd) {
                    return@sourceDateLoop
                }

                result.add(
                    CalendarEventOccurrence(
                        event = event,
                        date = actualDate,
                        occurrenceDate = sourceDate,
                        startMinute = override?.startMinute ?: event.startMinute,
                        durationMinutes = override?.durationMinutes ?: event.durationMinutes
                    )
                )
            }
        }

        return result
            .distinctBy { "${it.event.id}_${it.occurrenceDate}" }
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
     * Сохранённое размещение считается предпочтительным. Если оно скрыто для
     * конкретного дня, задача не появится в этот день и будет искать другое окно.
     *
     * Задачи могут пересекаться без фиксированного лимита.
     * Фиксированные мероприятия не перекрываются задачами.
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
        if (effectiveStart > rangeEnd) return emptyList()

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
                conflictsWithEvent(date, start, end)
            ) {
                return false
            }

            // Задачи могут накладываться друг на друга без фиксированного лимита.
            // Пересечения с мероприятиями по-прежнему запрещены.
            return true
        }

        val pendingTasks = tasks
            .filterNot { it.completed }
            .sortedWith(
                compareByDescending<TaskEntity> { priority(it, today) }
                    .thenBy { it.deadline.ifBlank { "9999-12-31" } }
                    .thenBy { it.id }
            )

        pendingTasks.forEach taskLoop@ { task ->
            val storedPlacement = placementsByTaskId[task.id]
            // Если дедлайн уже прошёл, а сохранённый слот остался в прошлом,
            // считаем его устаревшим и заново размещаем задачу в текущем расписании.
            val savedPlacement = storedPlacement?.takeUnless {
                task.deadline.isNotBlank() && task.deadline < today && it.date < today
            }
            val duration = (savedPlacement?.durationMinutes ?: DEFAULT_TASK_DURATION_MINUTES)
                .coerceIn(15, DAY_END_MINUTE - DAY_START_MINUTE)

            // Ручное размещение вне текущего периода остаётся вне экрана.
            if (
                savedPlacement != null &&
                !savedPlacement.hidden &&
                (savedPlacement.date < rangeStart || savedPlacement.date > rangeEnd)
            ) {
                return@taskLoop
            }

            val excludedDate = savedPlacement
                ?.takeIf { it.hidden }
                ?.date

            // Сначала сохраняем ручной слот, если он существует и допустим.
            if (
                savedPlacement != null &&
                !savedPlacement.hidden &&
                savedPlacement.date in dates &&
                canPlace(savedPlacement.date, savedPlacement.startMinute, duration)
            ) {
                result.add(
                    ScheduledTask(
                        task = task,
                        date = savedPlacement.date,
                        startMinute = savedPlacement.startMinute,
                        durationMinutes = duration,
                        priority = priority(task, today)
                    )
                )
                return@taskLoop
            }

            // Иначе ищем ранний доступный слот, кроме скрытой для задачи даты.
            var placed = false
            for (date in dates) {
                if (date == excludedDate) continue

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
