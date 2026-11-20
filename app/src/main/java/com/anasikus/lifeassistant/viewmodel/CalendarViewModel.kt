
package com.anasikus.lifeassistant.viewmodel

import com.anasikus.lifeassistant.settings.AppText
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    val financeError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private fun financeChange(onSaved: () -> Unit = {}, action: suspend () -> Unit) {
        viewModelScope.launch {
            financeError.value = null
            try { action(); onSaved() }
            catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                financeError.value = AppText.get("Не удалось сохранить изменение. Данные формы оставлены для повторной попытки.")
            }
        }
    }
    val incomeCategories = repository.planning.incomeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveIncomeCategory(category: com.anasikus.lifeassistant.data.IncomeCategoryEntity, onSaved: () -> Unit = {}) =
        financeChange(onSaved) { repository.planning.saveIncomeCategory(category) }
    fun deleteIncomeCategory(id: Int) = financeChange { repository.planning.deleteIncomeCategory(id) }
    fun setIncomeCategory(id: Int, categoryId: Int?) = financeChange { repository.planning.setIncomeCategory(id, categoryId) }

    fun saveExpenseCategory(category: ExpenseCategoryEntity, onSaved: () -> Unit = {}) =
        financeChange(onSaved) { repository.planning.saveExpenseCategory(category) }
    fun deleteExpenseCategory(id: Int) = financeChange { repository.planning.deleteExpenseCategory(id) }
    fun savePaymentPlan(plan: PaymentPlanEntity, onSaved: () -> Unit = {}) =
        financeChange(onSaved) { repository.planning.savePaymentPlan(plan) }
    fun deletePaymentPlan(id: Int) = financeChange { repository.planning.deletePaymentPlan(id) }

    val inboxMessages = repository.inbox.messages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun inboxForStatus(status: String) = repository.inbox.messagesWithStatus(status)

    val importRules = repository.inbox.rules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val inboxError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    fun confirmInbox(
        id: Long, title: String, body: String, income: Boolean, amount: Long, date: String,
        categoryId: Int?, sourceKey: String?, sourceName: String?, learn: Boolean,
        textFilter: String? = null, transfer: Boolean = false, onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.inbox.confirm(id, title, body, income, amount, date, categoryId, sourceKey, sourceName, learn, textFilter = textFilter, transfer = transfer)
                inboxError.value = null
                onSaved()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                inboxError.value = AppText.get("Не удалось сохранить операцию. Повтори сохранение.")
            }
        }
    }

    fun keepInbox(id: Long, title: String, body: String, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            inboxError.value = null
            try {
                repository.inbox.keepMessage(id, title, body)
                onSaved()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                inboxError.value = AppText.get("Не удалось сохранить сообщение. Повтори сохранение.")
            }
        }
    }

    fun ignoreInbox(id: Long, learn: Boolean, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            inboxError.value = null
            try {
                withContext(Dispatchers.IO) { repository.inbox.ignore(id, learn) }
                onSaved()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                inboxError.value = AppText.get("Не удалось удалить ненужные сообщения. Повтори действие.")
            }
        }
    }

    fun deleteInbox(id: Long) {
        viewModelScope.launch { repository.inbox.deleteMessage(id) }
    }

    fun configureFinanceSender(source: String, sender: String, bank: String, financial: Boolean, onSaved: () -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.inbox.configureSender(source, sender, bank, financial) }
                inboxError.value = null
                onSaved()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                inboxError.value = AppText.get("Не удалось сохранить настройки отправителя.")
            }
        }
    }

    fun forgetImportRule(key: String) {
        viewModelScope.launch { repository.inbox.forgetRule(key) }
    }

    fun addFinanceTransaction(value: FinanceTransactionEntity, onSaved: () -> Unit = {}) =
        financeChange(onSaved) { repository.planning.addFinanceTransaction(value) }


    fun updateFinanceTransaction(value: FinanceTransactionEntity, onSaved: () -> Unit = {}) =
        financeChange(onSaved) { repository.planning.updateFinanceTransaction(value) }

    fun setExpenseCategory(id: Int, categoryId: Int?) {
        viewModelScope.launch { repository.inbox.setFinancialCategory(id, categoryId) }
    }

    fun deleteFinanceTransaction(id: Int) {
        viewModelScope.launch { repository.inbox.deleteFinancialTransaction(id) }
    }

    fun saveCategory(value: com.anasikus.lifeassistant.data.EventCategoryEntity, onSaved: () -> Unit = {}) =
        financeChange(onSaved) { repository.planning.saveCategory(value) }
    fun deleteCategory(id: Int) { viewModelScope.launch { repository.planning.deleteCategory(id) } }
    fun savePattern(value: com.anasikus.lifeassistant.data.ShiftPatternEntity, onSaved: () -> Unit = {}) =
        financeChange(onSaved) { repository.planning.savePattern(value) }
    fun saveShift(value: com.anasikus.lifeassistant.data.ShiftDayEntity, onSaved: () -> Unit = {}) =
        financeChange(onSaved) { repository.planning.saveShift(value.copy(predicted = false, confidence = 100)) }
    fun deleteShift(date: String) { viewModelScope.launch { repository.planning.deleteShift(date) } }
    fun applyForecast(values: List<com.anasikus.lifeassistant.data.ShiftDayEntity>) {
        viewModelScope.launch { repository.planning.saveForecast(values) }
    }
    fun saveSpecialDate(value: com.anasikus.lifeassistant.data.SpecialDateEntity, onSaved: () -> Unit = {}) =
        financeChange(onSaved) { repository.planning.saveSpecialDate(value) }
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
                planningMessage.value = AppText.get("Календарь обработан: {arg0} дат. Совпадающие записи пропущены.", values.size)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                planningMessage.value = error.message ?: AppText.get("Не удалось загрузить календарь.")
            } finally { importing.value = false }
        }
    }

    fun addEvent(event: CalendarEventEntity, onSaved: () -> Unit = {}) =
        financeChange(onSaved) { repository.addEvent(event) }

    fun updateEvent(event: CalendarEventEntity, onSaved: () -> Unit = {}) =
        financeChange(onSaved) { repository.updateEvent(event) }

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
        occurrence: CalendarEventOverrideEntity?,
        onSaved: () -> Unit = {}
    ) = financeChange(onSaved) { repository.saveEditedOccurrence(event, occurrence) }

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
