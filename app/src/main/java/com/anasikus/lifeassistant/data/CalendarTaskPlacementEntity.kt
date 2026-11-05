
package com.anasikus.lifeassistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Предпочтительное положение задачи в календаре.
 *
 * hidden = true означает, что пользователь убрал задачу
 * с выбранного дня, но не удалил её из приложения.
 */
@Entity(tableName = "calendar_task_placements")
data class CalendarTaskPlacementEntity(
    @PrimaryKey
    val taskId: Int,
    val date: String,
    val startMinute: Int,
    val durationMinutes: Int = 60,
    val hidden: Boolean = false
)
