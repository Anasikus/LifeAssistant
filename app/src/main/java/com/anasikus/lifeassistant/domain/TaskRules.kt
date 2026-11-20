package com.anasikus.lifeassistant.domain

import com.anasikus.lifeassistant.settings.AppText
import com.anasikus.lifeassistant.data.TaskEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TaskRules {

    fun today(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .format(Date())
    }

    fun formatDate(date: String): String {
        return runCatching {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .parse(date)

            if (parsed != null) {
                SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                    .format(parsed)
            } else {
                date
            }
        }.getOrDefault(date)
    }

    fun parseCalendar(date: String): Calendar {
        val calendar = Calendar.getInstance()

        runCatching {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .parse(date)

            if (parsed != null) {
                calendar.time = parsed
            }
        }

        return calendar
    }

    fun isOverdue(task: TaskEntity, today: String = TaskRules.today()): Boolean {
        return !task.completed &&
                task.deadline.isNotBlank() &&
                task.deadline < today
    }

    fun isDueSoon(task: TaskEntity, today: String = TaskRules.today()): Boolean =
        !task.completed && task.deadline.isNotBlank() &&
            task.deadline >= today && task.deadline <= CalendarDateUtils.addDays(today, 3)

    fun priorityLabel(level: Int): String = when (level.coerceIn(0, 2)) {
        0 -> AppText.get("Низкий")
        2 -> AppText.get("Высокий")
        else -> AppText.get("Обычный")
    }

    fun durationLabel(minutes: Int): String = buildList {
        if (minutes >= 60) add(AppText.get("{arg0} ч", minutes / 60))
        if (minutes % 60 != 0 || minutes < 60) add(AppText.get("{arg0} мин", minutes % 60))
    }.joinToString(" ")

    /** Deadline groups always outrank a manually selected priority. */
    fun urgency(task: TaskEntity, date: String = today()): Int = when {
        task.completed -> 0
        isOverdue(task, date) -> 3
        isDueSoon(task, date) -> 2
        else -> 1
    }

    fun comparator(date: String = today()): Comparator<TaskEntity> =
        compareByDescending<TaskEntity> { urgency(it, date) }
            .thenByDescending { it.priorityLevel.coerceIn(0, 2) }
            .thenBy { it.deadline.ifBlank { "9999-12-31" } }
            .thenBy { it.id }

    fun status(task: TaskEntity): String {
        if (task.frozen && !task.completed) return AppText.get("Заморожена")
        if (!task.completed && !task.isProject && TaskSessionPlanner.remaining(task) == 0) return AppText.get("Время исчерпано — увеличь оценку или заверши задачу")
        if (task.completed) {
            val completedAt = task.completedAt

            return if (
                completedAt != null &&
                task.deadline.isNotBlank() &&
                completedAt > task.deadline
            ) {
                AppText.get("Выполнен с опозданием")
            } else {
                AppText.get("Выполнен")
            }
        }

        return if (isOverdue(task)) {
            AppText.get("Просрочен")
        } else {
            AppText.get("В ожидании")
        }
    }
}
