package com.anasikus.lifeassistant.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String = "",
    val startDate: String = "",
    val deadline: String = "",
    val completed: Boolean = false,
    val completedAt: String? = null,
    @ColumnInfo(defaultValue = "60") val estimatedDurationMinutes: Int = 60,
    @ColumnInfo(defaultValue = "1") val priorityLevel: Int = 1,
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    @ColumnInfo(defaultValue = "0") val autoArchiveSuppressed: Boolean = false,
    @ColumnInfo(defaultValue = "0") val frozen: Boolean = false
)
