package com.anasikus.lifeassistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anasikus.lifeassistant.data.*
import com.anasikus.lifeassistant.domain.*
import com.anasikus.lifeassistant.ui.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Composable
fun FinanceStatisticsDialog(
    transactions: List<FinanceTransactionEntity>, categories: List<ExpenseCategoryEntity>, month: String, cutoff: String,
    events: List<CalendarEventEntity>, overrides: List<CalendarEventOverrideEntity>, pattern: ShiftPatternEntity?,
    days: List<ShiftDayEntity>, minute: Int, onDismiss: () -> Unit
) {
    val periods = listOf("Неделя" to 0, "Месяц" to 1, "3 месяца" to 3, "Полгода" to 6, "Год" to 12)
    var months by remember { mutableStateOf(1) }
    var periodMenu by remember { mutableStateOf(false) }
    var showEarned by remember { mutableStateOf(true) }
    val start = if (months == 0) CalendarDateUtils.addDays(cutoff, -6) else CalendarDateUtils.addMonths(month, 1 - months)
    val points = remember(transactions, start, cutoff) { FinanceStatistics.cashSeries(transactions, start, cutoff) }
    val expenseCategories = remember(transactions, categories, start, cutoff) {
        FinanceStatistics.expenseCategories(transactions, categories, start, cutoff)
    }
    val incomeSources = remember(transactions, start, cutoff) {
        transactions.filter { !it.transfer && it.income && it.date in start..cutoff }
            .groupBy { it.sourceName ?: "Прочий доход" }
            .map { (name, rows) -> name to rows.sumOf { it.amountCents } }.sortedByDescending { it.second }
    }
    val dates = remember(start, cutoff) {
        FinanceStatistics.bucketEnds(start, cutoff).let { ends ->
            if (ends.isEmpty()) emptyList() else listOf(CalendarDateUtils.addDays(start, -1)) + ends
        }
    }
    // Use the same accrual rules as the main finance screen, before subtracting payouts.
    // At most 32 daily cutoffs (or 13 monthly cutoffs), on a background dispatcher.
    val earnings by key(start, cutoff) {
        produceState<List<Long>?>(null, dates, events, overrides, pattern, days, minute) {
            value = null
            value = withContext(Dispatchers.Default) {
                val cumulative = dates.map { date ->
                    currentCoroutineContext().ensureActive()
                    FinanceRules.sources(events, overrides, pattern, days, date, if (date == cutoff) minute else 1439)
                        .sumOf { it.accrued }
                }
                val baseline = cumulative.firstOrNull() ?: 0L
                cumulative.map { (it - baseline).coerceAtLeast(0L) }
            }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp).fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.extraLarge, color = AppColors.Card, contentColor = AppColors.MainText) {
            Column {
                Text("Статистика финансов", Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box {
                        OutlinedButton(onClick = { periodMenu = true }) { Text(periods.first { it.second == months }.first) }
                        DropdownMenu(periodMenu, onDismissRequest = { periodMenu = false }) {
                            periods.forEach { (label, value) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { months = value; periodMenu = false })
                            }
                        }
                    }
                    Text("${CalendarDateUtils.formatDate(start)} – ${CalendarDateUtils.formatDate(cutoff)}", color = AppColors.SecondaryText)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Доход получен", color = AppColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
                            Text(ShiftRules.money(points.lastOrNull()?.income ?: 0L), color = AppColors.Green)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Потрачено", color = AppColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
                            Text(ShiftRules.money(points.lastOrNull()?.expense ?: 0L), color = AppColors.Red)
                        }
                    }
                    Text("Динамика за период", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = showEarned, onClick = { showEarned = true }, label = { Text("Заработано") })
                        FilterChip(selected = !showEarned, onClick = { showEarned = false }, label = { Text("Доход получен") })
                    }
                    Text(if (showEarned) "Завершённые смены и подработки до вычета выплат по ним."
                        else "Средства, уже записанные в доходы.", color = AppColors.SecondaryText, style = MaterialTheme.typography.bodySmall)
                    Text(if (dates.size > 1 && CalendarDateUtils.daysBetween(start, cutoff) >= 31)
                        "По месяцам, накопительно с начала периода." else "По дням, накопительно с начала периода.",
                        color = AppColors.SecondaryText, style = MaterialTheme.typography.bodySmall)
                    if (showEarned && earnings == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Считаю начисления…", color = AppColors.SecondaryText)
                    } else {
                        FinanceComparisonChart(points, if (showEarned) earnings.orEmpty() else points.map { it.income },
                            if (showEarned) "Заработано" else "Доход")
                    }
                    HorizontalDivider()
                    Text("На что ушли деньги", style = MaterialTheme.typography.titleMedium)
                    ExpenseDonutChart(expenseCategories)
                    HorizontalDivider()
                    Text("Доходы по источникам", style = MaterialTheme.typography.titleMedium)
                    if (incomeSources.isEmpty()) Text("Доходов за этот период нет", color = AppColors.SecondaryText)
                    incomeSources.forEach { (name, amount) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            Text(name, Modifier.weight(1f))
                            Text(ShiftRules.money(amount), color = AppColors.Green)
                        }
                    }
                    Text("Переводы не включены в суммы и диаграммы.", color = AppColors.SecondaryText, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                }
                HorizontalDivider(Modifier.padding(top = 12.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                }
            }
        }
    }
}
