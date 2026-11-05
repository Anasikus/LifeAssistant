
package com.anasikus.lifeassistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val title: String,

    val description: String = "",

    // Даты храним в формате yyyy-MM-dd
    val startDate: String = "",

    val deadline: String = "",

    val completed: Boolean = false,

    // Дата выполнения или null, если задача ещё не выполнена
    val completedAt: String? = null
)
