package com.anasikus.lifeassistant.ui.screens

import com.anasikus.lifeassistant.settings.AppText
import com.anasikus.lifeassistant.ui.assistantColorScheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.anasikus.lifeassistant.data.IncomeCategoryEntity
import com.anasikus.lifeassistant.ui.components.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
fun FinanceScreen(viewModel: CalendarViewModel, modifier: Modifier = Modifier, homeRequest: Int = 0) {
    val events by viewModel.events.collectAsState()
    val overrides by viewModel.eventOverrides.collectAsState()
    val pattern by viewModel.shiftPattern.collectAsState()
    val days by viewModel.shiftDays.collectAsState()
    val transactions by viewModel.financeTransactions.collectAsState()
    val categories by viewModel.expenseCategories.collectAsState()
    val inbox by viewModel.inboxMessages.collectAsStateWithLifecycle()
    val incomeCategories by viewModel.incomeCategories.collectAsState()
    val financeError by viewModel.financeError.collectAsState()
    var showIncomeCategories by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FinanceTransactionEntity?>(null) }
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
    val totals = remember(sources, transactions, month, cutoff, today) {
        FinanceRules.monthTotals(sources, transactions, month, cutoff).let { value ->
            // Historical months show their closing balance; today's reserve belongs to the current month.
            if (cutoff < today) value.copy(reserved = 0) else value
        }
    }
    val monthTransactions = remember(transactions, month, cutoff) {
        transactions.filter { it.date in month..cutoff }
    }
    val reserved = remember(transactions, today) { transactions.filter { !it.income && !it.transfer && it.date > today } }
    val paymentsThroughCutoff = remember(transactions, cutoff) { transactions.filter { it.date <= cutoff } }
    var newTransfer by remember { mutableStateOf(false) }
    var newIncome by remember { mutableStateOf<Boolean?>(null) }
    var deleting by remember { mutableStateOf<FinanceTransactionEntity?>(null) }

    LaunchedEffect(homeRequest) {
        showIncomeCategories = false
        editing = null
        showInbox = false
        showPlans = false
        showCategories = false
        showStatistics = false
        newIncome = null
        deleting = null
    }

    if (showInbox) {
        FinanceInboxScreen(viewModel, sources, onBack = { showInbox = false }, modifier = modifier)
        return
    }

    MaterialTheme(colorScheme = assistantColorScheme(primary = AppColors.Accent, surface = AppColors.Card)) {
        LazyColumn(
            modifier = modifier.background(AppColors.Background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(AppText.get("Финансы"), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, color = AppColors.MainText)
                    TextButton(onClick = { showInbox = true }) {
                        Text(AppText.get("Уведомления ({arg0})", inbox.count { it.status == "PENDING" }))
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { month = CalendarDateUtils.addMonths(month, -1) }) { Text("‹") }
                    Text(CalendarDateUtils.formatMonth(month), Modifier.weight(1f), textAlign = TextAlign.Center, color = AppColors.MainText)
                    TextButton(
                        onClick = { month = CalendarDateUtils.addMonths(month, 1) },
                        enabled = month < CalendarDateUtils.startOfMonth(today)
                    ) { Text("›") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FinanceMetric(AppText.get("Заработано"), totals.earned, Modifier.weight(1f))
                    FinanceMetric(AppText.get("Доход"), totals.income, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FinanceMetric(AppText.get("Расход"), totals.expense, Modifier.weight(1f))
                    FinanceMetric(AppText.get("На руках"), totals.available, Modifier.weight(1f))
                }
            }
            item {
                if (cutoff == today) {
                    FinanceMetric(AppText.get("Зарезервировано"), totals.reserved, Modifier.fillMaxWidth())
                    Text(AppText.get("На руках до резерва: {arg0}", ShiftRules.money(totals.cash)), color = AppColors.SecondaryText)
                }
                Text(AppText.get("Перенос на начало месяца: {arg0}", ShiftRules.money(totals.openingCash)), color = AppColors.SecondaryText)
                Text(AppText.get("Заработано — начисления за завершённые смены и подработки за вычетом выплат по ним."), color = AppColors.SecondaryText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { newTransfer = false; newIncome = true }, modifier = Modifier.weight(1f)) { Text(AppText.get("Доход")) }
                    OutlinedButton(onClick = { newTransfer = false; newIncome = false }, modifier = Modifier.weight(1f)) { Text(AppText.get("Расход")) }
                    OutlinedButton(onClick = { newTransfer = true; newIncome = false }, modifier = Modifier.weight(1f)) { Text(AppText.get("Перевод"), maxLines = 1) }
                }
            }
            item {
                Column {
                    OutlinedButton(onClick = { showPlans = true }, modifier = Modifier.fillMaxWidth()) { Text(AppText.get("Регулярные платежи")) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showCategories = true }) { Text(AppText.get("Категории расходов")) }
                        TextButton(onClick = { showStatistics = true }) { Text(AppText.get("Статистика")) }
                    }
                    TextButton(onClick = { showIncomeCategories = true }) { Text(AppText.get("Категории доходов")) }
                }
            }
            financeError?.let { message -> item { Text(message, color = AppColors.Red) } }
            if (cutoff == today && reserved.isNotEmpty()) {
                item { Text(AppText.get("Будущие расходы · резерв"), color = AppColors.MainText) }
                items(reserved, key = { "reserved:${it.id}" }) { transaction ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.Card)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${ShiftRules.money(transaction.amountCents)} · ${CalendarDateUtils.formatDate(transaction.date)}", color = AppColors.MainText)
                            Text(transaction.note.ifBlank { categories.find { it.id == transaction.expenseCategoryId }?.name ?: AppText.get("Запланированный расход") }, color = AppColors.SecondaryText)
                            Text(AppText.get("Учтён в резерве. В день операции перейдёт в расходы без повторного вычета."), color = AppColors.SecondaryText)
                            Row {
                                TextButton(onClick = { editing = transaction }) { Text(AppText.get("Изменить")) }
                                TextButton(onClick = { deleting = transaction }) { Text(AppText.get("Отменить расход")) }
                            }
                        }
                    }
                }
            }
            if (sources.isNotEmpty()) {
                item { Text(AppText.get("К выплате по работам"), color = AppColors.MainText) }
                items(sources, key = { it.key }) { source ->
                    Column {
                        Text("${source.name} · ${ShiftRules.money(FinanceRules.outstanding(source, paymentsThroughCutoff))}", color = AppColors.SecondaryText)
                        source.plannedAdvance?.let { Text(AppText.get("Плановый аванс · {arg0}", ShiftRules.money(it)), color = AppColors.SecondaryText) }
                    }
                }
            }
            item { Text(AppText.get("История операций"), color = AppColors.MainText) }
            if (monthTransactions.isEmpty()) item { Text(AppText.get("Операций пока нет"), color = AppColors.SecondaryText) }
            items(monthTransactions, key = { it.id }) { transaction ->
                Card(colors = CardDefaults.cardColors(containerColor = AppColors.Card)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        val sign = if (transaction.transfer) "↔ " else if (transaction.income) "+" else "−"
                        Text("$sign${ShiftRules.money(transaction.amountCents)}", color = if (transaction.transfer) AppColors.SecondaryText else if (transaction.income) AppColors.Green else AppColors.Red)
                        if (transaction.transfer) Text(AppText.get("Перевод · не влияет на остаток"), color = AppColors.SecondaryText)
                        Text(CalendarDateUtils.formatDate(transaction.date), color = AppColors.SecondaryText)
                        if (!transaction.transfer) {
                            val categoryName = if (transaction.income)
                                incomeCategories.find { it.id == transaction.incomeCategoryId }?.name ?: transaction.sourceName
                                else categories.find { it.id == transaction.expenseCategoryId }?.name
                            Text(categoryName ?: AppText.get("Без категории"), color = AppColors.SecondaryText)
                        }
                        if (transaction.note.isNotBlank()) Text(transaction.note, color = AppColors.SecondaryText)
                        Row {
                            TextButton(onClick = { editing = transaction }) { Text(AppText.get("Изменить")) }
                            TextButton(onClick = { deleting = transaction }) { Text(AppText.get("Удалить")) }
                        }
                    }
                }
            }
        }
        if (showIncomeCategories) IncomeCategoriesDialog(viewModel) { showIncomeCategories = false }
        if (showPlans) PaymentPlansDialog(viewModel, if (monthEnd < today) month else today) { showPlans = false }
        if (showCategories) ExpenseCategoriesDialog(viewModel) { showCategories = false }
        if (showStatistics) FinanceStatisticsDialog(transactions, categories, month, cutoff, events, overrides, pattern, days, minute, incomeCategories) { showStatistics = false }
        newIncome?.let { income ->
            TransactionDialog(income, sources, categories, incomeCategories, today, if (monthEnd < today) month else today,
                newTransfer, error = financeError, onManageIncome = { showIncomeCategories = true }, onDismiss = { newIncome = null }) {
                viewModel.addFinanceTransaction(it) { newIncome = null }
            }
        }
        editing?.let { transaction ->
            TransactionDialog(transaction.income, sources, categories, incomeCategories, today, transaction.date,
                transaction.transfer, existing = transaction, error = financeError, onManageIncome = { showIncomeCategories = true }, onDismiss = { editing = null }) {
                viewModel.updateFinanceTransaction(it) { editing = null }
            }
        }
        deleting?.let { transaction ->
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text(AppText.get("Удалить операцию?")) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteFinanceTransaction(transaction.id)
                        deleting = null
                    }) { Text(AppText.get("Удалить")) }
                },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text(AppText.get("Отмена")) } }
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
    income: Boolean, sources: List<FinanceSource>, categories: List<ExpenseCategoryEntity>,
    incomeCategories: List<IncomeCategoryEntity>, today: String, initialDate: String, transfer: Boolean,
    existing: FinanceTransactionEntity? = null, error: String? = null, onManageIncome: () -> Unit = {},
    onDismiss: () -> Unit, onSave: (FinanceTransactionEntity) -> Unit
) {
    var amount by remember { mutableStateOf(existing?.amountCents?.let { java.math.BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var date by remember { mutableStateOf(initialDate) }
    var categoryId by remember { mutableStateOf(existing?.expenseCategoryId) }
    var incomeCategoryId by remember { mutableStateOf(existing?.incomeCategoryId) }
    var sourceKey by remember { mutableStateOf(existing?.sourceKey?.takeUnless { it.startsWith("income:") }) }
    val cents = ShiftRules.cents(amount)
    var validation by remember { mutableStateOf(false) }
    val snapshot = listOf(amount, note, date, categoryId, incomeCategoryId, sourceKey)
    val initial = remember { snapshot }
    fun save() {
        if (cents == null || cents <= 0 || ((income || transfer) && date > today)) validation = true
        else {
            val selected = sources.find { it.key == sourceKey }
            onSave((existing ?: FinanceTransactionEntity(date = date, income = income, amountCents = cents, note = "")).copy(
                date = date, income = income && !transfer, transfer = transfer, amountCents = cents, note = note.trim(),
                sourceKey = if (income && !transfer) sourceKey else null,
                sourceName = if (income && !transfer) selected?.name ?: existing?.sourceName?.takeIf { sourceKey != null } else null,
                expenseCategoryId = if (income || transfer) null else categoryId?.takeIf { id -> categories.any { it.id == id } },
                incomeCategoryId = if (income && !transfer) incomeCategoryId?.takeIf { id -> incomeCategories.any { it.id == id } } else null))
        }
    }
    val dismiss = rememberDismissGuard(snapshot != initial, ::save, onDismiss)
    AlertDialog(onDismissRequest = dismiss,
        title = { Text(if (existing != null) AppText.get("Изменить операцию") else if (transfer) AppText.get("Добавить перевод") else if (income) AppText.get("Добавить доход") else AppText.get("Добавить расход")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text(AppText.get("Сумма, ₽")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text(AppText.get("Комментарий")) })
                OutlinedDateField(AppText.get("Дата операции"), date) { date = it }
                if (!income && !transfer) {
                    ExpenseCategoryPicker(categories, categoryId) { categoryId = it }
                    if (date > today) Text(AppText.get("Будущий расход: сумма попадёт в резерв и уменьшит доступный остаток. В дату операции она перейдёт в расходы."), color = AppColors.Yellow)
                }
                if (transfer) Text(AppText.get("Перевод сохраняется без изменения дохода, расхода и остатка."))
                if (income && !transfer) {
                    val workName = sources.find { it.key == sourceKey }?.name ?: existing?.sourceName?.takeIf { sourceKey != null }
                    if (sourceKey != null) Text(AppText.get("Категория дохода: {arg0}", workName.orEmpty()), color = AppColors.MainText)
                    else IncomeCategoryPicker(incomeCategories, incomeCategoryId) { incomeCategoryId = it }
                    if (sourceKey == null) TextButton(onClick = onManageIncome) { Text(AppText.get("Добавить или изменить категории")) }
                    Text(AppText.get("Например: возврат долга, помощь родителей, подарок. Категории можно создать в разделе финансов."), color = AppColors.SecondaryText)
                    val choices = listOf("" to AppText.get("Не связано с работой")) + sources.map { it.key to it.name } +
                        listOfNotNull(existing?.sourceKey?.takeIf { key -> !key.startsWith("income:") && sources.none { it.key == key } }?.let { it to existing.sourceName.orEmpty() })
                    ChoiceField(AppText.get("Выплата за работу (необязательно)"), choices.find { it.first == sourceKey.orEmpty() }?.second ?: AppText.get("Не связано с работой"), choices) {
                        sourceKey = it.takeIf(String::isNotBlank); incomeCategoryId = null
                    }
                    Text(AppText.get("Только связь с работой уменьшает сумму «Заработано». Категория дохода сама по себе её не меняет."), color = AppColors.SecondaryText)
                }
                if (validation) Text(AppText.get("Укажи положительную сумму. Будущая дата доступна для расходов."), color = AppColors.Red)
                error?.let { Text(it, color = AppColors.Red) }
            }
        }, confirmButton = { Button(onClick = ::save) { Text(AppText.get("Сохранить")) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(AppText.get("Отмена")) } })
}
