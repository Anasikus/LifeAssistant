
package com.anasikus.lifeassistant.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anasikus.lifeassistant.data.TaskEntity
import com.anasikus.lifeassistant.domain.TaskRules
import com.anasikus.lifeassistant.repository.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(
    private val repository: TaskRepository
) : ViewModel() {

    val tasks: StateFlow<List<TaskEntity>> =
        repository.allTasks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun initializeSampleTasks() {
        viewModelScope.launch {
            if (repository.allTasks.first().isEmpty()) {
                val today = TaskRules.today()

                repository.addTask(
                    TaskEntity(
                        title = "Изучить Kotlin",
                        startDate = today,
                        deadline = today
                    )
                )

                repository.addTask(
                    TaskEntity(
                        title = "Составить план на день",
                        startDate = today,
                        deadline = today
                    )
                )

                repository.addTask(
                    TaskEntity(
                        title = "Отдохнуть после работы",
                        startDate = today,
                        deadline = today
                    )
                )
            }
        }
    }

    fun addTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.addTask(task)
        }
    }

    fun updateTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.updateTask(task)
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    fun setCompleted(task: TaskEntity, completed: Boolean) {
        updateTask(
            task.copy(
                completed = completed,
                completedAt = if (completed) {
                    TaskRules.today()
                } else {
                    null
                }
            )
        )
    }
}

class TaskViewModelFactory(
    private val repository: TaskRepository
) : androidx.lifecycle.ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TaskViewModel(repository) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}
