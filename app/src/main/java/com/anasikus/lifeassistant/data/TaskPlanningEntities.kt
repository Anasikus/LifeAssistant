package com.anasikus.lifeassistant.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "task_groups")
data class TaskGroupEntity(@PrimaryKey(autoGenerate = true) val id: Int = 0, val name: String)

@Entity(tableName = "task_sessions", indices = [Index(value = ["taskId"])])
data class TaskSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Int,
    val date: String,
    val startMinute: Int,
    val durationMinutes: Int,
    val automatic: Boolean = true,
    val accounted: Boolean = false
)
