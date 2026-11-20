package com.anasikus.lifeassistant.ui.components

import com.anasikus.lifeassistant.settings.AppText
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.anasikus.lifeassistant.data.*
import com.anasikus.lifeassistant.domain.*
import com.anasikus.lifeassistant.ui.AppColors
import com.anasikus.lifeassistant.viewmodel.TaskViewModel

@Composable
fun TaskGroupsDialog(viewModel: TaskViewModel, onDismiss: () -> Unit) {
    val groups by viewModel.groups.collectAsState()
    val error by viewModel.error.collectAsState()
    CategoryManager(AppText.get("Группы задач"), groups.map { it.id to it.name }, error,
        { id, name, saved -> viewModel.saveGroup(TaskGroupEntity(id, name), saved) }, viewModel::deleteGroup, onDismiss, deleteMessage = AppText.get("Задачи сохранятся без группы."))
}

@Composable
fun TaskSplitDialog(task: TaskEntity, error: String?, onDismiss: () -> Unit, onSave: (List<TaskSplitDraft>) -> Unit) {
    val initial = remember(task.id) { TaskDecomposer.suggest(task) }
    var titles by remember { mutableStateOf(initial.map { it.title }) }
    var hours by remember { mutableStateOf(initial.map { (it.minutes / 60).toString() }) }
    var minuteParts by remember { mutableStateOf(initial.map { (it.minutes % 60).toString() }) }
    val budgets = hours.indices.map { index ->
        val h = hours[index].toIntOrNull(); val m = minuteParts[index].toIntOrNull()
        if (h == null || m == null || h !in 0..10000 || m !in 0..59) null else h * 60 + m
    }
    val remaining = TaskSessionPlanner.remaining(task)
    var validation by remember { mutableStateOf(false) }
    fun save() {
        val minutes = budgets
        if (titles.size < 2 || titles.any { it.isBlank() } || minutes.any { it == null || it < 15 } || minutes.sumOf { it ?: 0 } != remaining) validation = true
        else onSave(titles.indices.map { TaskSplitDraft(titles[it], checkNotNull(minutes[it])) })
    }
    val dismiss = rememberDismissGuard(true, ::save, onDismiss)
    AlertDialog(onDismissRequest = dismiss, title = { Text(AppText.get("Разделить по задачам")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium)
            Text(AppText.get("Локальный шаблон по названию и описанию. Проверь шаги и оценки перед созданием."), color = AppColors.SecondaryText)
            Text(AppText.get("Осталось распределить: {arg0}", TaskRules.durationLabel(remaining)))
            titles.forEachIndexed { index, value ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        OutlinedTextField(value, { next -> titles = titles.toMutableList().also { it[index] = next } }, label = { Text(AppText.get("Шаг {arg0}", index + 1)) })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(hours[index], { next -> hours = hours.toMutableList().also { it[index] = next.filter(Char::isDigit).take(5) } }, Modifier.weight(1f),
                                label = { Text(AppText.get("Часы")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(minuteParts[index], { next -> minuteParts = minuteParts.toMutableList().also { it[index] = next.filter(Char::isDigit).take(2) } }, Modifier.weight(1f),
                                label = { Text(AppText.get("Минуты")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                        if (titles.size > 2) TextButton(onClick = {
                            titles = titles.filterIndexed { i, _ -> i != index }
                            hours = hours.filterIndexed { i, _ -> i != index }
                            minuteParts = minuteParts.filterIndexed { i, _ -> i != index }
                        }) { Text(AppText.get("Убрать шаг")) }
                    }
                }
            }
            TextButton(onClick = { val values = TaskDecomposer.distribute(titles, remaining)
                hours = values.map { (it.minutes / 60).toString() }; minuteParts = values.map { (it.minutes % 60).toString() } }) { Text(AppText.get("Распределить время поровну")) }
            Text(AppText.get("Сумма: {arg0}", TaskRules.durationLabel(budgets.sumOf { it ?: 0 })))
            if (validation) Text(AppText.get("Нужны минимум два шага по 15 минут. Сумма должна совпадать с оставшимся временем."), color = AppColors.Red)
            error?.let { Text(it, color = AppColors.Red) }
        }
    }, confirmButton = { Button(onClick = ::save) { Text(AppText.get("Создать подзадачи")) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(AppText.get("Отмена")) } })
}
