
package com.anasikus.lifeassistant.ui.components

import com.anasikus.lifeassistant.settings.AppText
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
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
import com.anasikus.lifeassistant.data.TaskGroupEntity
import com.anasikus.lifeassistant.domain.TaskRules
import com.anasikus.lifeassistant.ui.AppColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun TaskEditorDialog(
    task: TaskEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Int, Int, Int?, Int) -> Unit,
    groups: List<TaskGroupEntity> = emptyList(),
    initialGroupId: Int? = null,
    projectTotal: Int? = null,
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

    var hours by remember(task?.id) { mutableStateOf(((projectTotal ?: task?.estimatedDurationMinutes ?: 60) / 60).toString()) }
    var minutes by remember(task?.id) { mutableStateOf(((projectTotal ?: task?.estimatedDurationMinutes ?: 60) % 60).toString()) }
    var priority by remember(task?.id) { mutableStateOf(task?.priorityLevel?.coerceIn(0, 2) ?: 1) }
    var priorityMenu by remember { mutableStateOf(false) }
    var durationError by remember { mutableStateOf(false) }
    val h = hours.toIntOrNull()
    val m = minutes.toIntOrNull()
    val duration = if (h != null && m != null && h in 0..10000 && m in 0..59) h * 60 + m else null

    var group by remember(task?.id) { mutableStateOf(task?.groupId ?: initialGroupId) }
    var spentHours by remember(task?.id) { mutableStateOf(((task?.spentMinutes ?: 0) / 60).toString()) }
    var spentMinutes by remember(task?.id) { mutableStateOf(((task?.spentMinutes ?: 0) % 60).toString()) }
    val spent = spentHours.toIntOrNull()?.takeIf { it in 0..10000 }?.let { hours ->
        spentMinutes.toIntOrNull()?.takeIf { it in 0..59 }?.let { hours * 60 + it }
    }
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

    val draft = listOf(title, description, startDate, deadline, hours, minutes, priority, group, spentHours, spentMinutes)
    val originalDraft = remember { draft }
    fun save() {
                    when {
                        title.isBlank() -> {
                            titleError = true
                        }

                        deadline < startDate -> {
                            dateError = true
                        }

                        duration == null || duration !in 15..600000 || spent == null || spent !in 0..duration -> {
                            durationError = true
                        }

                        else -> {
                            onSave(
                                title.trim(),
                                description.trim(),
                                startDate,
                                deadline,
                                checkNotNull(duration),
                                priority, group, checkNotNull(spent)
                            )
                        }
                    }

    }
    val dismiss = rememberDismissGuard(draft != originalDraft, ::save, onDismiss)
    val switchDrafts = remember { FormDraftRegistry() }
    SideEffect { switchDrafts.dirty = draft != originalDraft; switchDrafts.save = { save() } }
    val switchForm = rememberGuardedAction(switchDrafts)

    AlertDialog(
        onDismissRequest = dismiss,
        containerColor = AppColors.Card,
        titleContentColor = AppColors.MainText,
        textContentColor = AppColors.SecondaryText,
        title = {
            if (creationHeader != null) CompositionLocalProvider(LocalFormSwitch provides switchForm) { creationHeader() } else Text(
                text = if (task == null) {
                    AppText.get("Новая задача")
                } else {
                    AppText.get("Редактирование задачи")
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
                    label = { Text(AppText.get("Название")) },
                    singleLine = true,
                    isError = titleError,
                    supportingText = {
                        if (titleError) {
                            Text(AppText.get("Введите название задачи"))
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
                    label = { Text(AppText.get("Описание")) },
                    minLines = 3,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    colors = fieldColors
                )

                Text(AppText.get("Примерное время на задачу"), color = AppColors.MainText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(hours, { hours = it; durationError = false }, Modifier.weight(1f),
                        label = { Text(AppText.get("Часы")) }, enabled = task?.isProject != true, singleLine = true, colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = durationError)
                    OutlinedTextField(minutes, { minutes = it; durationError = false }, Modifier.weight(1f),
                        label = { Text(AppText.get("Минуты")) }, enabled = task?.isProject != true, singleLine = true, colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = durationError)
                }
                Text(AppText.get("Общий объём: от 15 минут до 10 000 часов. Задача разбивается на свободные окна календаря."),
                    color = AppColors.SecondaryText, fontSize = 12.sp)
                if (durationError) Text(AppText.get("Проверь общий объём и потраченное время. Потрачено не может превышать общий объём."), color = AppColors.Red)
                ChoiceField(AppText.get("Группа"), groups.find { it.id == group }?.name ?: AppText.get("Без группы"),
                    listOf("" to AppText.get("Без группы")) + groups.map { it.id.toString() to it.name }) { group = it.toIntOrNull() }
                if (task?.isProject == true) Text(AppText.get("Общий объём проекта складывается из подзадач. Увеличить время можно в нужной подзадаче."), color = AppColors.SecondaryText)
                else {
                    Text(AppText.get("Уже потрачено (можно исправить)"), color = AppColors.MainText)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(spentHours, { spentHours = it.filter(Char::isDigit).take(5); durationError = false }, Modifier.weight(1f),
                            label = { Text(AppText.get("Часы")) }, singleLine = true, colors = fieldColors, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(spentMinutes, { spentMinutes = it.filter(Char::isDigit).take(2); durationError = false }, Modifier.weight(1f),
                            label = { Text(AppText.get("Минуты")) }, singleLine = true, colors = fieldColors, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    Text(AppText.get("Завершённые окна учитываются автоматически по расписанию. Если в это время ты не работала, исправь значение вручную."), color = AppColors.SecondaryText)
                }
                Box {
                    OutlinedButton(onClick = { priorityMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(AppText.get("Приоритет: {arg0} ▾", TaskRules.priorityLabel(priority)), color = AppColors.Accent)
                    }
                    DropdownMenu(priorityMenu, onDismissRequest = { priorityMenu = false }) {
                        (0..2).forEach { level ->
                            DropdownMenuItem(text = { Text(TaskRules.priorityLabel(level)) },
                                onClick = { priority = level; priorityMenu = false })
                        }
                    }
                }
                Text(AppText.get("Просроченные задачи и задачи со сроком в ближайшие 3 дня всегда выше остальных."),
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
                        text = AppText.get("Дата начала: {arg0}", TaskRules.formatDate(startDate)),
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
                        text = AppText.get("Дедлайн: {arg0}", TaskRules.formatDate(deadline)),
                        color = AppColors.Accent
                    )
                }

                if (dateError) {
                    Text(
                        text = AppText.get("Дедлайн не может быть раньше даты начала."),
                        color = AppColors.Red,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = ::save
            ) {
                Text(AppText.get("Сохранить"), color = AppColors.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = dismiss) {
                Text(AppText.get("Отмена"), color = AppColors.SecondaryText)
            }
        }
    )
}
