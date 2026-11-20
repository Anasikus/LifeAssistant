package com.anasikus.lifeassistant.ui.components

import com.anasikus.lifeassistant.settings.AppText
import com.anasikus.lifeassistant.ui.assistantColorScheme
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anasikus.lifeassistant.data.*
import com.anasikus.lifeassistant.domain.CalendarDateUtils
import com.anasikus.lifeassistant.domain.PaymentPlanRules
import com.anasikus.lifeassistant.domain.ShiftRules
import com.anasikus.lifeassistant.notifications.PaymentReminders
import com.anasikus.lifeassistant.ui.AppColors
import com.anasikus.lifeassistant.viewmodel.CalendarViewModel
import kotlinx.coroutines.launch

@Composable
fun ChoiceField(label: String, value: String, choices: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(value, {}, readOnly = true, label = { Text(label) },
            modifier = Modifier.fillMaxWidth(), trailingIcon = { Text("▾") })
        Box(Modifier.matchParentSize().semantics { contentDescription = "$label: $value" }.clickable(role = Role.Button) { expanded = true })
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (key, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(key); expanded = false }) }
        }
    }
}


@Composable
fun ExpenseCategoryPicker(categories: List<ExpenseCategoryEntity>, selectedId: Int?, onSelect: (Int?) -> Unit) {
    ChoiceField(AppText.get("Категория расхода"), categories.find { it.id == selectedId }?.name ?: AppText.get("Без категории"),
        listOf("" to AppText.get("Без категории")) + categories.map { it.id.toString() to it.name }) { onSelect(it.toIntOrNull()) }
}

@Composable
fun IncomeCategoryPicker(categories: List<IncomeCategoryEntity>, selectedId: Int?, onSelect: (Int?) -> Unit) {
    ChoiceField(AppText.get("Категория дохода"), categories.find { it.id == selectedId }?.name ?: AppText.get("Без категории"),
        listOf("" to AppText.get("Без категории")) + categories.map { it.id.toString() to it.name }) { onSelect(it.toIntOrNull()) }
}

@Composable
fun ExpenseCategoriesDialog(viewModel: CalendarViewModel, onDismiss: () -> Unit) {
    val categories by viewModel.expenseCategories.collectAsState()
    val error by viewModel.financeError.collectAsState()
    CategoryManager(AppText.get("Категории расходов"), categories.map { it.id to it.name }, error,
        onSave = { id, name, saved -> viewModel.saveExpenseCategory(ExpenseCategoryEntity(id, name), saved) },
        onDelete = viewModel::deleteExpenseCategory, onDismiss = onDismiss)
}

@Composable
fun IncomeCategoriesDialog(viewModel: CalendarViewModel, onDismiss: () -> Unit) {
    val categories by viewModel.incomeCategories.collectAsState()
    val error by viewModel.financeError.collectAsState()
    CategoryManager(AppText.get("Категории доходов"), categories.map { it.id to it.name }, error,
        onSave = { id, name, saved -> viewModel.saveIncomeCategory(IncomeCategoryEntity(id, name), saved) },
        onDelete = viewModel::deleteIncomeCategory, onDismiss = onDismiss)
}

@Composable
internal fun CategoryManager(title: String, categories: List<Pair<Int, String>>, error: String?,
    onSave: (Int, String, () -> Unit) -> Unit, onDelete: (Int) -> Unit, onDismiss: () -> Unit, deleteMessage: String = AppText.get("Операции сохранятся без категории.")) {
    var editing by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var deleting by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var name by remember { mutableStateOf("") }
    var validation by remember { mutableStateOf(false) }
    val dirty = name != editing?.second.orEmpty()
    val valid = name.isNotBlank() && categories.none { it.first != editing?.first && it.second.equals(name.trim(), true) }
    // Replacing an unfinished draft is also a guarded dismissal.
    var nextAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun finish() { val action = nextAction; nextAction = null; if (action != null) action() else onDismiss() }
    fun save(after: () -> Unit) {
        if (!valid) validation = true
        else onSave(editing?.first ?: 0, name.trim()) { name = ""; editing = null; validation = false; after() }
    }
    val dismiss = rememberDismissGuard(dirty, onSave = { save { finish() } }, onDismiss = { finish() })
    AlertDialog(onDismissRequest = { nextAction = null; dismiss() }, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it; validation = false }, Modifier.weight(1f),
                    label = { Text(AppText.get("Название")) }, singleLine = true, isError = validation)
                Button(onClick = { save {} }, enabled = valid) { Text(if (editing == null) AppText.get("Добавить") else AppText.get("Сохранить")) }
            }
            if (validation) Text(AppText.get("Введи уникальное непустое название."), color = AppColors.Red)
            error?.let { Text(it, color = AppColors.Red) }
            categories.forEach { category ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(category.second)
                        Row {
                            TextButton(onClick = {
                                nextAction = { editing = category; name = category.second; validation = false }
                                dismiss()
                            }) { Text(AppText.get("Изменить")) }
                            TextButton(onClick = { deleting = category }) { Text(AppText.get("Удалить")) }
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { nextAction = null; dismiss() }) { Text(AppText.get("Закрыть")) } })
    deleting?.let { category ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text(AppText.get("Удалить «{arg0}»?", category.second)) },
            text = { Text(deleteMessage) }, confirmButton = {
                TextButton(onClick = {
                    onDelete(category.first)
                    if (editing?.first == category.first) { editing = null; name = "" }
                    deleting = null
                }) { Text(AppText.get("Удалить")) }
            }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(AppText.get("Отмена")) } })
    }
}

@Composable
fun PaymentPlansDialog(viewModel: CalendarViewModel, selectedDate: String, onDismiss: () -> Unit) {
    val plans by viewModel.paymentPlans.collectAsState()
    val categories by viewModel.expenseCategories.collectAsState()
    val transactions by viewModel.financeTransactions.collectAsState()
    val error by viewModel.financeError.collectAsState()
    val today = CalendarDateUtils.today()
    val paid = transactions.filter { !it.transfer && !it.income && it.date <= today }.mapNotNull { it.planKey }.toSet()
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
    MaterialTheme(colorScheme = assistantColorScheme(primary = AppColors.Accent, surface = AppColors.Card, onSurface = AppColors.MainText)) {
        AlertDialog(onDismissRequest = onDismiss, title = { Text(AppText.get("Регулярные платежи")) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!canNotify) TextButton(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(AppText.get("Разрешить уведомления")) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { month = CalendarDateUtils.addMonths(month, -1) }) { Text("‹") }
                    Text(CalendarDateUtils.formatMonth(month), Modifier.weight(1f), textAlign = TextAlign.Center)
                    TextButton(onClick = { month = CalendarDateUtils.addMonths(month, 1) }) { Text("›") }
                }
                error?.let { Text(it, color = AppColors.Red) }
                val occurrences = plans.flatMap { PaymentPlanRules.between(it, month, CalendarDateUtils.endOfMonth(month)) }.sortedBy { it.date }
                if (occurrences.isEmpty()) Text(AppText.get("На этот месяц платежей нет."))
                occurrences.forEach { payment ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.Background)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(payment.plan.title, style = MaterialTheme.typography.titleSmall)
                            Text("${CalendarDateUtils.formatDate(payment.date)} · ${ShiftRules.money(payment.plan.amountCents)}")
                            categories.find { it.id == payment.plan.categoryId }?.let { Text(it.name, color = AppColors.SecondaryText) }
                            if (payment.key in paid) Text(AppText.get("Оплачено"), color = AppColors.Green)
                            else TextButton(onClick = {
                                viewModel.addFinanceTransaction(FinanceTransactionEntity(date = today, income = false,
                                    amountCents = payment.plan.amountCents, note = payment.plan.title,
                                    expenseCategoryId = payment.plan.categoryId, planKey = payment.key))
                            }) { Text(AppText.get("Оплачено сегодня")) }
                        }
                    }
                }
                if (plans.isNotEmpty()) Text(AppText.get("Настройки платежей"), style = MaterialTheme.typography.titleSmall)
                plans.forEach { plan ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.Background)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(plan.title, style = MaterialTheme.typography.titleSmall)
                            Text(PaymentPlanRules.label(plan), color = AppColors.SecondaryText)
                            Text(AppText.get("Напоминание за {arg0} дн. в %02d:%02d", plan.reminderDays).format(plan.reminderMinute / 60, plan.reminderMinute % 60), color = AppColors.SecondaryText)
                            if (plan.reminderRepeatDays > 0) Text(AppText.get("Повтор каждые {arg0} дн. до платежа", plan.reminderRepeatDays), color = AppColors.SecondaryText)
                            Row {
                                TextButton(onClick = { editing = plan; editorOpen = true }) { Text(AppText.get("Изменить")) }
                                TextButton(onClick = { deleting = plan }) { Text(AppText.get("Удалить")) }
                            }
                        }
                    }
                }
            }
        }, confirmButton = {
            Column(Modifier.fillMaxWidth()) {
                Button(onClick = { editing = null; editorOpen = true }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)) { Text(AppText.get("＋ Добавить платёж")) }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(AppText.get("Закрыть"), color = AppColors.SecondaryText) }
            }
        })
        if (editorOpen) PaymentPlanEditor(editing, selectedDate, categories, error, onDismiss = { editorOpen = false }) {
            viewModel.savePaymentPlan(it) { editorOpen = false }
        }
        deleting?.let { plan ->
            AlertDialog(onDismissRequest = { deleting = null }, title = { Text(AppText.get("Удалить «{arg0}»?", plan.title)) },
                text = { Text(AppText.get("Оплаченные расходы останутся в истории.")) }, confirmButton = {
                    TextButton(onClick = { viewModel.deletePaymentPlan(plan.id); deleting = null }) { Text(AppText.get("Удалить")) }
                }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(AppText.get("Отмена")) } })
        }
    }
}

private val repeatPresets get() = listOf("WEEK:1" to AppText.get("Каждую неделю"), "MONTH:1" to AppText.get("Каждый месяц"),
    "MONTH:3" to AppText.get("Каждые 3 месяца"), "YEAR:1" to AppText.get("Каждый год"), "CUSTOM" to AppText.get("Другое"))
private val repeatUnits get() = listOf("DAY" to AppText.get("День"), "WEEK" to AppText.get("Неделя"), "MONTH" to AppText.get("Месяц"), "YEAR" to AppText.get("Год"))
private val reminderPresets get() = listOf("1" to AppText.get("За день"), "3" to AppText.get("За 3 дня"), "7" to AppText.get("За неделю"), "14" to AppText.get("За 2 недели"), "CUSTOM" to AppText.get("Другое"))

@Composable
internal fun PaymentPlanEditor(plan: PaymentPlanEntity?, initialDate: String, categories: List<ExpenseCategoryEntity>,
    error: String? = null, onDismiss: () -> Unit, onSave: (PaymentPlanEntity) -> Unit) {
    var title by remember { mutableStateOf(plan?.title.orEmpty()) }
    var amount by remember { mutableStateOf(plan?.amountCents?.let { java.math.BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty()) }
    var date by remember { mutableStateOf(plan?.startDate ?: initialDate) }
    val initialUnit = plan?.repeatUnit ?: "MONTH"
    val initialCount = plan?.let(PaymentPlanRules::interval) ?: 1
    val initialPreset = "$initialUnit:$initialCount".takeIf { key -> repeatPresets.any { it.first == key } } ?: "CUSTOM"
    var preset by remember { mutableStateOf(initialPreset) }
    var count by remember { mutableStateOf(initialCount.toString()) }
    var unit by remember { mutableStateOf(initialUnit) }
    var reminder by remember { mutableStateOf((plan?.reminderDays ?: 3).toString().takeIf { key -> reminderPresets.any { it.first == key } } ?: "CUSTOM") }
    var customReminder by remember { mutableStateOf((plan?.reminderDays ?: 3).toString()) }
    var minute by remember { mutableStateOf(plan?.reminderMinute ?: 540) }
    var repeating by remember { mutableStateOf((plan?.reminderRepeatDays ?: 0) > 0) }
    var repeatDays by remember { mutableStateOf((plan?.reminderRepeatDays?.takeIf { it > 0 } ?: 1).toString()) }
    var categoryId by remember { mutableStateOf(plan?.categoryId) }
    var validation by remember { mutableStateOf(false) }
    val cents = ShiftRules.cents(amount)
    val every = if (preset == "CUSTOM") count.toIntOrNull() else preset.substringAfter(':').toIntOrNull()
    val effectiveUnit = if (preset == "CUSTOM") unit else preset.substringBefore(':')
    val lead = (if (reminder == "CUSTOM") customReminder else reminder).toIntOrNull()
    val repeat = if (repeating) repeatDays.toIntOrNull() else 0
    val snapshot = listOf(title, amount, date, preset, count, unit, reminder, customReminder, minute, repeating, repeatDays, categoryId)
    val initial = remember { snapshot }
    fun save() {
        if (title.isBlank() || cents == null || cents <= 0 || every == null || every !in 1..365 ||
            lead == null || lead !in 0..365 || repeat == null || repeat !in (if (repeating) 1..365 else 0..0)) validation = true
        else onSave(PaymentPlanEntity(plan?.id ?: 0, title.trim(), cents, date,
            if (effectiveUnit == "MONTH") every else 1, lead, categoryId?.takeIf { id -> categories.any { it.id == id } },
            effectiveUnit, every, minute, repeat))
    }
    val dismiss = rememberDismissGuard(snapshot != initial, ::save, onDismiss)
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (plan == null) AppText.get("Новый регулярный платёж") else AppText.get("Изменить платёж")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text(AppText.get("Название")) }, singleLine = true)
            OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text(AppText.get("Сумма, ₽")) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedDateField(AppText.get("Дата первого платежа"), date) { date = it }
            ChoiceField(AppText.get("Повторять"), repeatPresets.first { it.first == preset }.second, repeatPresets) { preset = it }
            if (preset == "CUSTOM") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(count, { count = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), label = { Text(AppText.get("Каждые")) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Box(Modifier.weight(1f)) { ChoiceField(AppText.get("Период"), repeatUnits.first { it.first == unit }.second, repeatUnits) { unit = it } }
            }
            ChoiceField(AppText.get("Напомнить"), reminderPresets.first { it.first == reminder }.second, reminderPresets) { reminder = it }
            if (reminder == "CUSTOM") OutlinedTextField(customReminder, { customReminder = it.filter(Char::isDigit).take(3) },
                Modifier.fillMaxWidth(), label = { Text(AppText.get("За сколько дней (0 — в день платежа)")) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTimeField(AppText.get("Время напоминания"), minute) { minute = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(repeating, onCheckedChange = { repeating = it }); Text(AppText.get("Повторять напоминание"))
            }
            if (repeating) {
                OutlinedTextField(repeatDays, { repeatDays = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(),
                    label = { Text(AppText.get("Повтор каждые N дней")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Text(AppText.get("В выбранное время, до даты платежа включительно. После оплаты напоминания прекращаются."), color = AppColors.SecondaryText)
            }
            ExpenseCategoryPicker(categories, categoryId) { categoryId = it }
            if (validation) Text(AppText.get("Проверь название, сумму и интервалы: повтор 1–365, напоминание 0–365 дней."), color = AppColors.Red)
            error?.let { Text(it, color = AppColors.Red) }
        }
    }, confirmButton = { Button(onClick = ::save) { Text(AppText.get("Сохранить")) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(AppText.get("Отмена")) } })
}
