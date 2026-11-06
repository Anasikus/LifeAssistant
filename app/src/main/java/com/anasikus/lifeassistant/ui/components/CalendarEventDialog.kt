package com.anasikus.lifeassistant.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anasikus.lifeassistant.data.CalendarEventEntity
import com.anasikus.lifeassistant.data.EventCategoryEntity
import com.anasikus.lifeassistant.domain.CalendarDisplay
import com.anasikus.lifeassistant.domain.WorkPay
import com.anasikus.lifeassistant.domain.ShiftRules
import com.anasikus.lifeassistant.domain.CalendarDateUtils
import com.anasikus.lifeassistant.ui.AppColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val calendarWeekDays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

@Composable
fun CalendarEventDialog(
    event: CalendarEventEntity?,
    initialDate: String,
    initialStartMinute: Int,
    initialDurationMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        description: String,
        date: String,
        startMinute: Int,
        durationMinutes: Int,
        repeatDaysMask: Int,
        categoryId: Int?,
        amountCents: Long?,
        payMode: WorkPay
    ) -> Unit,
    onDeleteOccurrence: (() -> Unit)? = null,
    onDeleteAll: (() -> Unit)? = null,
    categories: List<EventCategoryEntity> = emptyList(),
    defaultHourlyRateCents: Long = 0,
    initialCategoryId: Int? = event?.categoryId,
    requireCategory: Boolean = false,
    editSeries: Boolean = false,
    creationHeader: (@Composable () -> Unit)? = null,
    initialPayMode: String? = event?.payMode,
    initialPayAmount: Long? = event?.payAmountCents
) {
    val context = LocalContext.current

    var title by remember(event?.id, initialDate) { mutableStateOf(event?.title.orEmpty()) }
    var description by remember(event?.id, initialDate) { mutableStateOf(event?.description.orEmpty()) }
    var date by remember(event?.id, initialDate) { mutableStateOf(initialDate) }
    var startMinute by remember(event?.id, initialStartMinute) {
        mutableStateOf(initialStartMinute)
    }
    var durationMinutes by remember(event?.id, initialDate, initialDurationMinutes) {
        mutableStateOf(initialDurationMinutes)
    }
    var timeError by remember(event?.id, initialDate) { mutableStateOf<String?>(null) }
    var repeatDaysMask by remember(event?.id) {
        mutableStateOf(event?.repeatDaysMask ?: 0)
    }
    var categoryId by remember(event?.id) { mutableStateOf(initialCategoryId) }
    var categoryMenu by remember { mutableStateOf(false) }
    val selectedCategory = categories.find { it.id == categoryId }
    var payMode by remember(event?.id, initialPayMode) { mutableStateOf(WorkPay.from(initialPayMode)) }
    var payMenu by remember { mutableStateOf(false) }
    var amount by remember(event?.id, selectedCategory?.kind, initialPayAmount) {
        mutableStateOf(java.math.BigDecimal.valueOf(if (selectedCategory?.kind == "WORK") (initialPayAmount ?: event?.hourlyRateCents ?: defaultHourlyRateCents) else event?.oneTimePaymentCents ?: 0, 2).toPlainString())
    }
    var titleError by remember(event?.id) { mutableStateOf(false) }
    var showDeleteOptions by remember(event?.id) { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = AppColors.MainText,
        unfocusedTextColor = AppColors.MainText,
        cursorColor = AppColors.Accent,
        focusedBorderColor = AppColors.Accent,
        unfocusedBorderColor = AppColors.SecondaryText,
        focusedLabelColor = AppColors.Accent,
        unfocusedLabelColor = AppColors.SecondaryText,
        errorBorderColor = AppColors.Red,
        errorLabelColor = AppColors.Red
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Card,
        titleContentColor = AppColors.MainText,
        textContentColor = AppColors.SecondaryText,
        title = {
            if (creationHeader != null) creationHeader() else Text(
                text = if (event == null) "Новое мероприятие" else "Редактирование мероприятия",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Название") },
                    singleLine = true,
                    isError = titleError,
                    colors = fieldColors
                )
                if (titleError) {
                    Text("Введите название мероприятия.", color = AppColors.Red, fontSize = 12.sp)
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Описание") },
                    minLines = 2,
                    maxLines = 4,
                    colors = fieldColors
                )

                Box {
                    OutlinedButton(onClick = { categoryMenu = true }) { Text("Категория: ${selectedCategory?.name ?: "Без категории"}", color = AppColors.MainText) }
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        if (!requireCategory) DropdownMenuItem(text = { Text("Без категории") }, onClick = { categoryId = null; categoryMenu = false })
                        categories.forEach { category ->
                            DropdownMenuItem(text = { Text(category.name) }, onClick = { categoryId = category.id; categoryMenu = false })
                        }
                    }
                }
                if (selectedCategory?.kind == "WORK") {
                    Box {
                        OutlinedButton(onClick = { payMenu = true }) {
                            Text(payMode.label, color = AppColors.MainText)
                        }
                        DropdownMenu(expanded = payMenu, onDismissRequest = { payMenu = false }) {
                            WorkPay.entries.forEach { mode ->
                                DropdownMenuItem(text = { Text(mode.label) }, onClick = { payMode = mode; payMenu = false })
                            }
                        }
                    }
                    if (payMode == WorkPay.ADVANCE) {
                        Text("Плановая сумма аванса. Получение отметь в «Финансы → Доход».", color = AppColors.SecondaryText)
                    }
                    if (payMode == WorkPay.SALARY) {
                        Text("Укажи зарплату за месяц. Она начисляется по доле завершённых смен месяца.", color = AppColors.SecondaryText)
                    }
                    if (!editSeries && (event?.repeatDaysMask ?: 0) != 0) {
                        Text("Изменение оплаты здесь относится к выбранной смене.", color = AppColors.SecondaryText)
                    }
                }
                if (selectedCategory?.kind in listOf("WORK", "SIDE_JOB")) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (selectedCategory?.kind == "WORK") "${payMode.label}, ₽" else "Разовая выплата, ₽") },
                        colors = fieldColors,
                        singleLine = true
                    )
                }

                if (selectedCategory?.kind == "WORK") {
                    val cents = ShiftRules.cents(amount)
                    val shiftTotal = when (payMode) {
                        WorkPay.HOURLY -> cents?.let {
                            ShiftRules.payment(com.anasikus.lifeassistant.data.ShiftDayEntity(
                                date = date, status = "WORK", durationMinutes = durationMinutes, hourlyRateCents = it
                            ))
                        }
                        WorkPay.SHIFT -> cents
                        WorkPay.ADVANCE, WorkPay.SALARY -> null
                    }
                    shiftTotal?.let { Text("За эту смену · ${ShiftRules.money(it)}", color = AppColors.MainText) }
                }

                if (selectedCategory?.kind == "HOLIDAY") {
                    Text("Ежегодные дни рождения и годовщины добавляй в «Категории и праздники → Праздники». Здесь создаётся мероприятие со временем.", color = AppColors.SecondaryText, fontSize = 11.sp)
                }
                if (selectedCategory?.kind == "SIDE_JOB" && repeatDaysMask != 0) {
                    Text("Выплата планируется за каждое повторение подработки.", color = AppColors.SecondaryText, fontSize = 11.sp)
                }

                TextButton(
                    onClick = {
                        val calendar = CalendarDateUtils.parseCalendar(date)
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                val selected = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, day)
                                    set(Calendar.HOUR_OF_DAY, 12)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selected.time)
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                ) {
                    Text("Дата: ${CalendarDateUtils.formatDate(date)}", color = AppColors.Accent)
                }

                TimePickerField(
                    label = "Начало (с)",
                    minute = startMinute,
                    onTimeSelected = {
                        startMinute = it
                        timeError = null
                    }
                )
                TimePickerField(
                    label = "Окончание (до)",
                    minute = startMinute + durationMinutes,
                    onTimeSelected = { end ->
                        val selectedDuration = CalendarDisplay.endToDuration(startMinute, end)
                        if (selectedDuration >= 15) {
                            durationMinutes = selectedDuration
                            timeError = null
                        } else {
                            timeError = "Окончание должно быть хотя бы на 15 минут позже начала."
                        }
                    }
                )
                Text(CalendarDisplay.interval(startMinute, durationMinutes), color = AppColors.MainText)
                Text("Если окончание раньше начала, оно относится к следующему дню. Одинаковое время означает 24 часа.", color = AppColors.SecondaryText, fontSize = 11.sp)
                timeError?.let { Text(it, color = AppColors.Red, fontSize = 12.sp) }
                if (!editSeries && event != null && event.repeatDaysMask != 0) {
                    Text(
                        "Дата и время меняются для выбранного повторения. Название, описание и дни недели — для всей серии.",
                        color = AppColors.SecondaryText,
                        fontSize = 11.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Длительность", color = AppColors.MainText)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                durationMinutes = (durationMinutes - 15).coerceAtLeast(15)
                                timeError = null
                            }
                        ) { Text("−", color = AppColors.Accent) }
                        Text(
                            text = if (durationMinutes < 60) "$durationMinutes мин." else {
                                val hours = durationMinutes / 60
                                val minutes = durationMinutes % 60
                                if (minutes == 0) "$hours ч." else "$hours ч. $minutes мин."
                            },
                            color = AppColors.MainText
                        )
                        TextButton(
                            onClick = {
                                durationMinutes = (durationMinutes + 15).coerceAtMost(1440)
                                timeError = null
                            }
                        ) { Text("+", color = AppColors.Accent) }
                    }
                }

                Text(
                    text = "Повторять по дням недели",
                    color = AppColors.MainText,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Без выбранных дней мероприятие будет одноразовым.",
                    color = AppColors.SecondaryText,
                    fontSize = 11.sp
                )

                TextButton(onClick = { repeatDaysMask = if (repeatDaysMask == 127) 0 else 127 }) {
                    Text(if (repeatDaysMask == 127) "Каждый день ✓" else "Повторять каждый день", color = AppColors.Accent)
                }
                calendarWeekDays.indices.toList().chunked(2).forEach { indexes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        indexes.forEach { index ->
                            val bit = 1 shl index
                            val checked = (repeatDaysMask and bit) != 0
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        repeatDaysMask = if (isChecked) {
                                            repeatDaysMask or bit
                                        } else {
                                            repeatDaysMask and bit.inv()
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = AppColors.Accent,
                                        uncheckedColor = AppColors.SecondaryText,
                                        checkmarkColor = Color.White
                                    )
                                )
                                Text(
                                    text = calendarWeekDays[index],
                                    color = AppColors.MainText,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isBlank()) {
                        titleError = true
                    } else if (durationMinutes !in 15..1440) {
                        timeError = "Выбери длительность от 15 минут до 24 часов."
                    } else if (requireCategory && selectedCategory == null) {
                        timeError = "Выбери категорию работы или подработки."
                    } else if (selectedCategory?.kind in listOf("WORK", "SIDE_JOB") && ShiftRules.cents(amount) == null) {
                        timeError = "Введи неотрицательную сумму с точностью до копеек."
                    } else {
                        onSave(
                            title.trim(),
                            description.trim(),
                            date,
                            startMinute,
                            durationMinutes,
                            repeatDaysMask,
                            categoryId,
                            if (selectedCategory?.kind in listOf("WORK", "SIDE_JOB")) ShiftRules.cents(amount) else null,
                            payMode
                        )
                    }
                }
            ) {
                Text("Сохранить", color = AppColors.Accent)
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (event != null && (editSeries || onDeleteOccurrence != null) && onDeleteAll != null) {
                    TextButton(onClick = { showDeleteOptions = true }) {
                        Text("Удалить", color = AppColors.Red)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Отмена", color = AppColors.SecondaryText)
                }
            }
        }
    )

    if (showDeleteOptions && event != null && (editSeries || onDeleteOccurrence != null) && onDeleteAll != null) {
        AlertDialog(
            onDismissRequest = { showDeleteOptions = false },
            containerColor = AppColors.Card,
            title = { Text("Удаление мероприятия", color = AppColors.MainText) },
            text = {
                Text(
                    text = if (event.repeatDaysMask == 0) {
                        "Удалить это мероприятие?"
                    } else if (editSeries) {
                        "Удалить всю серию смен?"
                    } else {
                        "Удалить только выбранный день или все повторения?"
                    },
                    color = AppColors.SecondaryText
                )
            },
            confirmButton = {
                TextButton(onClick = { if (editSeries) onDeleteAll() else onDeleteOccurrence?.invoke() }) {
                    Text(
                        text = if (editSeries || event.repeatDaysMask == 0) "Удалить" else "Только этот день",
                        color = AppColors.Accent
                    )
                }
            },
            dismissButton = {
                Row {
                    if (!editSeries && event.repeatDaysMask != 0) {
                        TextButton(onClick = onDeleteAll) {
                            Text("Всю серию", color = AppColors.Red)
                        }
                    }
                    TextButton(onClick = { showDeleteOptions = false }) {
                        Text("Отмена", color = AppColors.SecondaryText)
                    }
                }
            }
        )
    }
}
