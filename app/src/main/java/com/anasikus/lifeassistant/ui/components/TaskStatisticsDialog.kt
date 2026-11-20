package com.anasikus.lifeassistant.ui.components

import com.anasikus.lifeassistant.settings.AppText
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.anasikus.lifeassistant.data.TaskEntity
import com.anasikus.lifeassistant.domain.CalendarDateUtils
import com.anasikus.lifeassistant.domain.TaskRules
import com.anasikus.lifeassistant.domain.TaskStatistics
import com.anasikus.lifeassistant.ui.AppColors
import kotlin.math.floor

@Composable
fun TaskStatisticsDialog(tasks: List<TaskEntity>, today: String, onDismiss: () -> Unit) {
    var period by remember { mutableStateOf(7) }
    val start = CalendarDateUtils.addDays(today, 1 - period)
    val days = remember(tasks, start, today) { TaskStatistics.days(tasks, start, today) }
    var selected by remember(period, today) { mutableStateOf(period - 1) }
    val count = days.sumOf { it.completed }
    val best = days.filter { it.completed > 0 }.maxWithOrNull(compareBy({ it.completed }, { it.date }))
    val point = days.getOrNull(selected)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(AppText.get("Продуктивность")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(period == 7, onClick = { period = 7 }, label = { Text(AppText.get("7 дней")) })
                FilterChip(period == 30, onClick = { period = 30 }, label = { Text(AppText.get("30 дней")) })
            }
            Text("${CalendarDateUtils.formatDate(start)} — ${CalendarDateUtils.formatDate(today)}", color = AppColors.SecondaryText)
            Text(AppText.get("Выполнено: {arg0}", count), style = MaterialTheme.typography.titleMedium)
            Text(AppText.get("С опозданием: {arg0}", days.sumOf { it.late }), color = AppColors.SecondaryText)
            if (best == null) Text(AppText.get("Выполненных задач за этот период пока нет."))
            else Text(AppText.get("Больше всего выполнено {arg0}: {arg1}.", CalendarDateUtils.formatDate(best.date), best.completed))
            Text(AppText.get("Количество выполненных задач по дням"), style = MaterialTheme.typography.labelMedium)
            Canvas(Modifier.fillMaxWidth().height(150.dp).semantics {
                contentDescription = AppText.get("Завершено {arg0} задач за {arg1} дней", count, period)
            }.pointerInput(days) {
                detectTapGestures { tap ->
                    selected = floor(tap.x / size.width.coerceAtLeast(1) * days.size).toInt().coerceIn(days.indices)
                }
            }) {
                val maximum = (days.maxOfOrNull { it.completed } ?: 0).coerceAtLeast(1)
                val width = size.width / days.size.coerceAtLeast(1)
                drawLine(AppColors.SecondaryText.copy(alpha = 0.4f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                days.forEachIndexed { index, value ->
                    val height = size.height * value.completed / maximum
                    drawRect(if (index == selected) AppColors.Accent else AppColors.Green,
                        topLeft = Offset(index * width + width * 0.12f, size.height - height),
                        size = Size(width * 0.76f, height))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(CalendarDateUtils.formatDate(start).take(5), style = MaterialTheme.typography.labelSmall)
                Text(CalendarDateUtils.formatDate(today).take(5), style = MaterialTheme.typography.labelSmall)
            }
            point?.let {
                Text(CalendarDateUtils.formatDate(it.date), style = MaterialTheme.typography.titleSmall)
                Text(AppText.get("Выполнено: {arg0} · С опозданием: {arg1}", it.completed, it.late))
                Text(AppText.get("Оценка времени этих задач: {arg0}", TaskRules.durationLabel(it.estimatedMinutes)))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    TextButton(enabled = selected > 0, onClick = { selected-- }) { Text(AppText.get("‹ День")) }
                    TextButton(enabled = selected < days.lastIndex, onClick = { selected++ }) { Text(AppText.get("День ›")) }
                }
            }
            Text(AppText.get("Нажми на столбец или выбери день стрелками. В статистику входят и архивные задачи. Время — оценка из задачи, а не фактический таймер."),
                style = MaterialTheme.typography.bodySmall, color = AppColors.SecondaryText)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(AppText.get("Закрыть")) } })
}
