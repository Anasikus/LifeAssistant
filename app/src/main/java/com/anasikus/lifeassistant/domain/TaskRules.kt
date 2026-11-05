package com.anasikus.lifeassistant.domain

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

    fun isOverdue(task: TaskEntity): Boolean {
        return !task.completed &&
                task.deadline.isNotBlank() &&
                task.deadline < today()
    }

    fun isDueSoon(task: TaskEntity): Boolean {
        if (task.completed || task.deadline.isBlank()) {
            return false
        }

        val today = today()

        if (task.deadline < today) {
            return false
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        return runCatching {
            val start = dateFormat.parse(today) ?: return false
            val deadline = dateFormat.parse(task.deadline) ?: return false

            val daysRemaining =
                (deadline.time - start.time) / (24 * 60 * 60 * 1000)

            daysRemaining in 0..3
        }.getOrDefault(false)
    }

    fun status(task: TaskEntity): String {
        if (task.completed) {
            val completedAt = task.completedAt

            return if (
                completedAt != null &&
                task.deadline.isNotBlank() &&
                completedAt > task.deadline
            ) {
                "Выполнен с опозданием"
            } else {
                "Выполнен"
            }
        }

        return if (isOverdue(task)) {
            "Просрочен"
        } else {
            "В ожидании"
        }
    }
}