package com.anasikus.lifeassistant.viewmodel

import com.anasikus.lifeassistant.settings.AppText
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

    private val clock = flow {
        while (true) { emit(System.currentTimeMillis() / 60_000L); delay(30_000L) }
    }
    val allTasks = combine(clock, repository.allTasks, repository.planningChanges) { _, _, _ ->
        val now = java.util.Calendar.getInstance()
        val date = TaskRules.today()
        try {
            repository.reconcile(date, now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE))
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            error.value = AppText.get("Не удалось сохранить изменение. Повтори действие.")
        }
        repository.allTasks.first()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val groups = repository.groups.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sessions = repository.sessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun saveGroup(group: com.anasikus.lifeassistant.data.TaskGroupEntity, onSaved: () -> Unit = {}) = change({ repository.saveGroup(group) }, onSaved)
    fun deleteGroup(id: Int) = change({ repository.deleteGroup(id) })
    fun moveSession(id: Long, date: String, start: Int, duration: Int) = change({ repository.moveSession(id, date, start, duration) })
    fun skipSession(id: Long) = change({ repository.skipSession(id) })
    fun split(task: TaskEntity, remaining: Int, drafts: List<com.anasikus.lifeassistant.domain.TaskSplitDraft>, onSaved: () -> Unit) =
        change({ repository.split(task.id, remaining, drafts) }, onSaved)
    private suspend fun refreshPlan() {
        val now = java.util.Calendar.getInstance()
        repository.reconcile(TaskRules.today(), now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE))
    }

    val tasks = allTasks.map { rows -> rows.filterNot { it.archived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun change(action: suspend () -> Unit, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            error.value = null
            try {
                refreshPlan()
                action()
                refreshPlan()
                onSaved()
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                error.value = AppText.get("Не удалось сохранить изменение. Повтори действие.")
            }
        }
    }

    private fun validate(task: TaskEntity) {
        require(task.title.isNotBlank())
        require(task.estimatedDurationMinutes in 15..com.anasikus.lifeassistant.domain.TaskSessionPlanner.MAX_BUDGET)
        require(task.spentMinutes in 0..task.estimatedDurationMinutes)
        require(task.priorityLevel in 0..2)
        require(task.deadline.isBlank() || task.startDate.isBlank() || task.deadline >= task.startDate)
    }

    fun addTask(task: TaskEntity, onSaved: () -> Unit = {}) =
        change({ validate(task); repository.addTask(task) }, onSaved)

    fun updateTask(task: TaskEntity, onSaved: () -> Unit = {}, adjustSpent: Boolean = false) =
        change({ validate(task); repository.updateTask(task, adjustSpent) }, onSaved)

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
