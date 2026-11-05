package com.anasikus.lifeassistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anasikus.lifeassistant.data.CalendarEventEntity
import com.anasikus.lifeassistant.data.CalendarEventOverrideEntity
import com.anasikus.lifeassistant.data.CalendarTaskPlacementEntity
import com.anasikus.lifeassistant.data.TaskEntity
import com.anasikus.lifeassistant.domain.CalendarDateUtils
import com.anasikus.lifeassistant.domain.CalendarEventOccurrence
import com.anasikus.lifeassistant.domain.CalendarEventRules
import com.anasikus.lifeassistant.domain.CalendarTaskScheduler
import com.anasikus.lifeassistant.domain.ScheduledTask
import com.anasikus.lifeassistant.ui.AppColors
import com.anasikus.lifeassistant.ui.components.CalendarEventDialog
import com.anasikus.lifeassistant.viewmodel.CalendarViewModel
import com.anasikus.lifeassistant.viewmodel.TaskViewModel
import android.app.TimePickerDialog
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

    var anchorDate by remember { mutableStateOf(CalendarDateUtils.today()) }
    var mode by remember { mutableStateOf(CalendarViewMode.WEEK) }
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

    val occurrences = remember(events, eventOverrides, rangeStart, rangeEnd) {
        CalendarEventRules.expand(
            events = events,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            overrides = eventOverrides
        )
    }

    val scheduledTasks = remember(tasks, occurrences, placements, rangeStart, rangeEnd) {
        CalendarTaskScheduler.schedule(
            tasks = tasks,
            events = occurrences,
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
        editingEvent = null
        eventInitialDate = date
        eventOccurrenceDate = date
        eventInitialMinute = minute.coerceIn(0, 23 * 60)
        eventInitialDuration = 60
        showEventDialog = true
    }

    fun openExistingEvent(occurrence: CalendarEventOccurrence) {
        editingEvent = occurrence.event
        eventInitialDate = occurrence.date
        eventOccurrenceDate = occurrence.occurrenceDate
        eventInitialMinute = occurrence.startMinute
        eventInitialDuration = occurrence.durationMinutes
        showEventDialog = true
    }

    fun saveTaskPlacement(
        scheduled: ScheduledTask,
        startMinute: Int = scheduled.startMinute,
        durationMinutes: Int = scheduled.durationMinutes,
        hidden: Boolean = false
    ) {
        val safeDuration = durationMinutes.coerceIn(15, 16 * 60)
        val safeStart = startMinute.coerceIn(7 * 60, 23 * 60 - safeDuration)
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
        viewModel.saveEventOverride(
            CalendarEventOverrideEntity(
                eventId = occurrence.event.id,
                occurrenceDate = occurrence.occurrenceDate,
                date = occurrence.date,
                startMinute = startMinute.coerceIn(0, 24 * 60 - 15),
                durationMinutes = durationMinutes.coerceIn(15, 24 * 60),
                deleted = false
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

    Surface(modifier = modifier.fillMaxSize(), color = AppColors.Background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "ПЛАНИРОВАНИЕ",
                color = AppColors.Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Календарь",
                color = AppColors.MainText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

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

            if (mode == CalendarViewMode.DAY) {
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

            OutlinedButton(
                onClick = { openNewEvent(anchorDate, 9 * 60) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("＋ Добавить мероприятие", color = AppColors.Accent)
            }

            when (mode) {
                CalendarViewMode.DAY -> {
                    ScheduleTimeline(
                        date = anchorDate,
                        intervalMinutes = intervalMinutes,
                        occurrences = occurrences.filter { it.date == anchorDate },
                        tasks = scheduledTasks.filter { it.date == anchorDate },
                        onAddEvent = ::openNewEvent,
                        onEventClick = ::openExistingEvent,
                        onTaskClick = { taskForActions = it },
                        onMoveTask = { task, start -> saveTaskPlacement(task, startMinute = start) },
                        onResizeTask = { task, duration -> saveTaskPlacement(task, durationMinutes = duration) },
                        onMoveEvent = { event, start -> saveEventPosition(event, startMinute = start) },
                        onResizeEvent = { event, duration -> saveEventPosition(event, durationMinutes = duration) },
                        modifier = Modifier.weight(1f)
                    )
                }
                CalendarViewMode.WEEK -> {
                    WeekTasksContent(
                        dates = dates,
                        tasks = scheduledTasks,
                        onTaskClick = { taskForActions = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                CalendarViewMode.MONTH -> {
                    MonthEventsContent(
                        anchorDate = anchorDate,
                        occurrences = occurrences,
                        onDateSelected = { anchorDate = it },
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

    if (showEventDialog) {
        val currentEvent = editingEvent
        CalendarEventDialog(
            event = currentEvent,
            initialDate = eventInitialDate,
            initialStartMinute = eventInitialMinute,
            onDismiss = { showEventDialog = false },
            onSave = { title, description, date, startMinute, durationMinutes, repeatDaysMask ->
                if (currentEvent == null) {
                    viewModel.addEvent(
                        CalendarEventEntity(
                            title = title,
                            description = description,
                            startDate = date,
                            startMinute = startMinute,
                            durationMinutes = durationMinutes,
                            repeatDaysMask = repeatDaysMask
                        )
                    )
                } else {
                    viewModel.updateEvent(
                        currentEvent.copy(
                            title = title,
                            description = description,
                            startDate = date,
                            startMinute = startMinute,
                            durationMinutes = durationMinutes,
                            repeatDaysMask = repeatDaysMask
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
private fun TaskTimeEditDialog(
    scheduled: ScheduledTask,
    onDismiss: () -> Unit,
    onSave: (startMinute: Int, durationMinutes: Int) -> Unit
) {
    val context = LocalContext.current
    fun formatTime(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

    val initialDuration = scheduled.durationMinutes
    var startMinute by remember(scheduled.task.id, scheduled.date, scheduled.startMinute) {
        mutableStateOf(scheduled.startMinute)
    }
    var endMinute by remember(scheduled.task.id, scheduled.date, scheduled.startMinute, initialDuration) {
        mutableStateOf((scheduled.startMinute + initialDuration).coerceAtMost(23 * 60))
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

                OutlinedTextField(
                    value = formatTime(startMinute),
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth().clickable {
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                startMinute = hour * 60 + minute
                                updateEndFromDuration()
                                validationError = null
                            },
                            startMinute / 60,
                            startMinute % 60,
                            true
                        ).show()
                    },
                    readOnly = true,
                    label = { Text("Начало (с)") },
                    singleLine = true,
                    colors = fieldColors
                )

                OutlinedTextField(
                    value = formatTime(endMinute.coerceIn(0, 24 * 60)),
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth().clickable {
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                val selectedEnd = hour * 60 + minute
                                if (selectedEnd > startMinute) {
                                    endMinute = selectedEnd
                                    updateDurationFields(selectedEnd - startMinute)
                                    validationError = null
                                } else {
                                    validationError = "Окончание должно быть позже начала."
                                }
                            },
                            (endMinute / 60).coerceIn(0, 23),
                            endMinute % 60,
                            true
                        ).show()
                    },
                    readOnly = true,
                    label = { Text("Окончание (до)") },
                    singleLine = true,
                    colors = fieldColors
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
                    startMinute < 7 * 60 || startMinute >= 23 * 60 ->
                        validationError = "Начало должно быть в диапазоне 07:00–22:59."
                    duration < 1 -> validationError = "Длительность должна быть больше нуля."
                    startMinute + duration != endMinute ->
                        validationError = "Проверь окончание и длительность."
                    endMinute > 23 * 60 -> validationError = "Задача должна завершаться не позднее 23:00."
                    else -> onSave(startMinute, duration)
                }
            }) { Text("Сохранить", color = AppColors.Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = AppColors.SecondaryText) }
        }
    )
}

private const val TIMELINE_START_MINUTE = 7 * 60
private const val TIMELINE_END_MINUTE = 23 * 60
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
    onMoveTask: (ScheduledTask, Int) -> Unit,
    onResizeTask: (ScheduledTask, Int) -> Unit,
    onMoveEvent: (CalendarEventOccurrence, Int) -> Unit,
    onResizeEvent: (CalendarEventOccurrence, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val blocks = remember(occurrences, tasks) {
        buildList {
            occurrences.forEach { occurrence ->
                add(TimelineBlock(
                    id = "event_${occurrence.event.id}_${occurrence.occurrenceDate}",
                    startMinute = occurrence.startMinute,
                    durationMinutes = occurrence.durationMinutes,
                    event = occurrence
                ))
            }
            tasks.forEach { scheduled ->
                add(TimelineBlock(
                    id = "task_${scheduled.task.id}",
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
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
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
                for (hour in 7..22) {
                    Text(
                        text = "%02d:00".format(hour),
                        modifier = Modifier.offset(y = ((hour * 60 - TIMELINE_START_MINUTE).coerceAtLeast(0)).dp),
                        color = AppColors.SecondaryText,
                        fontSize = 10.sp
                    )
                }
                Text(
                    text = "23:00",
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
                        val topMinute = (block.startMinute - TIMELINE_START_MINUTE).coerceAtLeast(0)
                        val visualHeight = block.durationMinutes.coerceAtLeast(30)
                            .coerceAtMost((TIMELINE_END_MINUTE - block.startMinute).coerceAtLeast(15))
                        if (block.event != null) {
                            EventTimelineCard(
                                occurrence = block.event,
                                laneWidth = laneWidth,
                                lane = block.lane,
                                blockTopMinute = topMinute,
                                blockHeightMinutes = visualHeight,
                                onClick = { onEventClick(block.event) },
                                onMove = { onMoveEvent(block.event, it) },
                                onResize = { onResizeEvent(block.event, it) }
                            )
                        } else if (block.task != null) {
                            TaskTimelineCard(
                                scheduled = block.task,
                                laneWidth = laneWidth,
                                lane = block.lane,
                                blockTopMinute = topMinute,
                                blockHeightMinutes = visualHeight,
                                onClick = { onTaskClick(block.task) },
                                onMove = { onMoveTask(block.task, it) },
                                onResize = { onResizeTask(block.task, it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskTimelineCard(
    scheduled: ScheduledTask,
    laneWidth: Dp,
    lane: Int,
    blockTopMinute: Int,
    blockHeightMinutes: Int,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
    onResize: (Int) -> Unit
) {
    val density = LocalDensity.current
    var dragOffsetPx by remember(scheduled.task.id, scheduled.date, scheduled.startMinute) {
        mutableStateOf(0f)
    }
    var previewDuration by remember(scheduled.task.id, scheduled.date, scheduled.startMinute, scheduled.durationMinutes) {
        mutableStateOf(scheduled.durationMinutes)
    }

    Card(
        modifier = Modifier
            .offset(x = (laneWidth.value * lane).dp, y = blockTopMinute.dp)
            .width(laneWidth)
            .height(previewDuration.coerceAtLeast(30).dp)
            .graphicsLayer { translationY = dragOffsetPx }
            .pointerInput(scheduled.task.id, scheduled.date, scheduled.startMinute) {
                detectDragGestures(
                    onDragStart = { dragOffsetPx = 0f },
                    onDrag = { change, amount ->
                        dragOffsetPx += amount.y
                        change.consume()
                    },
                    onDragEnd = {
                        val deltaMinutes = with(density) { dragOffsetPx.toDp().value.roundToInt() }
                        dragOffsetPx = 0f
                        val newStart = snapToMinutes(
                            (scheduled.startMinute + deltaMinutes).coerceIn(
                                TIMELINE_START_MINUTE,
                                TIMELINE_END_MINUTE - scheduled.durationMinutes
                            ),
                            1
                        )
                        onMove(newStart)
                    },
                    onDragCancel = { dragOffsetPx = 0f }
                )
            },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = taskPriorityColor(scheduled.priority).copy(alpha = 0.20f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().clickable(onClick = onClick).padding(horizontal = 5.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = scheduled.task.title,
                color = AppColors.MainText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                maxLines = 2
            )
            Text(
                text = "Приоритет ${scheduled.priority}",
                color = taskPriorityColor(scheduled.priority),
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            ResizeHandle(
                pointerKey = "task_${scheduled.task.id}_${scheduled.startMinute}",
                currentDuration = scheduled.durationMinutes,
                maxDuration = TIMELINE_END_MINUTE - scheduled.startMinute,
                onPreviewResize = { previewDuration = it },
                onResize = onResize
            )
        }
    }
}

@Composable
private fun EventTimelineCard(
    occurrence: CalendarEventOccurrence,
    laneWidth: Dp,
    lane: Int,
    blockTopMinute: Int,
    blockHeightMinutes: Int,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
    onResize: (Int) -> Unit
) {
    val density = LocalDensity.current
    var dragOffsetPx by remember(occurrence.event.id, occurrence.occurrenceDate, occurrence.startMinute) {
        mutableStateOf(0f)
    }
    var previewDuration by remember(occurrence.event.id, occurrence.occurrenceDate, occurrence.startMinute, occurrence.durationMinutes) {
        mutableStateOf(occurrence.durationMinutes)
    }

    Card(
        modifier = Modifier
            .offset(x = (laneWidth.value * lane).dp, y = blockTopMinute.dp)
            .width(laneWidth)
            .height(previewDuration.coerceAtLeast(30).dp)
            .graphicsLayer { translationY = dragOffsetPx }
            .pointerInput(occurrence.event.id, occurrence.occurrenceDate, occurrence.startMinute) {
                detectDragGestures(
                    onDragStart = { dragOffsetPx = 0f },
                    onDrag = { change, amount ->
                        dragOffsetPx += amount.y
                        change.consume()
                    },
                    onDragEnd = {
                        val deltaMinutes = with(density) { dragOffsetPx.toDp().value.roundToInt() }
                        dragOffsetPx = 0f
                        val newStart = snapToMinutes(
                            (occurrence.startMinute + deltaMinutes).coerceIn(
                                0,
                                24 * 60 - occurrence.durationMinutes
                            ),
                            1
                        )
                        onMove(newStart)
                    },
                    onDragCancel = { dragOffsetPx = 0f }
                )
            },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Card)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().clickable(onClick = onClick).padding(horizontal = 5.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = occurrence.event.title,
                color = AppColors.MainText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                maxLines = 2
            )
            Text(
                text = "%02d:%02d".format(occurrence.startMinute / 60, occurrence.startMinute % 60),
                color = AppColors.SecondaryText,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            ResizeHandle(
                pointerKey = "event_${occurrence.event.id}_${occurrence.occurrenceDate}_${occurrence.startMinute}",
                currentDuration = occurrence.durationMinutes,
                maxDuration = 24 * 60 - occurrence.startMinute,
                onPreviewResize = { previewDuration = it },
                onResize = onResize
            )
        }
    }
}

@Composable
private fun ResizeHandle(
    pointerKey: String,
    currentDuration: Int,
    maxDuration: Int,
    onPreviewResize: (Int) -> Unit,
    onResize: (Int) -> Unit
) {
    val density = LocalDensity.current
    var accumulatedDragPx by remember(pointerKey) { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .pointerInput(pointerKey, currentDuration, maxDuration) {
                detectDragGestures(
                    onDragStart = { accumulatedDragPx = 0f },
                    onDrag = { change, amount ->
                        accumulatedDragPx += amount.y
                        val deltaMinutes = with(density) {
                            accumulatedDragPx.toDp().value.roundToInt()
                        }
                        val preview = (currentDuration + deltaMinutes).coerceIn(15, maxDuration)
                        onPreviewResize(preview)
                        change.consume()
                    },
                    onDragEnd = {
                        val deltaMinutes = with(density) {
                            accumulatedDragPx.toDp().value.roundToInt()
                        }
                        accumulatedDragPx = 0f
                        val newDuration = (currentDuration + deltaMinutes).coerceIn(15, maxDuration)
                        onPreviewResize(newDuration)
                        onResize(newDuration)
                    },
                    onDragCancel = {
                        accumulatedDragPx = 0f
                        onPreviewResize(currentDuration)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(3.dp)
                .background(AppColors.SecondaryText.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
        )
    }
}

private fun snapToMinutes(value: Int, step: Int): Int =
    (((value + step / 2) / step) * step)

@Composable
private fun WeekTasksContent(
    dates: List<String>,
    tasks: List<ScheduledTask>,
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.Card)
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
    onDateSelected: (String) -> Unit,
    onEventClick: (CalendarEventOccurrence) -> Unit,
    onAddEvent: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridDates = remember(anchorDate) { CalendarDateUtils.monthGridDates(anchorDate) }
    val selectedEvents = occurrences.filter { it.date == anchorDate }

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
            modifier = Modifier.fillMaxWidth().height(250.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(gridDates, key = { it }) { date ->
                val dayEvents = occurrences.count { it.date == date }
                val isSelected = date == anchorDate
                val isInMonth = date.take(7) == anchorDate.take(7)
                Card(
                    modifier = Modifier.fillMaxWidth().height(48.dp).clickable { onDateSelected(date) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) AppColors.Accent.copy(alpha = 0.28f) else AppColors.Card
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
                        if (dayEvents > 0) {
                            Text("• $dayEvents", color = AppColors.Accent, fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        Text(CalendarDateUtils.formatLongDate(anchorDate), color = AppColors.MainText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (selectedEvents.isEmpty()) {
            Text("На этот день мероприятий нет.", color = AppColors.SecondaryText, fontSize = 13.sp)
        } else {
            selectedEvents.forEach { occurrence ->
                EventListCard(occurrence = occurrence, onClick = { onEventClick(occurrence) })
            }
        }
        TextButton(onClick = { onAddEvent(anchorDate, 9 * 60) }) {
            Text("＋ Добавить мероприятие на этот день", color = AppColors.Accent)
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
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = AppColors.Card)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(occurrence.event.title, color = AppColors.MainText, fontWeight = FontWeight.SemiBold)
            Text(
                text = "%02d:%02d · %d мин.".format(
                    occurrence.startMinute / 60,
                    occurrence.startMinute % 60,
                    occurrence.durationMinutes
                ),
                color = AppColors.SecondaryText,
                fontSize = 12.sp
            )
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

