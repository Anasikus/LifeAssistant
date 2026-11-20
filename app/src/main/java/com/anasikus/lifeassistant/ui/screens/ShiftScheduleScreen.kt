package com.anasikus.lifeassistant.ui.screens

import com.anasikus.lifeassistant.settings.AppText
import com.anasikus.lifeassistant.ui.assistantColorScheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anasikus.lifeassistant.data.CalendarEventEntity
import com.anasikus.lifeassistant.domain.CalendarDateUtils
import com.anasikus.lifeassistant.domain.CalendarDisplay
import com.anasikus.lifeassistant.domain.CalendarEventRules
import com.anasikus.lifeassistant.domain.ShiftRules
import com.anasikus.lifeassistant.ui.AppColors
import com.anasikus.lifeassistant.ui.components.CalendarEventDialog
import com.anasikus.lifeassistant.ui.components.PlanningDateField
import com.anasikus.lifeassistant.ui.components.*
import com.anasikus.lifeassistant.ui.components.shiftStatus
import com.anasikus.lifeassistant.viewmodel.CalendarViewModel

@Composable
internal fun ShiftScheduleScreen(
    viewModel: CalendarViewModel,
    initialDate: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val pattern by viewModel.shiftPattern.collectAsState()
    val days by viewModel.shiftDays.collectAsState()
    val events by viewModel.events.collectAsState()
    val overrides by viewModel.eventOverrides.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val workCategories = categories.filter { it.kind == "WORK" || it.kind == "SIDE_JOB" }
    val work = events.filter { event ->
        event.hourlyRateCents != null || event.payAmountCents != null || event.oneTimePaymentCents != null ||
            workCategories.any { it.id == event.categoryId }
    }
    var date by remember { mutableStateOf(initialDate) }
    var showSettings by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CalendarEventEntity?>(null) }
    val start = CalendarDateUtils.startOfMonth(date)
    val end = CalendarDateUtils.endOfMonth(date)
    val monthDays = remember(start, end, pattern, days) { ShiftRules.days(start, end, pattern, days) }
    val monthWork = remember(work, overrides, start, end) {
        CalendarEventRules.expand(work, start, end, overrides)
            .groupBy { it.date }
    }

    MaterialTheme(colorScheme = assistantColorScheme(primary = AppColors.Accent, surface = AppColors.Card)) {
        Column(
            modifier.background(AppColors.Background).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(onClick = onBack) { Text(AppText.get("‹ К календарю")) }
            Text(AppText.get("График смен"), style = MaterialTheme.typography.headlineSmall, color = AppColors.MainText)
            PlanningDateField(AppText.get("Месяц и выбранный день"), date) { date = it }
            Button(onClick = { showSettings = true }) { Text(AppText.get("Шаблон, отметки дня и прогноз")) }
            OutlinedButton(
                onClick = { editing = null; showEditor = true },
                enabled = workCategories.isNotEmpty()
            ) { Text(AppText.get("Добавить работу или подработку")) }
            if (workCategories.isEmpty()) {
                Text(AppText.get("Создай категорию работы или подработки в настройках категорий календаря."), color = AppColors.SecondaryText)
            }

            if (work.isNotEmpty()) {
                Text(AppText.get("Работы и подработки"), color = AppColors.MainText)
                work.forEach { event ->
                    TextButton(onClick = { editing = event; showEditor = true }) {
                        Text(event.title)
                    }
                }
            }
            Text(CalendarDateUtils.formatMonth(date), color = AppColors.MainText)
            val templateByDate = monthDays.associateBy { it.date }
            val listedDates = (templateByDate.keys + monthWork.keys).sorted()
            if (listedDates.isEmpty()) Text(AppText.get("На этот месяц смен пока нет"), color = AppColors.SecondaryText)
            listedDates.forEach { day ->
                Card(colors = CardDefaults.cardColors(containerColor = AppColors.Card)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                        Text(CalendarDateUtils.formatDate(day), color = AppColors.MainText)
                        templateByDate[day]?.let { shift ->
                            val status = shiftStatus(shift.status) + if (shift.predicted) AppText.get(" · предположение") else ""
                            Text(status, color = AppColors.SecondaryText)
                            if (shift.status == "WORK") {
                                Text(CalendarDisplay.interval(shift.startMinute, shift.durationMinutes), color = AppColors.SecondaryText)
                            }
                        }
                        monthWork[day].orEmpty().forEach { occurrence ->
                            val interval = CalendarDisplay.interval(occurrence.instanceStartMinute, occurrence.instanceDurationMinutes)
                            val continuation = if (occurrence.date != occurrence.instanceDate) AppText.get(" · продолжение") else ""
                            Text("${occurrence.event.title} · $interval$continuation", color = AppColors.SecondaryText)
                        }
                    }
                }
            }
        }
        if (showSettings) {
            val drafts = remember { FormDraftRegistry() }
            val protect = rememberGuardedAction(drafts)
            val error by viewModel.financeError.collectAsState()
            CompositionLocalProvider(LocalFormDraftRegistry provides drafts) {
            AlertDialog(
                onDismissRequest = { protect { showSettings = false } },
                title = { Text(AppText.get("Настройки графика")) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        ShiftSettings(date, pattern, days, viewModel)
                        error?.let { Text(it, color = AppColors.Red) }
                    }
                },
                confirmButton = { TextButton(onClick = { protect { showSettings = false } }) { Text(AppText.get("Закрыть")) } }
            )
            }
        }
        if (showEditor) {
            val eventError by viewModel.financeError.collectAsState()
            val current = editing
            CalendarEventDialog(
                event = current, error = eventError,
                initialDate = current?.startDate ?: date,
                initialStartMinute = current?.startMinute ?: 480,
                initialDurationMinutes = current?.durationMinutes ?: 720,
                categories = workCategories,
                initialCategoryId = current?.categoryId?.takeIf { id -> workCategories.any { it.id == id } }
                    ?: workCategories.firstOrNull()?.id,
                requireCategory = true,
                editSeries = true,
                defaultHourlyRateCents = pattern?.hourlyRateCents ?: 0L,
                onDismiss = { showEditor = false },
                onSave = { title, description, startDate, startMinute, duration, repeatMask, categoryId, amount, payMode ->
                    val isWork = workCategories.find { it.id == categoryId }?.kind == "WORK"
                    val event = CalendarEventEntity(
                        id = current?.id ?: 0,
                        title = title,
                        description = description,
                        startDate = startDate,
                        startMinute = startMinute,
                        durationMinutes = duration,
                        repeatDaysMask = repeatMask,
                        categoryId = categoryId,
                        oneTimePaymentCents = if (isWork) null else amount,
                        payMode = if (isWork) payMode.name else null,
                        payAmountCents = if (isWork) amount else null
                    )
                    if (current == null) viewModel.addEvent(event) { showEditor = false }
                    else viewModel.updateEvent(event) { showEditor = false }
                },
                onDeleteAll = current?.let { event ->
                    { viewModel.deleteEvent(event); showEditor = false }
                }
            )
        }
    }
}
