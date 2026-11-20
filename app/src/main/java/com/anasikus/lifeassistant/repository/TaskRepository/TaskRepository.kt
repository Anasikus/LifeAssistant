
package com.anasikus.lifeassistant.repository

import com.anasikus.lifeassistant.data.TaskDao
import com.anasikus.lifeassistant.data.TaskEntity
import kotlinx.coroutines.flow.Flow

class TaskRepository(
    private val taskDao: TaskDao
) {
    suspend fun setFrozen(id: Int, frozen: Boolean, today: String) = taskDao.freeze(id, frozen, today)

    val groups = taskDao.groups()
    val sessions = taskDao.sessions()
    val planningChanges = taskDao.planningChanges()
    suspend fun reconcile(today: String, minute: Int) = taskDao.reconcile(today, minute)
    suspend fun saveGroup(value: com.anasikus.lifeassistant.data.TaskGroupEntity) = taskDao.saveGroup(value)
    suspend fun deleteGroup(id: Int) = taskDao.deleteGroup(id)
    suspend fun moveSession(id: Long, date: String, start: Int, duration: Int) = taskDao.moveSession(id, date, start, duration)
    suspend fun skipSession(id: Long) = taskDao.skipSession(id)
    suspend fun split(id: Int, remaining: Int, drafts: List<com.anasikus.lifeassistant.domain.TaskSplitDraft>) = taskDao.split(id, remaining, drafts)

    val allTasks: Flow<List<TaskEntity>> =
        taskDao.getAllTasks()

    suspend fun archiveCompletedBefore(date: String) = taskDao.archiveCompletedBefore(date)

    suspend fun setArchived(task: TaskEntity, archived: Boolean, today: String) =
        taskDao.archive(task.id, archived, today)

    suspend fun addTask(task: TaskEntity) {
        taskDao.insertTask(task)
    }

    suspend fun updateTask(task: TaskEntity, adjustSpent: Boolean = false) {
        taskDao.editTask(task, adjustSpent)
    }

    suspend fun deleteTask(task: TaskEntity) {
        taskDao.removeTask(task)
    }
}
