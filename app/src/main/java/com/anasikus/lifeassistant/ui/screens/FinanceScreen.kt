package com.anasikus.lifeassistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anasikus.lifeassistant.data.FinanceTransactionEntity
import com.anasikus.lifeassistant.domain.CalendarDateUtils
import com.anasikus.lifeassistant.domain.FinanceRules
import com.anasikus.lifeassistant.domain.FinanceSource
import com.anasikus.lifeassistant.domain.ShiftRules
import com.anasikus.lifeassistant.ui.AppColors
import com.anasikus.lifeassistant.ui.components.PlanningDateField
import com.anasikus.lifeassistant.viewmodel.CalendarViewModel
import java.util.Calendar
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FinanceScreen(viewModel: CalendarViewModel, modifier: Modifier = Modifier) {
    val events by viewModel.events.collectAsState()
    val overrides by viewModel.eventOverrides.collectAsState()
    val pattern by viewModel.shiftPattern.collectAsState()
    val days by viewModel.shiftDays.collectAsState()
    val transactions by viewModel.financeTransactions.collectAsState()
    val now by produceState(Calendar.getInstance()) {
        while (true) {
            value = Calendar.getInstance()
            delay(60_000L)
        }
    }
    val today = CalendarDateUtils.today()
    val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val sources by produceState(emptyList<FinanceSource>(), events, overrides, pattern, days, today, minute) {
        value = withContext(Dispatchers.Default) {
            FinanceRules.sources(events, overrides, pattern, days, today, minute)
        }
    }
    val totals = remember(sources, transactions) { FinanceRules.totals(sources, transactions) }
    var newIncome by remember { mutableStateOf<Boolean?>(null) }
    var deleting by remember { mutableStateOf<FinanceTransactionEntity?>(null) }

    MaterialTheme(colorScheme = darkColorScheme(primary = AppColors.Accent, surface = AppColors.Card)) {
        LazyColumn(
            modifier = modifier.background(AppColors.Background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Text("Финансы", style = MaterialTheme.typography.headlineSmall, color = AppColors.MainText) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FinanceMetric("Заработано", totals.earned, Modifier.weight(1f))
                    FinanceMetric("Доход", totals.income, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FinanceMetric("Расход", totals.expense, Modifier.weight(1f))
                    FinanceMetric("На руках", totals.cash, Modifier.weight(1f))
                }
            }
            item {
                Text("Заработано — остаток к выплате за прошедшие смены и подработки. Суммы за всё время.", color = AppColors.SecondaryText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { newIncome = true }, modifier = Modifier.weight(1f)) { Text("Доход") }
                    OutlinedButton(onClick = { newIncome = false }, modifier = Modifier.weight(1f)) { Text("Расход") }
                }
            }
            if (sources.isNotEmpty()) {
                item { Text("К выплате по работам", color = AppColors.MainText) }
                items(sources, key = { it.key }) { source ->
                    Text("${source.name} · ${ShiftRules.money(FinanceRules.outstanding(source, transactions))}", color = AppColors.SecondaryText)
                }
            }
            item { Text("История операций", color = AppColors.MainText) }
            if (transactions.isEmpty()) item { Text("Операций пока нет", color = AppColors.SecondaryText) }
            items(transactions, key = { it.id }) { transaction ->
                Card(colors = CardDefaults.cardColors(containerColor = AppColors.Card)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        val sign = if (transaction.income) "+" else "−"
                        Text("$sign${ShiftRules.money(transaction.amountCents)}", color = if (transaction.income) AppColors.Green else AppColors.Red)
                        Text(CalendarDateUtils.formatDate(transaction.date), color = AppColors.SecondaryText)
                        transaction.sourceName?.let { Text(it, color = AppColors.MainText) }
                        if (transaction.note.isNotBlank()) Text(transaction.note, color = AppColors.SecondaryText)
                        TextButton(onClick = { deleting = transaction }) { Text("Удалить") }
                    }
                }
            }
        }
        newIncome?.let { income ->
            TransactionDialog(income, sources, today, onDismiss = { newIncome = null }) {
                viewModel.addFinanceTransaction(it)
                newIncome = null
            }
        }
        deleting?.let { transaction ->
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text("Удалить операцию?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteFinanceTransaction(transaction.id)
                        deleting = null
                    }) { Text("Удалить") }
                },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } }
            )
        }
    }
}

@Composable
private fun FinanceMetric(label: String, amount: Long, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = AppColors.Card)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = AppColors.SecondaryText)
            Text(ShiftRules.money(amount), color = AppColors.MainText, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun TransactionDialog(
    income: Boolean,
    sources: List<FinanceSource>,
    today: String,
    onDismiss: () -> Unit,
    onSave: (FinanceTransactionEntity) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today) }
    var selected by remember { mutableStateOf<FinanceSource?>(null) }
    var menu by remember { mutableStateOf(false) }
    val cents = ShiftRules.cents(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (income) "Добавить доход" else "Добавить расход") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(amount, { amount = it }, label = { Text("Сумма, ₽") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Комментарий") })
                PlanningDateField("Дата операции", date) { date = it }
                if (date > today) Text("Укажи дату не позднее сегодняшней", color = AppColors.Red)
                if (income) {
                    Box {
                        OutlinedButton(onClick = { menu = true }) {
                            Text(selected?.name ?: "Прочий доход")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Прочий доход") }, onClick = { selected = null; menu = false })
                            sources.forEach { source ->
                                DropdownMenuItem(text = { Text(source.name) }, onClick = { selected = source; menu = false })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = cents != null && cents > 0L && date <= today,
                onClick = {
                    onSave(FinanceTransactionEntity(
                        date = date,
                        income = income,
                        amountCents = checkNotNull(cents),
                        note = note.trim(),
                        sourceKey = selected?.key,
                        sourceName = selected?.name
                    ))
                }
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
