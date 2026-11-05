package com.anasikus.lifeassistant.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        description: String,
        date: String,
        startMinute: Int,
        durationMinutes: Int,
        repeatDaysMask: Int
    ) -> Unit,
    onDeleteOccurrence: (() -> Unit)? = null,
    onDeleteAll: (() -> Unit)? = null
) {
    val context = LocalContext.current

    var title by remember(event?.id, initialDate) { mutableStateOf(event?.title.orEmpty()) }
    var description by remember(event?.id, initialDate) { mutableStateOf(event?.description.orEmpty()) }
    var date by remember(event?.id, initialDate) { mutableStateOf(event?.startDate ?: initialDate) }
    var startMinute by remember(event?.id, initialStartMinute) {
        mutableStateOf(event?.startMinute ?: initialStartMinute)
    }
    var durationMinutes by remember(event?.id) {
        mutableStateOf(event?.durationMinutes ?: 60)
    }
    var repeatDaysMask by remember(event?.id) {
        mutableStateOf(event?.repeatDaysMask ?: 0)
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
            Text(
                text = if (event == null) "Новое мероприятие" else "Редактирование мероприятия",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

                TextButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> startMinute = hour * 60 + minute },
                            startMinute / 60,
                            startMinute % 60,
                            true
                        ).show()
                    }
                ) {
                    Text(
                        text = "Начало: %02d:%02d".format(startMinute / 60, startMinute % 60),
                        color = AppColors.Accent
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
                            onClick = { durationMinutes = (durationMinutes - 15).coerceAtLeast(15) }
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
                            onClick = { durationMinutes = (durationMinutes + 15).coerceAtMost(480) }
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

                calendarWeekDays.indices.toList().chunked(4).forEach { indexes ->
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
                    } else {
                        onSave(
                            title.trim(),
                            description.trim(),
                            date,
                            startMinute,
                            durationMinutes,
                            repeatDaysMask
                        )
                    }
                }
            ) {
                Text("Сохранить", color = AppColors.Accent)
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (event != null && onDeleteOccurrence != null && onDeleteAll != null) {
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

    if (showDeleteOptions && event != null && onDeleteOccurrence != null && onDeleteAll != null) {
        AlertDialog(
            onDismissRequest = { showDeleteOptions = false },
            containerColor = AppColors.Card,
            title = { Text("Удаление мероприятия", color = AppColors.MainText) },
            text = {
                Text(
                    text = if (event.repeatDaysMask == 0) {
                        "Удалить это мероприятие?"
                    } else {
                        "Удалить только выбранный день или все повторения?"
                    },
                    color = AppColors.SecondaryText
                )
            },
            confirmButton = {
                TextButton(onClick = onDeleteOccurrence) {
                    Text(
                        text = if (event.repeatDaysMask == 0) "Удалить" else "Только этот день",
                        color = AppColors.Accent
                    )
                }
            },
            dismissButton = {
                Row {
                    if (event.repeatDaysMask != 0) {
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
