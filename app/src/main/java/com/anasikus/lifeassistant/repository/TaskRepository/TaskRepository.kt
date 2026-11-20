
package com.anasikus.lifeassistant.repository

import com.anasikus.lifeassistant.data.TaskDao
import com.anasikus.lifeassistant.data.TaskEntity
import kotlinx.coroutines.flow.Flow

class TaskRepository(
    private val taskDao: TaskDao
) {
    suspend fun setFrozen(id: Int, frozen: Boolean, today: String) = taskDao.freeze(id, frozen, today)

    val allTasks: Flow<List<TaskEntity>> =
        taskDao.getAllTasks()

    suspend fun archiveCompletedBefore(date: String) = taskDao.archiveCompletedBefore(date)

    suspend fun setArchived(task: TaskEntity, archived: Boolean, today: String) =
        taskDao.archive(task.id, archived, today)

    suspend fun addTask(task: TaskEntity) {
        taskDao.insertTask(task)
    }

    suspend fun updateTask(task: TaskEntity) {
        taskDao.editTask(task)
    }

    suspend fun deleteTask(task: TaskEntity) {
        taskDao.removeTask(task)
    }
}
