package com.anasikus.lifeassistant.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anasikus.lifeassistant.data.TaskEntity
import com.anasikus.lifeassistant.domain.TaskRules
import com.anasikus.lifeassistant.ui.AppColors
import com.anasikus.lifeassistant.ui.components.TaskEditorDialog
import com.anasikus.lifeassistant.ui.components.TaskStatisticsDialog
import com.anasikus.lifeassistant.viewmodel.TaskViewModel

private fun taskStatusColor(task: TaskEntity): androidx.compose.ui.graphics.Color = when {
    task.completed -> AppColors.Green
    TaskRules.isOverdue(task) -> AppColors.Red
    TaskRules.isDueSoon(task) -> AppColors.Yellow
    else -> AppColors.SecondaryText
}

@Composable
fun TasksScreen(viewModel: TaskViewModel, modifier: Modifier = Modifier) {
    val all by viewModel.allTasks.collectAsStateWithLifecycle()
    val today by viewModel.currentDate.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var showArchive by rememberSaveable { mutableStateOf(false) }
    var showStatistics by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var deleting by remember { mutableStateOf<TaskEntity?>(null) }
    val active = remember(all, today) { all.filterNot { it.archived }.sortedWith(TaskRules.comparator(today)) }
    val archived = remember(all) { all.filter { it.archived }.sortedByDescending { it.id } }
    val shown = if (showArchive) archived else active
    val doneToday = all.count { it.completed && it.completedAt == today }

    MaterialTheme(colorScheme = darkColorScheme(primary = AppColors.Accent, surface = AppColors.Card,
        onSurface = AppColors.MainText, onSurfaceVariant = AppColors.SecondaryText)) {
        Surface(modifier.fillMaxSize(), color = AppColors.Background) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Задачи", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = { showStatistics = true }) { Text("Статистика") }
                }
                Text("Выполнено сегодня: $doneToday · В работе: ${active.count { !it.completed }}",
                    color = AppColors.SecondaryText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!showArchive, onClick = { showArchive = false }, label = { Text("Активные (${active.size})") })
                    FilterChip(showArchive, onClick = { showArchive = true }, label = { Text("Архив (${archived.size})") })
                }
                error?.let { Text(it, color = AppColors.Red) }
                Text(if (showArchive) "Из архива можно восстановить или удалить задачу."
                    else "Просроченные → ближайшие 3 дня → остальные. Приоритет действует внутри группы. Выполненные задачи уходят в архив на следующий день.",
                    style = MaterialTheme.typography.bodySmall, color = AppColors.SecondaryText)
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (shown.isEmpty()) item {
                        Text(if (showArchive) "Архив пока пуст" else "Задач пока нет", color = AppColors.SecondaryText)
                    }
                    items(shown, key = { it.id }) { task ->
                        Card(Modifier.fillMaxWidth().clickable { selectedId = task.id },
                            colors = CardDefaults.cardColors(containerColor = AppColors.Card)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(task.completed, onCheckedChange = { viewModel.setCompleted(task, it) }, enabled = !task.archived)
                                    Column(Modifier.weight(1f)) {
                                        Text(task.title, fontWeight = FontWeight.SemiBold)
                                        if (task.description.isNotBlank()) Text(task.description, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis, color = AppColors.SecondaryText, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Text("Оценка: ${TaskRules.durationLabel(task.estimatedDurationMinutes)} · ${TaskRules.priorityLabel(task.priorityLevel)} приоритет",
                                    style = MaterialTheme.typography.bodySmall, color = AppColors.SecondaryText)
                                Text("Срок: ${if (task.deadline.isBlank()) "не указан" else TaskRules.formatDate(task.deadline)}",
                                    style = MaterialTheme.typography.bodySmall, color = AppColors.SecondaryText)
                                Text(TaskRules.status(task), color = taskStatusColor(task), style = MaterialTheme.typography.labelMedium)
                                Row {
                                    TextButton(onClick = { viewModel.setArchived(task, !task.archived) }) {
                                        Text(if (task.archived) "Восстановить" else "В архив")
                                    }
                                    TextButton(onClick = { deleting = task }) { Text("Удалить", color = AppColors.Red) }
                                }
                            }
                        }
                    }
                }
                Button(onClick = { editingTask = null; showEditor = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("＋ Добавить задачу")
                }
            }
        }
        if (showEditor) TaskEditorDialog(editingTask, onDismiss = { showEditor = false }, error = error,
            onSave = { title, description, start, deadline, duration, priority ->
                val existing = editingTask
                val value = (existing ?: TaskEntity(title = title)).copy(title = title, description = description,
                    startDate = start, deadline = deadline, estimatedDurationMinutes = duration, priorityLevel = priority)
                val onSaved: () -> Unit = { showEditor = false; if (existing == null) showArchive = false }
                if (existing == null) viewModel.addTask(value, onSaved) else viewModel.updateTask(value, onSaved)
            })
        all.find { it.id == selectedId }?.let { task ->
            AlertDialog(onDismissRequest = { selectedId = null }, title = { Text(task.title) }, text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(task.description.ifBlank { "Описание не добавлено" })
                    Text("Начало: ${TaskRules.formatDate(task.startDate)}")
                    Text("Срок: ${if (task.deadline.isBlank()) "не указан" else TaskRules.formatDate(task.deadline)}")
                    Text("Примерная длительность: ${TaskRules.durationLabel(task.estimatedDurationMinutes)}")
                    Text("Приоритет: ${TaskRules.priorityLabel(task.priorityLevel)}")
                    Text(TaskRules.status(task), color = taskStatusColor(task))
                    task.completedAt?.let { Text("Выполнено: ${TaskRules.formatDate(it)}") }
                    TextButton(onClick = { viewModel.setArchived(task, !task.archived) { selectedId = null } }) {
                        Text(if (task.archived) "Восстановить из архива" else "Перенести в архив")
                    }
                    TextButton(onClick = { deleting = task; selectedId = null }) { Text("Удалить", color = AppColors.Red) }
                }
            }, confirmButton = {
                TextButton(onClick = { editingTask = task; selectedId = null; showEditor = true }) { Text("Редактировать") }
            }, dismissButton = { TextButton(onClick = { selectedId = null }) { Text("Закрыть") } })
        }
        deleting?.let { task ->
            AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Удалить задачу?") },
                text = { Text("«${task.title}» будет удалена. Если она завершена, её вклад в статистику тоже исчезнет.") },
                confirmButton = { TextButton(onClick = { viewModel.deleteTask(task) { deleting = null; selectedId = null } }) {
                    Text("Удалить", color = AppColors.Red)
                } },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } })
        }
        if (showStatistics) TaskStatisticsDialog(all, today) { showStatistics = false }
    }
}
