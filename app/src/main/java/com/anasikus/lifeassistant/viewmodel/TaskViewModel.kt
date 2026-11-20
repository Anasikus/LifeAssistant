package com.anasikus.lifeassistant.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anasikus.lifeassistant.data.TaskEntity
import com.anasikus.lifeassistant.domain.TaskRules
import com.anasikus.lifeassistant.repository.TaskRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TaskViewModel(private val repository: TaskRepository) : ViewModel() {
    val error = MutableStateFlow<String?>(null)
    val currentDate = flow {
        while (true) {
            emit(TaskRules.today())
            delay(30_000L)
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskRules.today())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allTasks = currentDate.flatMapLatest { date ->
        flow {
            repository.archiveCompletedBefore(date)
            emitAll(repository.allTasks)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tasks = allTasks.map { rows -> rows.filterNot { it.archived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun change(action: suspend () -> Unit, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            error.value = null
            try {
                action()
                onSaved()
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                error.value = "Не удалось сохранить изменение. Повтори действие."
            }
        }
    }

    private fun validate(task: TaskEntity) {
        require(task.title.isNotBlank())
        require(task.estimatedDurationMinutes in 15..1440)
        require(task.priorityLevel in 0..2)
        require(task.deadline.isBlank() || task.startDate.isBlank() || task.deadline >= task.startDate)
    }

    fun addTask(task: TaskEntity, onSaved: () -> Unit = {}) =
        change({ validate(task); repository.addTask(task) }, onSaved)

    fun updateTask(task: TaskEntity, onSaved: () -> Unit = {}) =
        change({ validate(task); repository.updateTask(task) }, onSaved)

    fun deleteTask(task: TaskEntity, onSaved: () -> Unit = {}) =
        change({ repository.deleteTask(task) }, onSaved)

    fun setArchived(task: TaskEntity, archived: Boolean, onSaved: () -> Unit = {}) =
        change({ repository.setArchived(task, archived, TaskRules.today()) }, onSaved)

    fun setFrozen(task: TaskEntity, frozen: Boolean, onSaved: () -> Unit = {}) =
        change({ repository.setFrozen(task.id, frozen, TaskRules.today()) }, onSaved)

    fun setCompleted(task: TaskEntity, completed: Boolean) {
        updateTask(task.copy(completed = completed, completedAt = if (completed) TaskRules.today() else null,
            autoArchiveSuppressed = false))
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
