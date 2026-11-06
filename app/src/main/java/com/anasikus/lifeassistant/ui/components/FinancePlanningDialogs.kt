package com.anasikus.lifeassistant.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.anasikus.lifeassistant.data.ExpenseCategoryEntity
import com.anasikus.lifeassistant.data.FinanceTransactionEntity
import com.anasikus.lifeassistant.data.PaymentPlanEntity
import com.anasikus.lifeassistant.domain.CalendarDateUtils
import com.anasikus.lifeassistant.domain.PaymentPlanRules
import com.anasikus.lifeassistant.domain.ShiftRules
import com.anasikus.lifeassistant.notifications.PaymentReminders
import com.anasikus.lifeassistant.ui.AppColors
import com.anasikus.lifeassistant.viewmodel.CalendarViewModel
import kotlinx.coroutines.launch

@Composable
fun ExpenseCategoryPicker(categories: List<ExpenseCategoryEntity>, selectedId: Int?, onSelect: (Int?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(categories.find { it.id == selectedId }?.name ?: "Без категории")
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Без категории") }, onClick = { onSelect(null); expanded = false })
            categories.forEach { category ->
                DropdownMenuItem(text = { Text(category.name) }, onClick = { onSelect(category.id); expanded = false })
            }
        }
    }
}

@Composable
fun ExpenseCategoriesDialog(viewModel: CalendarViewModel, onDismiss: () -> Unit) {
    val categories by viewModel.expenseCategories.collectAsState()
    var editing by remember { mutableStateOf<ExpenseCategoryEntity?>(null) }
    var deleting by remember { mutableStateOf<ExpenseCategoryEntity?>(null) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Категории расходов") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название категории") }, singleLine = true)
                TextButton(
                    enabled = name.isNotBlank() && categories.none { it.id != editing?.id && it.name.equals(name.trim(), ignoreCase = true) },
                    onClick = {
                        viewModel.saveExpenseCategory(ExpenseCategoryEntity(editing?.id ?: 0, name.trim()))
                        editing = null
                        name = ""
                    }
                ) { Text(if (editing == null) "Добавить" else "Сохранить") }
                categories.forEach { category ->
                    Text(category.name)
                    Row {
                        TextButton(onClick = { editing = category; name = category.name }) { Text("Изменить") }
                        TextButton(onClick = { deleting = category }) { Text("Удалить") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
    deleting?.let { category ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить «${category.name}»?") },
            text = { Text("Её платежи и расходы будут показаны без категории.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteExpenseCategory(category.id)
                    if (editing?.id == category.id) { editing = null; name = "" }
                    deleting = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } }
        )
    }
}

@Composable
fun PaymentPlansDialog(viewModel: CalendarViewModel, selectedDate: String, onDismiss: () -> Unit) {
    val plans by viewModel.paymentPlans.collectAsState()
    val categories by viewModel.expenseCategories.collectAsState()
    val transactions by viewModel.financeTransactions.collectAsState()
    val paid = transactions.mapNotNull { it.planKey }.toSet()
    val today = CalendarDateUtils.today()
    var month by remember(selectedDate) { mutableStateOf(CalendarDateUtils.startOfMonth(selectedDate)) }
    var editing by remember { mutableStateOf<PaymentPlanEntity?>(null) }
    var deleting by remember { mutableStateOf<PaymentPlanEntity?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var canNotify by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = granted
        if (granted) scope.launch { PaymentReminders.refresh(context.applicationContext) }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = AppColors.Accent, surface = AppColors.Card)) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Регулярные платежи") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!canNotify) {
                        TextButton(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Разрешить уведомления") }
                    }
                    OutlinedButton(onClick = { editing = null; editorOpen = true }) { Text("Добавить платёж") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { month = CalendarDateUtils.addMonths(month, -1) }) { Text("‹") }
                        Text(CalendarDateUtils.formatMonth(month), Modifier.weight(1f))
                        TextButton(onClick = { month = CalendarDateUtils.addMonths(month, 1) }) { Text("›") }
                    }
                    val occurrences = plans.flatMap { PaymentPlanRules.between(it, month, CalendarDateUtils.endOfMonth(month)) }.sortedBy { it.date }
                    if (occurrences.isEmpty()) Text("На этот месяц платежей нет.")
                    occurrences.forEach { payment ->
                        Text("${CalendarDateUtils.formatDate(payment.date)} · ${payment.plan.title} · ${ShiftRules.money(payment.plan.amountCents)}")
                        if (payment.key in paid) {
                            Text("Оплачено", color = AppColors.Green)
                        } else {
                            TextButton(onClick = {
                                viewModel.addFinanceTransaction(
                                    FinanceTransactionEntity(
                                        date = today, income = false, amountCents = payment.plan.amountCents,
                                        note = payment.plan.title, expenseCategoryId = payment.plan.categoryId,
                                        planKey = payment.key
                                    )
                                )
                            }) { Text("Отметить оплаченным сегодня") }
                        }
                    }
                    if (plans.isNotEmpty()) Text("Настройки платежей", style = MaterialTheme.typography.titleSmall)
                    plans.forEach { plan ->
                        Text("${plan.title} · каждые ${plan.everyMonths} мес.")
                        Row {
                            TextButton(onClick = { editing = plan; editorOpen = true }) { Text("Изменить") }
                            TextButton(onClick = { deleting = plan }) { Text("Удалить") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
        )
        if (editorOpen) {
            PaymentPlanEditor(editing, selectedDate, categories, onDismiss = { editorOpen = false }) {
                viewModel.savePaymentPlan(it)
                editorOpen = false
            }
        }
        deleting?.let { plan ->
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text("Удалить «${plan.title}»?") },
                text = { Text("Уже оплаченные расходы останутся в истории.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.deletePaymentPlan(plan.id); deleting = null }) { Text("Удалить") }
                },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } }
            )
        }
    }
}

@Composable
private fun PaymentPlanEditor(
    plan: PaymentPlanEntity?,
    initialDate: String,
    categories: List<ExpenseCategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (PaymentPlanEntity) -> Unit
) {
    var title by remember { mutableStateOf(plan?.title.orEmpty()) }
    var amount by remember { mutableStateOf(plan?.amountCents?.let { java.math.BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty()) }
    var date by remember { mutableStateOf(plan?.startDate ?: initialDate) }
    var interval by remember { mutableStateOf((plan?.everyMonths ?: 1).toString()) }
    var reminder by remember { mutableStateOf((plan?.reminderDays ?: 3).toString()) }
    var categoryId by remember { mutableStateOf(plan?.categoryId) }
    val cents = ShiftRules.cents(amount)
    val months = interval.toIntOrNull()?.takeIf { it in 1..24 }
    val days = reminder.toIntOrNull()?.takeIf { it in 0..365 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (plan == null) "Новый регулярный платёж" else "Изменить платёж") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("Сумма, ₽") }, singleLine = true)
                PlanningDateField("Дата первого платежа", date) { date = it }
                OutlinedTextField(interval, { interval = it.filter(Char::isDigit).take(2) }, label = { Text("Каждые N месяцев (1–24)") }, singleLine = true)
                OutlinedTextField(reminder, { reminder = it.filter(Char::isDigit).take(3) }, label = { Text("Напомнить за N дней (0–365)") }, singleLine = true)
                Text("0 — в день платежа. Напоминания запланированы на 09:00.")
                ExpenseCategoryPicker(categories, categoryId) { categoryId = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && cents != null && cents > 0L && months != null && days != null,
                onClick = {
                    onSave(PaymentPlanEntity(plan?.id ?: 0, title.trim(), checkNotNull(cents), date,
                        checkNotNull(months), checkNotNull(days), categoryId))
                }
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun FinanceStatisticsDialog(
    transactions: List<FinanceTransactionEntity>,
    categories: List<ExpenseCategoryEntity>,
    month: String,
    cutoff: String,
    onDismiss: () -> Unit
) {
    val periods = listOf("Неделя" to 0, "Месяц" to 1, "3 месяца" to 3, "Полгода" to 6, "Год" to 12)
    var months by remember { mutableStateOf(1) }
    var menu by remember { mutableStateOf(false) }
    val start = if (months == 0) CalendarDateUtils.addDays(cutoff, -6) else CalendarDateUtils.addMonths(month, 1 - months)
    val rows = transactions.filter { it.date in start..cutoff }
    val expenses = rows.filterNot { it.income }.groupingBy { it.expenseCategoryId }
        .fold(0L) { total, entry -> total + entry.amountCents }
    val incomes = rows.filter { it.income }.groupingBy { it.sourceName ?: "Прочий доход" }
        .fold(0L) { total, entry -> total + entry.amountCents }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Статистика") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { menu = true }) { Text(periods.first { it.second == months }.first) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        periods.forEach { (label, count) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { months = count; menu = false })
                        }
                    }
                }
                Text("${CalendarDateUtils.formatDate(start)} – ${CalendarDateUtils.formatDate(cutoff)}")
                Text("Расход · ${ShiftRules.money(expenses.values.sum())}", style = MaterialTheme.typography.titleSmall)
                expenses.entries.sortedByDescending { it.value }.forEach { (id, amount) ->
                    Text("${categories.find { it.id == id }?.name ?: "Без категории"} · ${ShiftRules.money(amount)}")
                }
                Text("Доход · ${ShiftRules.money(incomes.values.sum())}", style = MaterialTheme.typography.titleSmall)
                incomes.entries.sortedByDescending { it.value }.forEach { (name, amount) ->
                    Text("$name · ${ShiftRules.money(amount)}")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}
