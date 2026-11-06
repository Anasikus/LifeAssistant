
package com.anasikus.lifeassistant.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anasikus.lifeassistant.data.ExpenseCategoryEntity
import com.anasikus.lifeassistant.data.PaymentPlanEntity
import com.anasikus.lifeassistant.data.FinanceTransactionEntity
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

    val categories = repository.planning.categories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val shiftPattern = repository.planning.pattern().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val shiftDays = repository.planning.shifts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val specialDates = repository.planning.specialDates().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val planningMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val importing = kotlinx.coroutines.flow.MutableStateFlow(false)

    val financeTransactions = repository.planning.financeTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val expenseCategories = repository.planning.expenseCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val paymentPlans = repository.planning.paymentPlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveExpenseCategory(category: ExpenseCategoryEntity) {
        viewModelScope.launch { repository.planning.saveExpenseCategory(category) }
    }

    fun deleteExpenseCategory(id: Int) {
        viewModelScope.launch { repository.planning.deleteExpenseCategory(id) }
    }

    fun savePaymentPlan(plan: PaymentPlanEntity) {
        viewModelScope.launch { repository.planning.savePaymentPlan(plan) }
    }

    fun deletePaymentPlan(id: Int) {
        viewModelScope.launch { repository.planning.deletePaymentPlan(id) }
    }

    fun addFinanceTransaction(value: FinanceTransactionEntity) {
        viewModelScope.launch { repository.planning.addFinanceTransaction(value) }
    }

    fun setExpenseCategory(id: Int, categoryId: Int?) {
        viewModelScope.launch { repository.planning.setExpenseCategory(id, categoryId) }
    }

    fun deleteFinanceTransaction(id: Int) {
        viewModelScope.launch { repository.planning.deleteFinanceTransaction(id) }
    }

    fun saveCategory(value: com.anasikus.lifeassistant.data.EventCategoryEntity) {
        viewModelScope.launch { repository.planning.saveCategory(value) }
    }
    fun deleteCategory(id: Int) { viewModelScope.launch { repository.planning.deleteCategory(id) } }
    fun savePattern(value: com.anasikus.lifeassistant.data.ShiftPatternEntity) {
        viewModelScope.launch { repository.planning.savePattern(value) }
    }
    fun saveShift(value: com.anasikus.lifeassistant.data.ShiftDayEntity) {
        viewModelScope.launch { repository.planning.saveShift(value.copy(predicted = false, confidence = 100)) }
    }
    fun deleteShift(date: String) { viewModelScope.launch { repository.planning.deleteShift(date) } }
    fun applyForecast(values: List<com.anasikus.lifeassistant.data.ShiftDayEntity>) {
        viewModelScope.launch { repository.planning.saveForecast(values) }
    }
    fun saveSpecialDate(value: com.anasikus.lifeassistant.data.SpecialDateEntity) {
        viewModelScope.launch { repository.planning.saveSpecialDate(value) }
    }
    fun deleteSpecialDate(id: Int) { viewModelScope.launch { repository.planning.deleteSpecialDate(id) } }
    fun importHolidays(url: String?) {
        if (importing.value) return
        viewModelScope.launch {
            importing.value = true
            planningMessage.value = null
            try {
                val values = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    if (url == null) com.anasikus.lifeassistant.repository.HolidayImporter.internationalSelection()
                    else com.anasikus.lifeassistant.repository.HolidayImporter.download(url)
                }
                repository.planning.importSpecialDates(values)
                planningMessage.value = "Календарь обработан: ${values.size} дат. Совпадающие записи пропущены."
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                planningMessage.value = error.message ?: "Не удалось загрузить календарь."
            } finally { importing.value = false }
        }
    }

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
        occurrence: CalendarEventOverrideEntity?
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
