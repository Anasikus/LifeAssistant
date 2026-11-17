
package com.anasikus.lifeassistant.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anasikus.lifeassistant.data.TaskEntity
import com.anasikus.lifeassistant.domain.TaskRules
import com.anasikus.lifeassistant.ui.AppColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun TaskEditorDialog(
    task: TaskEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Int, Int) -> Unit,
    initialDate: String = TaskRules.today(),
    creationHeader: (@Composable () -> Unit)? = null,
    error: String? = null
) {
    var title by remember(task?.id) {
        mutableStateOf(task?.title.orEmpty())
    }

    var description by remember(task?.id) {
        mutableStateOf(task?.description.orEmpty())
    }

    var startDate by remember(task?.id) {
        mutableStateOf(
            task?.startDate?.takeIf { it.isNotBlank() }
                ?: initialDate
        )
    }

    var deadline by remember(task?.id) {
        mutableStateOf(
            task?.deadline?.takeIf { it.isNotBlank() }
                ?: initialDate
        )
    }

    var hours by remember(task?.id) { mutableStateOf(((task?.estimatedDurationMinutes ?: 60) / 60).toString()) }
    var minutes by remember(task?.id) { mutableStateOf(((task?.estimatedDurationMinutes ?: 60) % 60).toString()) }
    var priority by remember(task?.id) { mutableStateOf(task?.priorityLevel?.coerceIn(0, 2) ?: 1) }
    var priorityMenu by remember { mutableStateOf(false) }
    var durationError by remember { mutableStateOf(false) }
    val h = hours.toIntOrNull()
    val m = minutes.toIntOrNull()
    val duration = if (h != null && m != null && h in 0..24 && m in 0..59) h * 60 + m else null

    var titleError by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf(false) }

    val context = LocalContext.current

    fun showDatePicker(
        currentDate: String,
        minimumDate: String? = null,
        onDateSelected: (String) -> Unit
    ) {
        val calendar = TaskRules.parseCalendar(currentDate)

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

                val formatted = SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.US
                ).format(selected.time)

                onDateSelected(formatted)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            // Запрещаем выбирать дедлайн раньше начала задачи.
            minimumDate?.let { minDate ->
                val minimumCalendar = TaskRules.parseCalendar(minDate)

                minimumCalendar.set(Calendar.HOUR_OF_DAY, 0)
                minimumCalendar.set(Calendar.MINUTE, 0)
                minimumCalendar.set(Calendar.SECOND, 0)
                minimumCalendar.set(Calendar.MILLISECOND, 0)

                datePicker.minDate = minimumCalendar.timeInMillis
            }
        }.show()
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = AppColors.MainText,
        unfocusedTextColor = AppColors.MainText,
        disabledTextColor = AppColors.SecondaryText,
        cursorColor = AppColors.Accent,
        focusedBorderColor = AppColors.Accent,
        unfocusedBorderColor = AppColors.SecondaryText,
        focusedLabelColor = AppColors.Accent,
        unfocusedLabelColor = AppColors.SecondaryText,
        focusedPlaceholderColor = AppColors.SecondaryText,
        unfocusedPlaceholderColor = AppColors.SecondaryText,
        errorTextColor = AppColors.Red,
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
                text = if (task == null) {
                    "Новая задача"
                } else {
                    "Редактирование задачи"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    supportingText = {
                        if (titleError) {
                            Text("Введите название задачи")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    colors = fieldColors
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Описание") },
                    minLines = 3,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    colors = fieldColors
                )

                Text("Примерное время на задачу", color = AppColors.MainText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(hours, { hours = it; durationError = false }, Modifier.weight(1f),
                        label = { Text("Часы") }, singleLine = true, colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = durationError)
                    OutlinedTextField(minutes, { minutes = it; durationError = false }, Modifier.weight(1f),
                        label = { Text("Минуты") }, singleLine = true, colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = durationError)
                }
                Text("От 15 минут до 24 часов. Оценку можно менять; длительность в календаре тоже редактируется.",
                    color = AppColors.SecondaryText, fontSize = 12.sp)
                if (durationError) Text("Укажи время от 15 минут до 24 часов; минуты — от 0 до 59.", color = AppColors.Red)
                if (duration != null && duration > 16 * 60) Text("Задачу длиннее 16 часов нужно разместить вручную: автоматические окна — с 07:00 до 23:00.",
                    color = AppColors.Yellow, fontSize = 12.sp)
                Box {
                    OutlinedButton(onClick = { priorityMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Приоритет: ${TaskRules.priorityLabel(priority)} ▾", color = AppColors.Accent)
                    }
                    DropdownMenu(priorityMenu, onDismissRequest = { priorityMenu = false }) {
                        (0..2).forEach { level ->
                            DropdownMenuItem(text = { Text(TaskRules.priorityLabel(level)) },
                                onClick = { priority = level; priorityMenu = false })
                        }
                    }
                }
                Text("Просроченные задачи и задачи со сроком в ближайшие 3 дня всегда выше остальных.",
                    color = AppColors.SecondaryText, fontSize = 12.sp)
                error?.let { Text(it, color = AppColors.Red) }

                TextButton(
                    onClick = {
                        showDatePicker(startDate) { selectedDate ->
                            startDate = selectedDate

                            // Если новый старт позже дедлайна,
                            // автоматически переносим дедлайн.
                            if (deadline < selectedDate) {
                                deadline = selectedDate
                            }

                            dateError = false
                        }
                    }
                ) {
                    Text(
                        text = "Дата начала: ${TaskRules.formatDate(startDate)}",
                        color = AppColors.Accent
                    )
                }

                TextButton(
                    onClick = {
                        showDatePicker(
                            currentDate = deadline,
                            minimumDate = startDate
                        ) { selectedDate ->
                            deadline = selectedDate
                            dateError = selectedDate < startDate
                        }
                    }
                ) {
                    Text(
                        text = "Дедлайн: ${TaskRules.formatDate(deadline)}",
                        color = AppColors.Accent
                    )
                }

                if (dateError) {
                    Text(
                        text = "Дедлайн не может быть раньше даты начала.",
                        color = AppColors.Red,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        title.isBlank() -> {
                            titleError = true
                        }

                        deadline < startDate -> {
                            dateError = true
                        }

                        duration == null || duration !in 15..1440 -> {
                            durationError = true
                        }

                        else -> {
                            onSave(
                                title.trim(),
                                description.trim(),
                                startDate,
                                deadline,
                                checkNotNull(duration),
                                priority
                            )
                        }
                    }
                }
            ) {
                Text("Сохранить", color = AppColors.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = AppColors.SecondaryText)
            }
        }
    )
}
