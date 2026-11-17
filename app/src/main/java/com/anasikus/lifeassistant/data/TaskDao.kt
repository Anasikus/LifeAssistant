package com.anasikus.lifeassistant.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY id ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun task(id: Int): TaskEntity?

    @Insert
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("UPDATE tasks SET archived = 1 WHERE completed = 1 AND completedAt IS NOT NULL AND completedAt < :today AND archived = 0 AND autoArchiveSuppressed = 0")
    suspend fun archiveCompletedBefore(today: String)

    @Query("UPDATE tasks SET archived = :archived, autoArchiveSuppressed = :restored WHERE id = :id")
    suspend fun setArchiveState(id: Int, archived: Boolean, restored: Boolean)

    @Query("DELETE FROM calendar_task_placements WHERE taskId = :id")
    suspend fun deletePlacement(id: Int)

    @Query("DELETE FROM calendar_task_placements WHERE taskId = :id AND date < :today")
    suspend fun deletePastPlacement(id: Int, today: String)

    @Query("UPDATE calendar_task_placements SET durationMinutes = :duration, startMinute = min(startMinute, 1440 - :duration) WHERE taskId = :id")
    suspend fun updatePlacementDuration(id: Int, duration: Int)

    @Transaction
    suspend fun editTask(value: TaskEntity) {
        val old = task(value.id) ?: return
        val completionChanged = old.completed != value.completed || old.completedAt != value.completedAt
        updateTask(value.copy(archived = old.archived,
            autoArchiveSuppressed = if (completionChanged) false else old.autoArchiveSuppressed))
        if (old.estimatedDurationMinutes != value.estimatedDurationMinutes) {
            updatePlacementDuration(value.id, value.estimatedDurationMinutes)
        }
    }

    @Transaction
    suspend fun archive(id: Int, archived: Boolean, today: String) {
        setArchiveState(id, archived, restored = !archived)
        if (!archived) deletePastPlacement(id, today)
    }

    @Transaction
    suspend fun removeTask(value: TaskEntity) {
        deletePlacement(value.id)
        deleteTask(value)
    }
}
