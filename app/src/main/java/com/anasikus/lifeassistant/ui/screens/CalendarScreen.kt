package com.anasikus.lifeassistant.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anasikus.lifeassistant.data.CalendarEventEntity
import com.anasikus.lifeassistant.data.CalendarEventOverrideEntity
import com.anasikus.lifeassistant.data.CalendarTaskPlacementEntity
import com.anasikus.lifeassistant.data.TaskEntity
import com.anasikus.lifeassistant.data.ShiftDayEntity
import com.anasikus.lifeassistant.data.SpecialDateEntity
import com.anasikus.lifeassistant.data.EventCategoryEntity
import com.anasikus.lifeassistant.domain.PaymentPlanRules
import com.anasikus.lifeassistant.ui.components.PaymentPlansDialog
import com.anasikus.lifeassistant.domain.WorkPay
import com.anasikus.lifeassistant.domain.ShiftRules
import com.anasikus.lifeassistant.domain.CalendarDisplay
import com.anasikus.lifeassistant.ui.components.CalendarPlanningDialog
import com.anasikus.lifeassistant.ui.components.shiftStatus
import com.anasikus.lifeassistant.domain.CalendarDateUtils
import com.anasikus.lifeassistant.domain.CalendarEventOccurrence
import com.anasikus.lifeassistant.domain.CalendarEventRules
import com.anasikus.lifeassistant.domain.CalendarTaskScheduler
import com.anasikus.lifeassistant.domain.ScheduledTask
import com.anasikus.lifeassistant.ui.AppColors
import com.anasikus.lifeassistant.domain.TimelineRange
import com.anasikus.lifeassistant.ui.components.CalendarTimelineCard
import com.anasikus.lifeassistant.ui.components.TaskEditorDialog
import com.anasikus.lifeassistant.ui.components.TimePickerField
import com.anasikus.lifeassistant.ui.components.CalendarEventDialog
import com.anasikus.lifeassistant.viewmodel.CalendarViewModel
import com.anasikus.lifeassistant.viewmodel.TaskViewModel
import kotlin.math.roundToInt

private enum class CalendarViewMode(val label: String) {
    DAY("День"),
    WEEK("Неделя"),
    MONTH("Месяц"),
    YEAR("Год")
}

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    taskViewModel: TaskViewModel,
    modifier: Modifier = Modifier
) {
    val events by viewModel.events.collectAsState()
    val eventOverrides by viewModel.eventOverrides.collectAsState()
    val placements by viewModel.placements.collectAsState()
    val tasks by taskViewModel.tasks.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val shiftPattern by viewModel.shiftPattern.collectAsState()
    val shiftDays by viewModel.shiftDays.collectAsState()
    val specialDates by viewModel.specialDates.collectAsState()
    val paymentPlans by viewModel.paymentPlans.collectAsState()
    val financeTransactions by viewModel.financeTransactions.collectAsState()
    var paymentDialogDate by remember { mutableStateOf<String?>(null) }
    var showPlanningDialog by remember { mutableStateOf(false) }
    var showShifts by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showGridControls by remember { mutableStateOf(false) }
    var createTask by remember { mutableStateOf(false) }

    var anchorDate by remember { mutableStateOf(CalendarDateUtils.today()) }
    var mode by remember { mutableStateOf(CalendarViewMode.DAY) }
    var intervalMinutes by remember { mutableStateOf(30) }

    var showEventDialog by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<CalendarEventEntity?>(null) }
    var eventInitialDate by remember { mutableStateOf(CalendarDateUtils.today()) }
    var eventOccurrenceDate by remember { mutableStateOf(CalendarDateUtils.today()) }
    var eventInitialMinute by remember { mutableStateOf(9 * 60) }
    var eventInitialDuration by remember { mutableStateOf(60) }

    var taskForActions by remember { mutableStateOf<ScheduledTask?>(null) }
    var taskForTimeEdit by remember { mutableStateOf<ScheduledTask?>(null) }
    var taskForFullDelete by remember { mutableStateOf<ScheduledTask?>(null) }

    val (rangeStart, rangeEnd) = remember(anchorDate, mode) {
        when (mode) {
            CalendarViewMode.DAY -> anchorDate to anchorDate
            CalendarViewMode.WEEK -> {
                val start = CalendarDateUtils.startOfWeek(anchorDate)
                start to CalendarDateUtils.addDays(start, 6)
            }
            CalendarViewMode.MONTH ->
                CalendarDateUtils.startOfMonth(anchorDate) to
                        CalendarDateUtils.endOfMonth(anchorDate)
            CalendarViewMode.YEAR ->
                CalendarDateUtils.startOfYear(anchorDate) to
                        CalendarDateUtils.endOfYear(anchorDate)
        }
    }

    val dates = remember(rangeStart, rangeEnd) {
        CalendarDateUtils.datesBetween(rangeStart, rangeEnd)
    }

    val monthGrid = remember(rangeStart, mode) {
        if (mode == CalendarViewMode.MONTH) CalendarDateUtils.monthGridDates(rangeStart) else emptyList()
    }
    val displayStart = monthGrid.firstOrNull() ?: rangeStart
    val displayEnd = monthGrid.lastOrNull() ?: rangeEnd
    val occurrences = remember(events, eventOverrides, displayStart, displayEnd) {
        CalendarEventRules.expand(
            events = events,
            rangeStart = displayStart,
            rangeEnd = displayEnd,
            overrides = eventOverrides
        )
    }

    val effectiveShifts = remember(shiftPattern, shiftDays, displayStart, displayEnd) {
        ShiftRules.days(CalendarDateUtils.addDays(displayStart, -1), displayEnd, shiftPattern, shiftDays)
    }
    val shiftBlocks = remember(effectiveShifts, displayStart, displayEnd) {
        CalendarEventRules.expand(
            effectiveShifts.filter { it.status == "WORK" }.mapIndexed { index, day ->
                CalendarEventEntity(
                    id = -index - 1,
                    title = if (day.predicted) "Работа · предположение" else "Работа · смена",
                    startDate = day.date,
                    startMinute = day.startMinute,
                    durationMinutes = day.durationMinutes
                )
            },
            displayStart,
            displayEnd
        )
    }
    val paymentBlocks = remember(paymentPlans, financeTransactions, displayStart, displayEnd) {
        val paid = financeTransactions.mapNotNull { it.planKey }.toSet()
        paymentPlans.flatMap { PaymentPlanRules.between(it, displayStart, displayEnd) }
            .mapIndexed { index, payment ->
                val status = if (payment.key in paid) "Оплачено" else "Платёж"
                val event = CalendarEventEntity(
                    id = Int.MIN_VALUE + index,
                    title = "$status · ${payment.plan.title}",
                    description = ShiftRules.money(payment.plan.amountCents),
                    startDate = payment.date,
                    startMinute = 9 * 60,
                    durationMinutes = 30
                )
                CalendarEventOccurrence(event, payment.date, plannedPayment = payment)
            }
    }
    val displayEvents = remember(occurrences, shiftBlocks, paymentBlocks) {
        (occurrences + shiftBlocks + paymentBlocks).sortedWith(compareBy({ it.date }, { it.startMinute }))
    }
    val scheduledTasks = remember(tasks, occurrences, shiftBlocks, effectiveShifts, placements, rangeStart, rangeEnd) {
        val tentativeDates = effectiveShifts.filter { it.predicted }.map { it.date }.toSet()
        CalendarTaskScheduler.schedule(
            tasks = tasks,
            events = occurrences + shiftBlocks.filter { it.instanceDate !in tentativeDates },
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            placements = placements
        )
    }

    fun movePeriod(direction: Int) {
        anchorDate = when (mode) {
            CalendarViewMode.DAY -> CalendarDateUtils.addDays(anchorDate, direction)
            CalendarViewMode.WEEK -> CalendarDateUtils.addDays(anchorDate, direction * 7)
            CalendarViewMode.MONTH -> CalendarDateUtils.addMonths(anchorDate, direction)
            CalendarViewMode.YEAR -> CalendarDateUtils.addYears(anchorDate, direction)
        }
    }

    fun openNewEvent(date: String, minute: Int) {
        createTask = false
        editingEvent = null
        eventInitialDate = date
        eventOccurrenceDate = date
        eventInitialMinute = minute.coerceIn(0, 24 * 60 - 15)
        eventInitialDuration = 60
        showEventDialog = true
    }

    fun openExistingEvent(occurrence: CalendarEventOccurrence) {
        occurrence.plannedPayment?.let {
            paymentDialogDate = it.date
            return
        }
        if (occurrence.event.id < 0) {
            showShifts = true
            return
        }
        createTask = false
        editingEvent = occurrence.event
        eventInitialDate = occurrence.instanceDate
        eventOccurrenceDate = occurrence.occurrenceDate
        eventInitialMinute = occurrence.instanceStartMinute
        eventInitialDuration = occurrence.instanceDurationMinutes
        showEventDialog = true
    }

    fun saveTaskPlacement(
        scheduled: ScheduledTask,
        startMinute: Int = scheduled.startMinute,
        durationMinutes: Int = scheduled.durationMinutes,
        hidden: Boolean = false
    ) {
        val safeDuration = durationMinutes.coerceIn(15, 24 * 60)
        val safeStart = startMinute.coerceIn(0, 24 * 60 - safeDuration)
        viewModel.savePlacement(
            CalendarTaskPlacementEntity(
                taskId = scheduled.task.id,
                date = scheduled.date,
                startMinute = safeStart,
                durationMinutes = safeDuration,
                hidden = hidden
            )
        )
    }

    fun saveEventPosition(
        occurrence: CalendarEventOccurrence,
        startMinute: Int = occurrence.startMinute,
        durationMinutes: Int = occurrence.durationMinutes
    ) {
        val previous = eventOverrides.find {
            it.eventId == occurrence.event.id && it.occurrenceDate == occurrence.occurrenceDate
        }
        val changed = occurrence.withSegmentRange(TimelineRange(startMinute, durationMinutes))
        viewModel.saveEventOverride(
            CalendarEventOverrideEntity(
                eventId = occurrence.event.id,
                occurrenceDate = occurrence.occurrenceDate,
                date = changed.instanceDate,
                startMinute = changed.instanceStartMinute,
                durationMinutes = changed.instanceDurationMinutes,
                deleted = false,
                payMode = previous?.payMode,
                payAmountCents = previous?.payAmountCents
            )
        )
    }

    val periodLabel = when (mode) {
        CalendarViewMode.DAY -> CalendarDateUtils.formatLongDate(anchorDate)
        CalendarViewMode.WEEK ->
            "${CalendarDateUtils.formatShortDate(rangeStart)} — " +
                    "${CalendarDateUtils.formatShortDate(rangeEnd)} ${rangeEnd.take(4)}"
        CalendarViewMode.MONTH -> CalendarDateUtils.formatMonth(anchorDate)
        CalendarViewMode.YEAR -> rangeStart.take(4)
    }

    if (showShifts) {
        ShiftScheduleScreen(viewModel, anchorDate, onBack = { showShifts = false }, modifier = modifier)
        return
    }

    Surface(modifier = modifier.fillMaxSize(), color = AppColors.Background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Календарь", Modifier.weight(1f), color = AppColors.MainText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { anchorDate = CalendarDateUtils.today(); mode = CalendarViewMode.DAY }) {
                    Text("Сегодня", color = AppColors.Accent)
                }
                Box {
                    TextButton(onClick = { showSettingsMenu = true }) { Text("⋮", color = AppColors.Accent, fontSize = 22.sp) }
                    DropdownMenu(expanded = showSettingsMenu, onDismissRequest = { showSettingsMenu = false }) {
                        DropdownMenuItem(text = { Text("Категории и праздники") }, onClick = { showSettingsMenu = false; showPlanningDialog = true })
                        DropdownMenuItem(text = { Text("Регулярные платежи") }, onClick = { showSettingsMenu = false; paymentDialogDate = anchorDate })
                        DropdownMenuItem(text = { Text("Шаг сетки") }, onClick = { showSettingsMenu = false; mode = CalendarViewMode.DAY; showGridControls = !showGridControls })
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { movePeriod(-1) }) {
                    Text("‹", color = AppColors.Accent, fontSize = 28.sp)
                }
                Text(
                    text = periodLabel,
                    modifier = Modifier.weight(1f),
                    color = AppColors.MainText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = { movePeriod(1) }) {
                    Text("›", color = AppColors.Accent, fontSize = 28.sp)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CalendarViewMode.values().forEach { viewMode ->
                    TextButton(onClick = { mode = viewMode }) {
                        Text(
                            text = viewMode.label,
                            color = if (mode == viewMode) AppColors.Accent
                            else AppColors.SecondaryText,
                            fontWeight = if (mode == viewMode) FontWeight.Bold
                            else FontWeight.Normal
                        )
                    }
                }
            }

            if (mode == CalendarViewMode.DAY && showGridControls) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Шаг сетки", color = AppColors.SecondaryText, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { intervalMinutes = (intervalMinutes - 15).coerceAtLeast(15) }
                        ) { Text("−", color = AppColors.Accent) }
                        Text("$intervalMinutes мин.", color = AppColors.MainText)
                        TextButton(
                            onClick = { intervalMinutes = (intervalMinutes + 15).coerceAtMost(120) }
                        ) { Text("+", color = AppColors.Accent) }
                    }
                }
            }

            effectiveShifts.find { it.date == anchorDate }?.let { day ->
                Text(
                    "${shiftStatus(day.status)}${if (day.predicted) " · предположение ?" else ""}" +
                        if (day.status == "WORK") " · ${CalendarDisplay.interval(day.startMinute, day.durationMinutes)}" else "",
                    color = shiftColor(day.status), fontSize = 12.sp
                )
            }
            if (mode == CalendarViewMode.DAY) {
                val holidays = ShiftRules.specialDatesFor(anchorDate, specialDates)
                if (holidays.isNotEmpty()) Text("★ " + holidays.joinToString(" · ") { it.title }, color = AppColors.Yellow, fontSize = 12.sp, maxLines = 2)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openNewEvent(anchorDate, 9 * 60) }, modifier = Modifier.weight(1f)) {
                    Text("＋ Добавить", color = AppColors.Accent)
                }
                TextButton(onClick = { showShifts = true }) { Text("Смены", color = AppColors.Accent) }
            }

            when (mode) {
                CalendarViewMode.DAY -> {
                    ScheduleTimeline(
                        date = anchorDate,
                        intervalMinutes = intervalMinutes,
                        occurrences = displayEvents.filter { it.date == anchorDate },
                        tasks = scheduledTasks.filter { it.date == anchorDate },
                        onAddEvent = ::openNewEvent,
                        onEventClick = ::openExistingEvent,
                        onTaskClick = { taskForActions = it },
                        onChangeTask = { task, range ->
                            saveTaskPlacement(task, startMinute = range.startMinute, durationMinutes = range.durationMinutes)
                        },
                        onChangeEvent = { event, range ->
                            saveEventPosition(event, startMinute = range.startMinute, durationMinutes = range.durationMinutes)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                CalendarViewMode.WEEK -> {
                    WeekTasksContent(
                        dates = dates,
                        tasks = scheduledTasks,
                        paymentBlocks = paymentBlocks,
                        onPaymentClick = ::openExistingEvent,
                        shifts = effectiveShifts,
                        specialDates = specialDates,
                        onTaskClick = { taskForActions = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                CalendarViewMode.MONTH -> {
                    MonthEventsContent(
                        anchorDate = anchorDate,
                        occurrences = displayEvents,
                        scheduledTasks = scheduledTasks,
                        tasks = tasks,
                        placements = placements,
                        shifts = effectiveShifts,
                        specialDates = specialDates,
                        categories = categories,
                        onDateSelected = { anchorDate = it },
                        onDateDoubleClick = { date -> anchorDate = date; openNewEvent(date, 9 * 60) },
                        onEventClick = ::openExistingEvent,
                        onAddEvent = ::openNewEvent,
                        modifier = Modifier.weight(1f)
                    )
                }
                CalendarViewMode.YEAR -> {
                    YearEventsContent(
                        anchorDate = anchorDate,
                        events = events,
                        overrides = eventOverrides,
                        onMonthSelected = {
                            anchorDate = it
                            mode = CalendarViewMode.MONTH
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    paymentDialogDate?.let { date ->
        PaymentPlansDialog(viewModel, date) { paymentDialogDate = null }
    }
    if (showPlanningDialog) {
        CalendarPlanningDialog(viewModel, anchorDate) { showPlanningDialog = false }
    }

    if (showEventDialog && createTask) {
        TaskEditorDialog(
            task = null,
            initialDate = eventInitialDate,
            creationHeader = { CalendarCreationTabs(true) { createTask = it } },
            onDismiss = { showEventDialog = false },
            onSave = { title, description, start, deadline ->
                taskViewModel.addTask(TaskEntity(title = title, description = description, startDate = start, deadline = deadline))
                showEventDialog = false
            }
        )
    } else if (showEventDialog) {
        val currentEvent = editingEvent
        val editedOverride = eventOverrides.find {
            it.eventId == currentEvent?.id && it.occurrenceDate == eventOccurrenceDate
        }
        CalendarEventDialog(
            event = currentEvent,
            initialPayMode = editedOverride?.payMode ?: currentEvent?.payMode,
            initialPayAmount = editedOverride?.payAmountCents ?: currentEvent?.payAmountCents,
            creationHeader = if (currentEvent == null) ({ CalendarCreationTabs(false) { createTask = it } }) else null,
            initialDate = eventInitialDate,
            initialStartMinute = eventInitialMinute,
            initialDurationMinutes = eventInitialDuration,
            categories = categories,
            defaultHourlyRateCents = shiftPattern?.hourlyRateCents ?: 0,
            onDismiss = { showEventDialog = false },
            onSave = { title, description, date, startMinute, durationMinutes, repeatDaysMask, categoryId, amountCents, payMode ->
                val kind = categories.find { it.id == categoryId }?.kind
                if (currentEvent == null) {
                    viewModel.addEvent(
                        CalendarEventEntity(
                            title = title,
                            description = description,
                            startDate = date,
                            startMinute = startMinute,
                            durationMinutes = durationMinutes,
                            repeatDaysMask = repeatDaysMask,
                            categoryId = categoryId,
                            oneTimePaymentCents = if (kind == "SIDE_JOB") amountCents else null,
                            payMode = if (kind == "WORK") payMode.name else null,
                            payAmountCents = if (kind == "WORK") amountCents else null
                        )
                    )
                } else {
                    val replaceBaseSchedule = currentEvent.repeatDaysMask == 0 || repeatDaysMask == 0
                    val editBasePay = replaceBaseSchedule || currentEvent.categoryId != categoryId
                    val keepOrphanPay = categoryId == null && currentEvent.categoryId == null
                    val baseMode = WorkPay.from(currentEvent.payMode)
                    val baseAmount = currentEvent.payAmountCents ?: currentEvent.hourlyRateCents
                    val customPay = kind == "WORK" && !editBasePay && (baseMode != payMode || baseAmount != amountCents)
                    val anchor = if (replaceBaseSchedule) date else currentEvent.startDate
                    viewModel.saveEditedOccurrence(
                        event = currentEvent.copy(
                            title = title,
                            description = description,
                            startDate = anchor,
                            startMinute = if (replaceBaseSchedule) startMinute else currentEvent.startMinute,
                            durationMinutes = if (replaceBaseSchedule) durationMinutes else currentEvent.durationMinutes,
                            repeatDaysMask = repeatDaysMask,
                            categoryId = categoryId,
                            oneTimePaymentCents = if (kind == "SIDE_JOB") amountCents else if (categoryId == null && currentEvent.categoryId == null) currentEvent.oneTimePaymentCents else null,
                            hourlyRateCents = if (kind == "WORK" && !editBasePay || categoryId == null && currentEvent.categoryId == null) currentEvent.hourlyRateCents else null,
                            payMode = if (kind == "WORK" && editBasePay) payMode.name else if (!editBasePay || keepOrphanPay) currentEvent.payMode else null,
                            payAmountCents = if (kind == "WORK" && editBasePay) amountCents else if (!editBasePay || keepOrphanPay) currentEvent.payAmountCents else null
                        ),
                        occurrence = if (replaceBaseSchedule) null else CalendarEventOverrideEntity(
                            eventId = currentEvent.id,
                            occurrenceDate = eventOccurrenceDate,
                            date = date,
                            startMinute = startMinute,
                            durationMinutes = durationMinutes,
                            payMode = if (customPay) payMode.name else null,
                            payAmountCents = if (customPay) amountCents else null
                        )
                    )
                }
                showEventDialog = false
            },
            onDeleteOccurrence = {
                if (currentEvent != null) {
                    if (currentEvent.repeatDaysMask == 0) {
                        viewModel.deleteEvent(currentEvent)
                    } else {
                        viewModel.saveEventOverride(
                            CalendarEventOverrideEntity(
                                eventId = currentEvent.id,
                                occurrenceDate = eventOccurrenceDate,
                                date = eventInitialDate,
                                startMinute = eventInitialMinute,
                                durationMinutes = eventInitialDuration,
                                deleted = true
                            )
                        )
                    }
                }
                showEventDialog = false
            },
            onDeleteAll = {
                currentEvent?.let(viewModel::deleteEvent)
                showEventDialog = false
            }
        )
    }

    taskForActions?.let { scheduled ->
        AlertDialog(
            onDismissRequest = { taskForActions = null },
            containerColor = AppColors.Card,
            title = { Text(scheduled.task.title, color = AppColors.MainText) },
            text = {
                Text(
                    "Убрать задачу только из этого дня или удалить её из приложения?",
                    color = AppColors.SecondaryText
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        taskForTimeEdit = scheduled
                        taskForActions = null
                    }) {
                        Text("Изменить время", color = AppColors.Accent)
                    }
                    TextButton(onClick = {
                        saveTaskPlacement(scheduled, hidden = true)
                        taskForActions = null
                    }) {
                        Text("Убрать с дня", color = AppColors.Accent)
                    }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        taskForFullDelete = scheduled
                        taskForActions = null
                    }) {
                        Text("Удалить полностью", color = AppColors.Red)
                    }
                    TextButton(onClick = { taskForActions = null }) {
                        Text("Отмена", color = AppColors.SecondaryText)
                    }
                }
            }
        )
    }

    taskForTimeEdit?.let { scheduled ->
        TaskTimeEditDialog(
            scheduled = scheduled,
            onDismiss = { taskForTimeEdit = null },
            onSave = { startMinute, durationMinutes ->
                saveTaskPlacement(
                    scheduled = scheduled,
                    startMinute = startMinute,
                    durationMinutes = durationMinutes
                )
                taskForTimeEdit = null
            }
        )
    }

    taskForFullDelete?.let { scheduled ->
        AlertDialog(
            onDismissRequest = { taskForFullDelete = null },
            containerColor = AppColors.Card,
            title = { Text("Удалить задачу полностью?", color = AppColors.MainText) },
            text = {
                Text(
                    "«${scheduled.task.title}» будет удалена из списка задач и календаря.",
                    color = AppColors.SecondaryText
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlacement(scheduled.task.id)
                    taskViewModel.deleteTask(scheduled.task)
                    taskForFullDelete = null
                }) {
                    Text("Удалить", color = AppColors.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { taskForFullDelete = null }) {
                    Text("Отмена", color = AppColors.SecondaryText)
                }
            }
        )
    }
}


@Composable
private fun CalendarCreationTabs(task: Boolean, onSelect: (Boolean) -> Unit) {
    Row {
        TextButton(onClick = { onSelect(false) }) {
            Text("Мероприятие", color = if (task) AppColors.SecondaryText else AppColors.Accent)
        }
        TextButton(onClick = { onSelect(true) }) {
            Text("Задача", color = if (task) AppColors.Accent else AppColors.SecondaryText)
        }
    }
}

@Composable
private fun TaskTimeEditDialog(
    scheduled: ScheduledTask,
    onDismiss: () -> Unit,
    onSave: (startMinute: Int, durationMinutes: Int) -> Unit
) {
    val initialDuration = scheduled.durationMinutes
    var startMinute by remember(scheduled.task.id, scheduled.date, scheduled.startMinute) {
        mutableStateOf(scheduled.startMinute)
    }
    var endMinute by remember(scheduled.task.id, scheduled.date, scheduled.startMinute, initialDuration) {
        mutableStateOf((scheduled.startMinute + initialDuration).coerceAtMost(24 * 60))
    }
    var durationHoursText by remember(scheduled.task.id, initialDuration) {
        mutableStateOf((initialDuration / 60).toString())
    }
    var durationMinutesText by remember(scheduled.task.id, initialDuration) {
        mutableStateOf((initialDuration % 60).toString())
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        disabledTextColor = Color.White,
        cursorColor = AppColors.Accent,
        focusedBorderColor = AppColors.Accent,
        unfocusedBorderColor = AppColors.SecondaryText,
        focusedLabelColor = AppColors.Accent,
        unfocusedLabelColor = Color.White,
        focusedTrailingIconColor = Color.White,
        unfocusedTrailingIconColor = Color.White
    )

    fun currentDuration(): Int? {
        val hours = durationHoursText.toIntOrNull() ?: return null
        val minutes = durationMinutesText.toIntOrNull() ?: return null
        if (hours < 0 || minutes !in 0..59) return null
        return hours * 60 + minutes
    }

    fun updateDurationFields(totalMinutes: Int) {
        durationHoursText = (totalMinutes / 60).toString()
        durationMinutesText = (totalMinutes % 60).toString()
    }

    fun updateEndFromDuration() {
        val duration = currentDuration() ?: return
        endMinute = startMinute + duration
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Card,
        title = { Text("Время задачи", color = Color.White) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(scheduled.task.title, color = Color.White, fontSize = 13.sp)

                TimePickerField(
                    label = "Начало (с)",
                    minute = startMinute,
                    onTimeSelected = { selected ->
                        startMinute = selected
                        updateEndFromDuration()
                        validationError = null
                    }
                )

                TimePickerField(
                    label = "Окончание (до)",
                    minute = endMinute,
                    endOfDayAtMidnight = true,
                    onTimeSelected = { selectedEnd ->
                        if (selectedEnd > startMinute) {
                            endMinute = selectedEnd
                            updateDurationFields(selectedEnd - startMinute)
                            validationError = null
                        } else {
                            validationError = "Окончание должно быть позже начала."
                        }
                    }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = durationHoursText,
                        onValueChange = { value ->
                            durationHoursText = value.filter(Char::isDigit).take(4)
                            updateEndFromDuration()
                            validationError = null
                        },
                        label = { Text("Часы") },
                        singleLine = true,
                        colors = fieldColors
                    )
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = durationMinutesText,
                        onValueChange = { value ->
                            durationMinutesText = value.filter(Char::isDigit).take(2)
                            updateEndFromDuration()
                            validationError = null
                        },
                        label = { Text("Минуты (0–59)") },
                        singleLine = true,
                        colors = fieldColors
                    )
                }

                Text(
                    "Нажми на поле начала или окончания, чтобы выбрать время. Длительность можно вводить в часах и минутах.",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp
                )
                validationError?.let { Text(it, color = AppColors.Red, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val duration = currentDuration()
                when {
                    duration == null -> validationError = "Введи часы и минуты числами."
                    startMinute < 0 || startMinute >= 24 * 60 ->
                        validationError = "Начало должно быть в диапазоне 00:00–23:59."
                    duration < 15 -> validationError = "Минимальная длительность — 15 минут."
                    startMinute + duration != endMinute ->
                        validationError = "Проверь окончание и длительность."
                    endMinute > 24 * 60 -> validationError = "Задача должна завершаться не позднее 24:00."
                    else -> onSave(startMinute, duration)
                }
            }) { Text("Сохранить", color = AppColors.Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = AppColors.SecondaryText) }
        }
    )
}

private const val TIMELINE_START_MINUTE = 0
private const val TIMELINE_END_MINUTE = 24 * 60
private val TIMELINE_HEIGHT: Dp = (TIMELINE_END_MINUTE - TIMELINE_START_MINUTE).dp

private data class TimelineBlock(
    val id: String,
    val startMinute: Int,
    val durationMinutes: Int,
    val event: CalendarEventOccurrence? = null,
    val task: ScheduledTask? = null,
    val lane: Int = 0
)

private fun arrangeTimelineLanes(blocks: List<TimelineBlock>): Pair<List<TimelineBlock>, Int> {
    val laneEnds = mutableListOf<Int>()
    val arranged = blocks
        .sortedWith(compareBy<TimelineBlock> { it.startMinute }.thenByDescending { it.durationMinutes })
        .map { block ->
            var lane = laneEnds.indexOfFirst { it <= block.startMinute }
            if (lane == -1) {
                lane = laneEnds.size
                laneEnds.add(0)
            }
            laneEnds[lane] = block.startMinute + block.durationMinutes
            block.copy(lane = lane)
        }
    return arranged to laneEnds.size.coerceAtLeast(1)
}

@Composable
private fun ScheduleTimeline(
    date: String,
    intervalMinutes: Int,
    occurrences: List<CalendarEventOccurrence>,
    tasks: List<ScheduledTask>,
    onAddEvent: (String, Int) -> Unit,
    onEventClick: (CalendarEventOccurrence) -> Unit,
    onTaskClick: (ScheduledTask) -> Unit,
    onChangeTask: (ScheduledTask, TimelineRange) -> Unit,
    onChangeEvent: (CalendarEventOccurrence, TimelineRange) -> Unit,
    modifier: Modifier = Modifier
) {
    val blocks = remember(occurrences, tasks) {
        buildList {
            occurrences.forEach { occurrence ->
                add(TimelineBlock(
                    id = "event_${occurrence.event.id}_${occurrence.occurrenceDate}_${occurrence.date}",
                    startMinute = occurrence.startMinute,
                    durationMinutes = occurrence.durationMinutes,
                    event = occurrence
                ))
            }
            tasks.forEach { scheduled ->
                add(TimelineBlock(
                    id = "task_${scheduled.task.id}_${scheduled.date}",
                    startMinute = scheduled.startMinute,
                    durationMinutes = scheduled.durationMinutes,
                    task = scheduled
                ))
            }
        }
    }
    val (arrangedBlocks, laneCount) = remember(blocks) { arrangeTimelineLanes(blocks) }
    val density = LocalDensity.current

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState(initial = with(density) { (7 * 60).dp.roundToPx() })),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = CalendarDateUtils.formatLongDate(date),
            color = AppColors.MainText,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.width(48.dp).height(TIMELINE_HEIGHT)) {
                for (hour in 0..23) {
                    Text(
                        text = "%02d:00".format(hour),
                        modifier = Modifier.offset(y = ((hour * 60 - TIMELINE_START_MINUTE).coerceAtLeast(0)).dp),
                        color = AppColors.SecondaryText,
                        fontSize = 10.sp
                    )
                }
                Text(
                    text = "24:00",
                    modifier = Modifier.align(Alignment.BottomStart),
                    color = AppColors.SecondaryText,
                    fontSize = 10.sp
                )
            }

            BoxWithConstraints(
                modifier = Modifier.weight(1f).height(TIMELINE_HEIGHT)
            ) {
                val laneWidth = maxWidth / laneCount.toFloat()
                for (minute in 0..(TIMELINE_END_MINUTE - TIMELINE_START_MINUTE) step intervalMinutes) {
                    Box(
                        modifier = Modifier
                            .offset(y = minute.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(AppColors.SecondaryText.copy(alpha = 0.2f))
                    )
                }

                Box(
                    modifier = Modifier.fillMaxSize().pointerInput(date, intervalMinutes) {
                        detectTapGestures { position ->
                            val offsetMinutes = with(density) { position.y.toDp().value.roundToInt() }
                            val rawMinute = (TIMELINE_START_MINUTE + offsetMinutes)
                                .coerceIn(TIMELINE_START_MINUTE, TIMELINE_END_MINUTE - 15)
                            val roundedMinute = ((rawMinute + intervalMinutes / 2) / intervalMinutes) * intervalMinutes
                            onAddEvent(date, roundedMinute.coerceAtMost(TIMELINE_END_MINUTE - 15))
                        }
                    }
                )

                arrangedBlocks.forEach { block ->
                    key(block.id) {
                        CalendarTimelineCard(
                            itemKey = block.id,
                            title = block.event?.event?.title ?: block.task!!.task.title,
                            range = TimelineRange(block.startMinute, block.durationMinutes),
                            laneWidth = laneWidth,
                            lane = block.lane,
                            color = block.task?.let { taskPriorityColor(it.priority).copy(alpha = 0.20f) }
                                ?: AppColors.Card,
                            editable = block.event?.event?.id?.let { it > 0 } ?: true,
                            onClick = { displayedRange ->
                                block.event?.let {
                                    onEventClick(it.withSegmentRange(displayedRange))
                                }
                                block.task?.let {
                                    onTaskClick(it.copy(startMinute = displayedRange.startMinute, durationMinutes = displayedRange.durationMinutes))
                                }
                            },
                            onSave = { range ->
                                block.event?.let { onChangeEvent(it, range) }
                                block.task?.let { onChangeTask(it, range) }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekTasksContent(
    dates: List<String>,
    tasks: List<ScheduledTask>,
    paymentBlocks: List<CalendarEventOccurrence>,
    onPaymentClick: (CalendarEventOccurrence) -> Unit,
    shifts: List<ShiftDayEntity>,
    specialDates: List<SpecialDateEntity>,
    onTaskClick: (ScheduledTask) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Задачи на неделю", color = AppColors.SecondaryText, fontSize = 13.sp)
        dates.forEach { date ->
            val dayTasks = tasks.filter { it.date == date }.sortedBy { it.startMinute }
            val shift = shifts.find { it.date == date }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = shift?.let { shiftColor(it.status).copy(alpha = 0.18f) } ?: AppColors.Card)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = CalendarDateUtils.formatLongDate(date),
                        color = AppColors.MainText,
                        fontWeight = FontWeight.Bold
                    )
                    shift?.let { Text(shiftStatus(it.status) + if (it.predicted) " · предположение ?" else "", color = shiftColor(it.status), fontSize = 12.sp) }
                    ShiftRules.specialDatesFor(date, specialDates).forEach { Text("★ ${it.title}", color = AppColors.Yellow, fontSize = 12.sp) }
                    paymentBlocks.filter { it.date == date }.forEach { payment ->
                        TextButton(onClick = { onPaymentClick(payment) }) { Text(payment.event.title, color = AppColors.Accent) }
                    }
                    if (dayTasks.isEmpty()) {
                        Text("Нет запланированных задач", color = AppColors.SecondaryText, fontSize = 12.sp)
                    } else {
                        dayTasks.forEach { scheduled ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onTaskClick(scheduled) }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "%02d:%02d".format(scheduled.startMinute / 60, scheduled.startMinute % 60),
                                    modifier = Modifier.width(55.dp),
                                    color = taskPriorityColor(scheduled.priority),
                                    fontSize = 12.sp
                                )
                                Column {
                                    Text(scheduled.task.title, color = AppColors.MainText, fontWeight = FontWeight.SemiBold)
                                    Text("Приоритет ${scheduled.priority}", color = taskPriorityColor(scheduled.priority), fontSize = 11.sp)
                                    if (scheduled.task.deadline.isNotBlank()) {
                                        Text(
                                            text = "Дедлайн: ${CalendarDateUtils.formatDate(scheduled.task.deadline)}",
                                            color = AppColors.SecondaryText,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthEventsContent(
    anchorDate: String,
    occurrences: List<CalendarEventOccurrence>,
    scheduledTasks: List<ScheduledTask>,
    tasks: List<TaskEntity>,
    placements: List<CalendarTaskPlacementEntity>,
    shifts: List<ShiftDayEntity>,
    specialDates: List<SpecialDateEntity>,
    categories: List<EventCategoryEntity>,
    onDateSelected: (String) -> Unit,
    onDateDoubleClick: (String) -> Unit,
    onEventClick: (CalendarEventOccurrence) -> Unit,
    onAddEvent: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridDates = remember(anchorDate) { CalendarDateUtils.monthGridDates(anchorDate) }
    val eventsByDate = occurrences.groupBy { it.date }
    val scheduledById = scheduledTasks.associateBy { it.task.id }
    val placementById = placements.associateBy { it.taskId }
    val tasksByDate = tasks.mapNotNull { task ->
        val placement = placementById[task.id]
        val date = scheduledById[task.id]?.date ?: when {
            placement?.hidden == true -> null
            placement != null -> placement.date
            else -> task.startDate.takeIf { it.isNotBlank() }
        }
        date?.let { it to task }
    }.groupBy({ it.first }, { it.second })
    val selectedEvents = eventsByDate[anchorDate].orEmpty()
    val selectedTasks = tasksByDate[anchorDate].orEmpty()

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { day ->
                Text(day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = AppColors.SecondaryText, fontSize = 11.sp)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth().height((gridDates.size / 7 * 50).dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(gridDates, key = { it }) { date ->
                val eventsOnDay = eventsByDate[date].orEmpty()
                val dayEvents = eventsOnDay.size
                val dayTasks = tasksByDate[date].orEmpty().size
                val hasWork = eventsOnDay.any { occurrence ->
                    (occurrence.event.id < 0 && occurrence.plannedPayment == null) ||
                        occurrence.event.hourlyRateCents != null ||
                        occurrence.event.payAmountCents != null ||
                        categories.any { it.id == occurrence.event.categoryId && it.kind == "WORK" }
                }
                val dayShift = shifts.find { it.date == date }
                val daySpecials = ShiftRules.specialDatesFor(date, specialDates)
                val isSelected = date == anchorDate
                val isInMonth = date.take(7) == anchorDate.take(7)
                Card(
                    modifier = Modifier.fillMaxWidth().height(48.dp).combinedClickable(
                        onClick = { onDateSelected(date) },
                        onDoubleClick = { onDateDoubleClick(date) }
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = dayShift?.let { shiftColor(it.status).copy(alpha = if (isSelected) 0.65f else if (it.predicted) 0.12f else 0.28f) }
                            ?: if (hasWork) shiftColor("WORK").copy(alpha = if (isSelected) 0.65f else 0.28f)
                            else if (isSelected) AppColors.Accent.copy(alpha = 0.28f) else AppColors.Card
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = CalendarDateUtils.parseCalendar(date).get(java.util.Calendar.DAY_OF_MONTH).toString(),
                            color = if (isInMonth) AppColors.MainText else AppColors.SecondaryText.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                        if (dayEvents + dayTasks > 0 || daySpecials.isNotEmpty() || dayShift?.predicted == true) {
                            Text("${if (dayEvents + dayTasks > 0) "• ${dayEvents + dayTasks}" else ""}${if (daySpecials.isNotEmpty()) " ★" else ""}${if (dayShift?.predicted == true) " ?" else ""}", color = AppColors.MainText, fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        Text("Голубой — работа · красный — выходной · жёлтый — больничный · фиолетовый — отпуск · ? — предположение", color = AppColors.SecondaryText, fontSize = 11.sp)
        ShiftRules.specialDatesFor(anchorDate, specialDates).forEach { Text("★ ${it.title}", color = AppColors.Yellow) }
        Text(CalendarDateUtils.formatLongDate(anchorDate), color = AppColors.MainText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Двойное нажатие добавляет запись", color = AppColors.SecondaryText, fontSize = 11.sp)
        if (selectedTasks.isEmpty() && selectedEvents.isEmpty()) {
            Text("На этот день записей нет.", color = AppColors.SecondaryText, fontSize = 13.sp)
        }
        if (selectedTasks.isNotEmpty()) {
            Text("Задачи", color = AppColors.SecondaryText, fontWeight = FontWeight.SemiBold)
            selectedTasks.forEach { task ->
                val time = scheduledById[task.id]?.let { CalendarDisplay.interval(it.startMinute, it.durationMinutes) }
                Text(
                    (if (task.completed) "✓ " else "• ") + task.title + (time?.let { " · $it" } ?: ""),
                    color = AppColors.MainText
                )
            }
        }
        if (selectedEvents.isNotEmpty()) {
            Text("Мероприятия и смены", color = AppColors.SecondaryText, fontWeight = FontWeight.SemiBold)
            selectedEvents.forEach { occurrence ->
                EventListCard(occurrence = occurrence, categoryName = categories.find { it.id == occurrence.event.categoryId }?.name, onClick = { onEventClick(occurrence) })
            }
        }
        TextButton(onClick = { onAddEvent(anchorDate, 9 * 60) }) {
            Text("＋ Добавить запись на этот день", color = AppColors.Accent)
        }
    }
}

@Composable
private fun YearEventsContent(
    anchorDate: String,
    events: List<CalendarEventEntity>,
    overrides: List<CalendarEventOverrideEntity>,
    onMonthSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val yearStart = CalendarDateUtils.startOfYear(anchorDate)
    val yearEnd = CalendarDateUtils.endOfYear(anchorDate)
    val yearOccurrences = remember(events, overrides, yearStart, yearEnd) {
        CalendarEventRules.expand(events, yearStart, yearEnd, overrides)
    }
    val months = remember(yearStart) { (0..11).map { CalendarDateUtils.addMonths(yearStart, it) } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(months, key = { it }) { monthDate ->
            val monthStart = CalendarDateUtils.startOfMonth(monthDate)
            val monthEnd = CalendarDateUtils.endOfMonth(monthDate)
            val count = yearOccurrences.count { it.date >= monthStart && it.date <= monthEnd }
            Card(
                modifier = Modifier.fillMaxWidth().height(100.dp).clickable { onMonthSelected(monthStart) },
                colors = CardDefaults.cardColors(containerColor = AppColors.Card)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = CalendarDateUtils.formatMonth(monthDate).substringBefore(" "),
                        color = AppColors.MainText,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text("$count мероприятий", color = AppColors.SecondaryText, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun EventListCard(
    occurrence: CalendarEventOccurrence,
    categoryName: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = AppColors.Card)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(occurrence.event.title, color = AppColors.MainText, fontWeight = FontWeight.SemiBold)
            Text(
                text = CalendarDisplay.interval(occurrence.instanceStartMinute, occurrence.instanceDurationMinutes) +
                    if (occurrence.date != occurrence.instanceDate) " · продолжение с ${CalendarDateUtils.formatDate(occurrence.instanceDate)}" else "",
                color = AppColors.SecondaryText,
                fontSize = 12.sp
            )
            categoryName?.let { Text(it, color = AppColors.Accent, fontSize = 12.sp) }
            if (occurrence.event.description.isNotBlank()) {
                Text(occurrence.event.description, color = AppColors.SecondaryText, fontSize = 12.sp)
            }
        }
    }
}

private fun taskPriorityColor(priority: Int): Color = when (priority) {
    5 -> AppColors.Red
    4 -> AppColors.LatePurple
    3 -> AppColors.Yellow
    2 -> AppColors.Accent
    else -> AppColors.Green
}


private fun shiftColor(status: String): Color = when (status) {
    "WORK" -> Color(0xFF7DD3FC)
    "OFF" -> AppColors.Red
    "SICK" -> AppColors.Yellow
    "VACATION" -> AppColors.Accent
    else -> AppColors.SecondaryText
}
