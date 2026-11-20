package com.anasikus.lifeassistant.ui.screens

import com.anasikus.lifeassistant.settings.AppText
import com.anasikus.lifeassistant.ui.assistantColorScheme
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
import com.anasikus.lifeassistant.domain.TaskProgress
import com.anasikus.lifeassistant.domain.TaskSessionPlanner
import com.anasikus.lifeassistant.ui.components.*
import com.anasikus.lifeassistant.ui.AppColors
import com.anasikus.lifeassistant.ui.components.TaskEditorDialog
import com.anasikus.lifeassistant.ui.components.TaskStatisticsDialog
import com.anasikus.lifeassistant.viewmodel.TaskViewModel

private fun taskStatusColor(task: TaskEntity): androidx.compose.ui.graphics.Color = when {
    task.frozen -> AppColors.SecondaryText
    task.completed -> AppColors.Green
    TaskRules.isOverdue(task) -> AppColors.Red
    TaskRules.isDueSoon(task) -> AppColors.Yellow
    else -> AppColors.SecondaryText
}

@Composable
fun TasksScreen(viewModel: TaskViewModel, modifier: Modifier = Modifier) {
    val all by viewModel.allTasks.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    var groupFilter by rememberSaveable { mutableStateOf(-1) }
    var showGroups by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf<Int>()) }
    var splitting by remember { mutableStateOf<TaskEntity?>(null) }
    val today by viewModel.currentDate.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var showFrozen by rememberSaveable { mutableStateOf(false) }
    var showArchive by rememberSaveable { mutableStateOf(false) }
    var showStatistics by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var deleting by remember { mutableStateOf<TaskEntity?>(null) }
    val active = remember(all, today) { all.filterNot { it.archived || it.frozen }.let { current ->
        current.sortedWith(compareByDescending<TaskEntity> { row ->
            (listOf(row) + current.filter { it.parentId == row.id }).maxOf { TaskRules.urgency(it, today) }
        }.then(TaskRules.comparator(today)))
    } }
    val archived = remember(all) { all.filter { it.archived }.sortedByDescending { it.id } }
    val frozen = all.filter { it.frozen && !it.archived }
    val selectedRows = (if (showArchive) archived else if (showFrozen) frozen else active)
        .filter { groupFilter == -1 || (it.groupId ?: 0) == groupFilter }
    LaunchedEffect(groups) { if (groupFilter > 0 && groups.none { it.id == groupFilter }) groupFilter = -1 }
    val shown = selectedRows.filter { task -> task.parentId == null || task.parentId in expanded || selectedRows.none { it.id == task.parentId } }
    val doneToday = all.count { !it.isProject && it.completed && it.completedAt == today }

    MaterialTheme(colorScheme = assistantColorScheme(primary = AppColors.Accent, surface = AppColors.Card,
        onSurface = AppColors.MainText, onSurfaceVariant = AppColors.SecondaryText)) {
        Surface(modifier.fillMaxSize(), color = AppColors.Background) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(AppText.get("Задачи"), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = { showStatistics = true }) { Text(AppText.get("Статистика")) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { ChoiceField(AppText.get("Группа задач"), if (groupFilter == -1) AppText.get("Все группы") else groups.find { it.id == groupFilter }?.name ?: AppText.get("Без группы"),
                        listOf("-1" to AppText.get("Все группы"), "0" to AppText.get("Без группы")) + groups.map { it.id.toString() to it.name }) { groupFilter = it.toInt() } }
                    TextButton(onClick = { showGroups = true }) { Text(AppText.get("Группы")) }
                }
                Text(AppText.get("Выполнено сегодня: {arg0} · В работе: {arg1}", doneToday, active.count { !it.isProject && !it.completed }),
                    color = AppColors.SecondaryText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!showArchive && !showFrozen, onClick = { showArchive = false; showFrozen = false }, label = { Text(AppText.get("Активные ({arg0})", active.size)) })
                    FilterChip(showArchive, onClick = { showArchive = true; showFrozen = false }, label = { Text(AppText.get("Архив ({arg0})", archived.size)) })
                }
                Row {
                    FilterChip(showFrozen, onClick = { showFrozen = true; showArchive = false }, label = { Text(AppText.get("Замороженные ({arg0})", frozen.size)) })
                }
                error?.let { Text(it, color = AppColors.Red) }
                Text(if (showArchive) AppText.get("Из архива можно восстановить или удалить задачу.")
                    else if (showFrozen) AppText.get("Идеи на будущее: эти задачи не занимают время в календаре.")
                    else AppText.get("Просроченные → ближайшие 3 дня → остальные. Приоритет действует внутри группы. Выполненные задачи уходят в архив на следующий день."),
                    style = MaterialTheme.typography.bodySmall, color = AppColors.SecondaryText)
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (shown.isEmpty()) item {
                        Text(if (showArchive) AppText.get("Архив пока пуст") else if (showFrozen) AppText.get("Замороженных задач нет") else AppText.get("Задач пока нет"), color = AppColors.SecondaryText)
                    }
                    shown.groupBy { it.groupId }.forEach { (groupId, rows) ->
                    item(key = "group:$groupId") { Text(groups.find { it.id == groupId }?.name ?: AppText.get("Без группы"), color = AppColors.Accent, fontWeight = FontWeight.Bold) }
                    items(rows, key = { it.id }) { task ->
                        Card(Modifier.fillMaxWidth().clickable { selectedId = task.id },
                            colors = CardDefaults.cardColors(containerColor = AppColors.Card)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(task.completed, onCheckedChange = { viewModel.setCompleted(task, it) }, enabled = !task.archived && !task.frozen)
                                    Column(Modifier.weight(1f)) {
                                        Text(task.title, fontWeight = FontWeight.SemiBold)
                                        if (task.description.isNotBlank()) Text(task.description, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis, color = AppColors.SecondaryText, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                val progress = TaskProgress.of(task, all)
                                if (task.parentId != null) Text(all.find { it.id == task.parentId }?.title.orEmpty(), color = AppColors.Accent, style = MaterialTheme.typography.labelSmall)
                                Text(AppText.get("Всего: {arg0} · Потрачено: {arg1} · Осталось: {arg2}", TaskRules.durationLabel(progress.total), TaskRules.durationLabel(progress.spent), TaskRules.durationLabel(progress.remaining)),
                                    style = MaterialTheme.typography.bodySmall, color = AppColors.SecondaryText)
                                Text(AppText.get("Срок: {arg0}", if (task.deadline.isBlank()) AppText.get("не указан") else TaskRules.formatDate(task.deadline)),
                                    style = MaterialTheme.typography.bodySmall, color = AppColors.SecondaryText)
                                Text(TaskRules.status(task), color = taskStatusColor(task), style = MaterialTheme.typography.labelMedium)
                                val overdueChildren = all.count { it.parentId == task.id && !it.completed && TaskRules.isOverdue(it, today) }
                                if (overdueChildren > 0) Text(AppText.get("Просроченные подзадачи: {arg0}", overdueChildren), color = AppColors.Red)
                                if (task.isProject) TextButton(onClick = { expanded = if (task.id in expanded) expanded - task.id else expanded + task.id }) {
                                    Text(if (task.id in expanded) AppText.get("Свернуть подзадачи") else AppText.get("Показать подзадачи"))
                                }
                                Row {
                                    if (!task.completed && !task.archived) TextButton(onClick = { viewModel.setFrozen(task, !task.frozen) }) {
                                        Text(if (task.frozen) AppText.get("Разморозить") else AppText.get("Заморозить"))
                                    }
                                    TextButton(onClick = { viewModel.setArchived(task, !task.archived) }) {
                                        Text(if (task.archived) AppText.get("Восстановить") else AppText.get("В архив"))
                                    }
                                    TextButton(onClick = { deleting = task }) { Text(AppText.get("Удалить"), color = AppColors.Red) }
                                }
                            }
                        }
                    }
                    }
                }
                Button(onClick = { editingTask = null; showEditor = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(AppText.get("＋ Добавить задачу"))
                }
            }
        }
        if (showGroups) TaskGroupsDialog(viewModel) { showGroups = false }
        if (showEditor) TaskEditorDialog(editingTask, groups = groups, initialGroupId = groupFilter.takeIf { it > 0 }, projectTotal = editingTask?.takeIf { it.isProject }?.let { TaskProgress.of(it, all).total }, onDismiss = { showEditor = false }, error = error,
            onSave = { title, description, start, deadline, duration, priority, group, spent ->
                val existing = editingTask
                val value = (existing ?: TaskEntity(title = title)).copy(title = title, description = description,
                    startDate = start, deadline = deadline, estimatedDurationMinutes = duration, priorityLevel = priority, groupId = group, spentMinutes = spent)
                val onSaved: () -> Unit = { showEditor = false; if (existing == null) { showArchive = false; showFrozen = false } }
                if (existing == null) viewModel.addTask(value, onSaved) else viewModel.updateTask(value, onSaved, adjustSpent = spent != existing.spentMinutes)
            })
        all.find { it.id == selectedId }?.let { task ->
            AlertDialog(onDismissRequest = { selectedId = null }, title = { Text(task.title) }, text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(task.description.ifBlank { AppText.get("Описание не добавлено") })
                    Text(AppText.get("Начало: {arg0}", TaskRules.formatDate(task.startDate)))
                    Text(AppText.get("Срок: {arg0}", if (task.deadline.isBlank()) AppText.get("не указан") else TaskRules.formatDate(task.deadline)))
                    val progress = TaskProgress.of(task, all)
                    Text(AppText.get("Всего: {arg0}", TaskRules.durationLabel(progress.total)))
                    Text(AppText.get("Потрачено: {arg0} · Осталось: {arg1}", TaskRules.durationLabel(progress.spent), TaskRules.durationLabel(progress.remaining)))
                    Text(AppText.get("Учёт по завершённым окнам календаря. Потраченное время можно исправить в редактировании."), color = AppColors.SecondaryText)
                    if (task.parentId == null && !task.isProject && !task.completed && !task.frozen && !task.archived && progress.remaining >= 30)
                        Button(onClick = { splitting = task; selectedId = null }) { Text(AppText.get("Разделить по задачам")) }
                    Text(AppText.get("Приоритет: {arg0}", TaskRules.priorityLabel(task.priorityLevel)))
                    Text(TaskRules.status(task), color = taskStatusColor(task))
                    task.completedAt?.let { Text(AppText.get("Выполнено: {arg0}", TaskRules.formatDate(it))) }
                    if (!task.completed && !task.archived) TextButton(onClick = { viewModel.setFrozen(task, !task.frozen) { selectedId = null } }) {
                        Text(if (task.frozen) AppText.get("Разморозить задачу") else AppText.get("Заморозить задачу"))
                    }
                    TextButton(onClick = { viewModel.setArchived(task, !task.archived) { selectedId = null } }) {
                        Text(if (task.archived) AppText.get("Восстановить из архива") else AppText.get("Перенести в архив"))
                    }
                    TextButton(onClick = { deleting = task; selectedId = null }) { Text(AppText.get("Удалить"), color = AppColors.Red) }
                }
            }, confirmButton = {
                TextButton(onClick = { editingTask = task; selectedId = null; showEditor = true }) { Text(AppText.get("Редактировать")) }
            }, dismissButton = { TextButton(onClick = { selectedId = null }) { Text(AppText.get("Закрыть")) } })
        }
        deleting?.let { task ->
            AlertDialog(onDismissRequest = { deleting = null }, title = { Text(AppText.get("Удалить задачу?")) },
                text = { Text(if (task.isProject) AppText.get("«{arg0}» и её подзадачи ({arg1}) будут удалены. Их вклад в статистику тоже исчезнет.", task.title, all.count { it.parentId == task.id })
                    else AppText.get("«{arg0}» будет удалена. Если она завершена, её вклад в статистику тоже исчезнет.", task.title)) },
                confirmButton = { TextButton(onClick = { viewModel.deleteTask(task) { deleting = null; selectedId = null } }) {
                    Text(AppText.get("Удалить"), color = AppColors.Red)
                } },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text(AppText.get("Отмена")) } })
        }
        splitting?.let { original ->
            val task = all.find { it.id == original.id } ?: original
            TaskSplitDialog(task, error, onDismiss = { splitting = null }) { drafts ->
            viewModel.split(task, TaskSessionPlanner.remaining(task), drafts) { splitting = null; expanded = expanded + task.id }
        } }
        if (showStatistics) TaskStatisticsDialog(all, today) { showStatistics = false }
    }
}
