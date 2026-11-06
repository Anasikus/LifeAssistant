
package com.anasikus.lifeassistant.ui.components

import android.app.DatePickerDialog
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
    onSave: (String, String, String, String) -> Unit,
    initialDate: String = TaskRules.today(),
    creationHeader: (@Composable () -> Unit)? = null
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

                        else -> {
                            onSave(
                                title.trim(),
                                description.trim(),
                                startDate,
                                deadline
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
