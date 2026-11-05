
package com.anasikus.lifeassistant.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anasikus.lifeassistant.data.CalendarEventEntity
import com.anasikus.lifeassistant.data.CalendarEventOverrideEntity
import com.anasikus.lifeassistant.data.CalendarTaskPlacementEntity
import com.anasikus.lifeassistant.repository.CalendarRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CalendarViewModel(
    private val repository: CalendarRepository
) : ViewModel() {

    val events: StateFlow<List<CalendarEventEntity>> =
        repository.allEvents.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val placements: StateFlow<List<CalendarTaskPlacementEntity>> =
        repository.allPlacements.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val eventOverrides: StateFlow<List<CalendarEventOverrideEntity>> =
        repository.allEventOverrides.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    fun addEvent(event: CalendarEventEntity) {
        viewModelScope.launch {
            repository.addEvent(event)
        }
    }

    fun updateEvent(event: CalendarEventEntity) {
        viewModelScope.launch {
            repository.updateEvent(event)
        }
    }

    fun deleteEvent(event: CalendarEventEntity) {
        viewModelScope.launch {
            repository.deleteEvent(event)
        }
    }

    fun savePlacement(placement: CalendarTaskPlacementEntity) {
        viewModelScope.launch {
            repository.savePlacement(placement)
        }
    }

    fun deletePlacement(taskId: Int) {
        viewModelScope.launch {
            repository.deletePlacement(taskId)
        }
    }

    fun saveEditedOccurrence(
        event: CalendarEventEntity,
        occurrence: CalendarEventOverrideEntity
    ) {
        viewModelScope.launch {
            repository.saveEditedOccurrence(event, occurrence)
        }
    }

    fun saveEventOverride(override: CalendarEventOverrideEntity) {
        viewModelScope.launch {
            repository.saveEventOverride(override)
        }
    }
}

class CalendarViewModelFactory(
    private val repository: CalendarRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CalendarViewModel(repository) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}
