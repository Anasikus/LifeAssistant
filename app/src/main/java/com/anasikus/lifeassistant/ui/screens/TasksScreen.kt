
package com.anasikus.lifeassistant.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anasikus.lifeassistant.data.TaskEntity
import com.anasikus.lifeassistant.domain.TaskRules
import com.anasikus.lifeassistant.ui.AppColors
import com.anasikus.lifeassistant.ui.components.TaskEditorDialog
import com.anasikus.lifeassistant.viewmodel.TaskViewModel

private fun statusColor(task: TaskEntity): Color {
    return when (TaskRules.status(task)) {
        "Выполнен" -> AppColors.Green
        "Просрочен" -> AppColors.Red
        "Выполнен с опозданием" -> AppColors.LatePurple
        else -> AppColors.Yellow
    }
}

@Composable
fun TasksScreen(
    viewModel: TaskViewModel,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsState()

    var showEditor by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var selectedTask by remember { mutableStateOf<TaskEntity?>(null) }
    var taskToDelete by remember { mutableStateOf<TaskEntity?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.initializeSampleTasks()
    }

    val completedCount = tasks.count { it.completed }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = AppColors.Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 20.dp,
                    vertical = 24.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "ТВОЙ ЛИЧНЫЙ АССИСТЕНТ",
                    color = AppColors.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Привет! ✨",
                    color = AppColors.MainText,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Давай сделаем этот день лучше.",
                    color = AppColors.SecondaryText,
                    fontSize = 15.sp
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = AppColors.Card
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ТВОЙ ПРОГРЕСС",
                        color = AppColors.SecondaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "$completedCount из ${tasks.size} задач",
                        color = AppColors.MainText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (
                            tasks.isNotEmpty() &&
                            completedCount == tasks.size
                        ) {
                            "Все задачи выполнены! 🎉"
                        } else {
                            "Маленькие шаги ведут к большим целям."
                        },
                        color = AppColors.SecondaryText,
                        fontSize = 14.sp
                    )
                }
            }

            Text(
                text = "План на сегодня",
                color = AppColors.MainText,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = tasks,
                    key = { task -> task.id }
                ) { task ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedTask = task
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = AppColors.Card
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = task.completed,
                                    onCheckedChange = { checked ->
                                        viewModel.setCompleted(task, checked)
                                    }
                                )

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = task.title,
                                        color = AppColors.MainText,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    if (task.description.isNotBlank()) {
                                        Text(
                                            text = task.description,
                                            color = AppColors.SecondaryText,
                                            fontSize = 13.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Начало: ${
                                    TaskRules.formatDate(task.startDate)
                                }  •  Дедлайн: ${
                                    TaskRules.formatDate(task.deadline)
                                }",
                                color = AppColors.SecondaryText,
                                fontSize = 12.sp
                            )

                            Text(
                                text = TaskRules.status(task),
                                color = statusColor(task),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    editingTask = null
                    showEditor = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("＋ Добавить задачу")
            }
        }
    }

    // Используем редактор из отдельного файла.
    if (showEditor) {
        TaskEditorDialog(
            task = editingTask,
            onDismiss = {
                showEditor = false
            },
            onSave = { title, description, startDate, deadline ->
                val existing = editingTask

                if (existing == null) {
                    viewModel.addTask(
                        TaskEntity(
                            title = title,
                            description = description,
                            startDate = startDate,
                            deadline = deadline
                        )
                    )
                } else {
                    viewModel.updateTask(
                        existing.copy(
                            title = title,
                            description = description,
                            startDate = startDate,
                            deadline = deadline
                        )
                    )
                }

                showEditor = false
            }
        )
    }

    // Просмотр подробностей задачи.
    selectedTask?.let { task ->
        AlertDialog(
            onDismissRequest = {
                selectedTask = null
            },
            containerColor = AppColors.Card,
            titleContentColor = AppColors.MainText,
            textContentColor = AppColors.SecondaryText,
            title = {
                Text(task.title)
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        task.description.ifBlank {
                            "Описание не добавлено."
                        }
                    )

                    Text(
                        "Дата начала: ${
                            TaskRules.formatDate(task.startDate)
                        }"
                    )

                    Text(
                        "Дата окончания: ${
                            TaskRules.formatDate(task.deadline)
                        }"
                    )

                    Text(
                        text = "Статус: ${TaskRules.status(task)}",
                        color = statusColor(task),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editingTask = task
                        selectedTask = null
                        showEditor = true
                    }
                ) {
                    Text(
                        text = "Редактировать",
                        color = AppColors.Accent
                    )
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            taskToDelete = task
                            selectedTask = null
                        }
                    ) {
                        Text(
                            text = "Удалить",
                            color = AppColors.Red
                        )
                    }

                    TextButton(
                        onClick = {
                            selectedTask = null
                        }
                    ) {
                        Text(
                            text = "Закрыть",
                            color = AppColors.SecondaryText
                        )
                    }
                }
            }
        )
    }

    // Подтверждение удаления.
    taskToDelete?.let { task ->
        AlertDialog(
            onDismissRequest = {
                taskToDelete = null
            },
            containerColor = AppColors.Card,
            title = {
                Text(
                    text = "Удалить задачу?",
                    color = AppColors.MainText
                )
            },
            text = {
                Text(
                    text = "Задача «${task.title}» будет удалена без возможности восстановления.",
                    color = AppColors.SecondaryText
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTask(task)
                        taskToDelete = null
                    }
                ) {
                    Text(
                        text = "Удалить",
                        color = AppColors.Red
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        taskToDelete = null
                    }
                ) {
                    Text(
                        text = "Отмена",
                        color = AppColors.SecondaryText
                    )
                }
            }
        )
    }
}
