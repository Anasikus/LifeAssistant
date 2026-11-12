package com.anasikus.lifeassistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anasikus.lifeassistant.data.ExpenseCategoryEntity
import com.anasikus.lifeassistant.data.FinanceTransactionEntity
import com.anasikus.lifeassistant.domain.CalendarDateUtils
import com.anasikus.lifeassistant.domain.FinanceRules
import com.anasikus.lifeassistant.domain.FinanceSource
import com.anasikus.lifeassistant.domain.ShiftRules
import com.anasikus.lifeassistant.ui.AppColors
import com.anasikus.lifeassistant.ui.components.PlanningDateField
import com.anasikus.lifeassistant.ui.components.ExpenseCategoryPicker
import com.anasikus.lifeassistant.ui.components.ExpenseCategoriesDialog
import com.anasikus.lifeassistant.ui.components.PaymentPlansDialog
import com.anasikus.lifeassistant.ui.components.FinanceStatisticsDialog
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
    val categories by viewModel.expenseCategories.collectAsState()
    val inbox by viewModel.inboxMessages.collectAsState()
    var showInbox by remember { mutableStateOf(false) }
    var showPlans by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    var showStatistics by remember { mutableStateOf(false) }
    val now by produceState(Calendar.getInstance()) {
        while (true) {
            value = Calendar.getInstance()
            delay(60_000L)
        }
    }
    val today = CalendarDateUtils.today()
    var month by remember { mutableStateOf(CalendarDateUtils.startOfMonth(today)) }
    val monthEnd = CalendarDateUtils.endOfMonth(month)
    val cutoff = minOf(monthEnd, today)
    val minute = if (cutoff == today) now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE) else 1439
    val sources = key(cutoff) {
        val calculated by produceState(emptyList<FinanceSource>(), events, overrides, pattern, days, minute) {
            value = withContext(Dispatchers.Default) {
                FinanceRules.sources(events, overrides, pattern, days, cutoff, minute)
            }
        }
        calculated
    }
    val totals = remember(sources, transactions, month, cutoff) {
        FinanceRules.monthTotals(sources, transactions, month, cutoff)
    }
    val monthTransactions = remember(transactions, month, cutoff) {
        transactions.filter { it.date in month..cutoff }
    }
    val paymentsThroughCutoff = remember(transactions, cutoff) { transactions.filter { it.date <= cutoff } }
    var newIncome by remember { mutableStateOf<Boolean?>(null) }
    var deleting by remember { mutableStateOf<FinanceTransactionEntity?>(null) }

    if (showInbox) {
        FinanceInboxScreen(viewModel, sources, onBack = { showInbox = false }, modifier = modifier)
        return
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = AppColors.Accent, surface = AppColors.Card)) {
        LazyColumn(
            modifier = modifier.background(AppColors.Background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Финансы", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, color = AppColors.MainText)
                    TextButton(onClick = { showInbox = true }) {
                        Text("Уведомления (${inbox.count { it.status == "PENDING" }})")
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { month = CalendarDateUtils.addMonths(month, -1) }) { Text("‹") }
                    Text(CalendarDateUtils.formatMonth(month), Modifier.weight(1f), color = AppColors.MainText)
                    TextButton(
                        onClick = { month = CalendarDateUtils.addMonths(month, 1) },
                        enabled = month < CalendarDateUtils.startOfMonth(today)
                    ) { Text("›") }
                }
            }
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
                Text("Перенос на начало месяца: ${ShiftRules.money(totals.openingCash)}", color = AppColors.SecondaryText)
                Text("Заработано — начисления за завершённые смены и подработки за вычетом выплат по ним.", color = AppColors.SecondaryText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { newIncome = true }, modifier = Modifier.weight(1f)) { Text("Доход") }
                    OutlinedButton(onClick = { newIncome = false }, modifier = Modifier.weight(1f)) { Text("Расход") }
                }
            }
            item {
                Column {
                    OutlinedButton(onClick = { showPlans = true }, modifier = Modifier.fillMaxWidth()) { Text("Регулярные платежи") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showCategories = true }) { Text("Категории расходов") }
                        TextButton(onClick = { showStatistics = true }) { Text("Статистика") }
                    }
                }
            }
            if (sources.isNotEmpty()) {
                item { Text("К выплате по работам", color = AppColors.MainText) }
                items(sources, key = { it.key }) { source ->
                    Column {
                        Text("${source.name} · ${ShiftRules.money(FinanceRules.outstanding(source, paymentsThroughCutoff))}", color = AppColors.SecondaryText)
                        source.plannedAdvance?.let { Text("Плановый аванс · ${ShiftRules.money(it)}", color = AppColors.SecondaryText) }
                    }
                }
            }
            item { Text("История операций", color = AppColors.MainText) }
            if (monthTransactions.isEmpty()) item { Text("Операций пока нет", color = AppColors.SecondaryText) }
            items(monthTransactions, key = { it.id }) { transaction ->
                Card(colors = CardDefaults.cardColors(containerColor = AppColors.Card)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        val sign = if (transaction.income) "+" else "−"
                        Text("$sign${ShiftRules.money(transaction.amountCents)}", color = if (transaction.income) AppColors.Green else AppColors.Red)
                        Text(CalendarDateUtils.formatDate(transaction.date), color = AppColors.SecondaryText)
                        if (!transaction.income) {
                            ExpenseCategoryPicker(categories, transaction.expenseCategoryId) {
                                viewModel.setExpenseCategory(transaction.id, it)
                            }
                        }
                        transaction.sourceName?.let { Text(it, color = AppColors.MainText) }
                        if (transaction.note.isNotBlank()) Text(transaction.note, color = AppColors.SecondaryText)
                        TextButton(onClick = { deleting = transaction }) { Text("Удалить") }
                    }
                }
            }
        }
        if (showPlans) PaymentPlansDialog(viewModel, if (monthEnd < today) month else today) { showPlans = false }
        if (showCategories) ExpenseCategoriesDialog(viewModel) { showCategories = false }
        if (showStatistics) FinanceStatisticsDialog(transactions, categories, month, cutoff) { showStatistics = false }
        newIncome?.let { income ->
            TransactionDialog(income, sources, categories, today, if (monthEnd < today) month else today, onDismiss = { newIncome = null }) {
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
    categories: List<ExpenseCategoryEntity>,
    today: String,
    initialDate: String,
    onDismiss: () -> Unit,
    onSave: (FinanceTransactionEntity) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(initialDate) }
    var categoryId by remember { mutableStateOf<Int?>(null) }
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
                if (!income) ExpenseCategoryPicker(categories, categoryId) { categoryId = it }
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
                        sourceName = selected?.name,
                        expenseCategoryId = if (income) null else categoryId
                    ))
                }
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
