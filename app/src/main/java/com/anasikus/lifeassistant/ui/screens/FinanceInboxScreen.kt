package com.anasikus.lifeassistant.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anasikus.lifeassistant.data.FinanceInboxEntity
import com.anasikus.lifeassistant.data.FinanceImportRuleEntity
import com.anasikus.lifeassistant.data.ExpenseCategoryEntity
import com.anasikus.lifeassistant.domain.CalendarDateUtils
import com.anasikus.lifeassistant.domain.FinanceMessageParser
import com.anasikus.lifeassistant.domain.FinanceSource
import com.anasikus.lifeassistant.domain.ShiftRules
import com.anasikus.lifeassistant.notifications.FinanceCaptureSettings
import com.anasikus.lifeassistant.notifications.FinanceMessageCapture
import com.anasikus.lifeassistant.ui.AppColors
import com.anasikus.lifeassistant.ui.components.ExpenseCategoryPicker
import com.anasikus.lifeassistant.ui.components.ExpenseCategoriesDialog
import com.anasikus.lifeassistant.ui.components.PlanningDateField
import com.anasikus.lifeassistant.ui.components.rememberDismissGuard
import com.anasikus.lifeassistant.ui.components.ChoiceField
import com.anasikus.lifeassistant.data.IncomeCategoryEntity
import com.anasikus.lifeassistant.viewmodel.CalendarViewModel
import kotlinx.coroutines.launch

@Composable
fun FinanceInboxScreen(viewModel: CalendarViewModel, sources: List<FinanceSource>, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    val messages by viewModel.inboxMessages.collectAsStateWithLifecycle()
    val rules by viewModel.importRules.collectAsStateWithLifecycle()
    val categories by viewModel.expenseCategories.collectAsStateWithLifecycle()
    val incomeCategories by viewModel.incomeCategories.collectAsStateWithLifecycle()
    val error by viewModel.inboxError.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<FinanceInboxEntity?>(null) }
    var deleting by remember { mutableStateOf<FinanceInboxEntity?>(null) }
    var ignoring by remember { mutableStateOf<FinanceInboxEntity?>(null) }
    var senderSettings by remember { mutableStateOf<Pair<String, String>?>(null) }
    var filter by rememberSaveable { mutableStateOf("PENDING") }
    val listState = rememberLazyListState()
    LaunchedEffect(filter) { listState.scrollToItem(0) }
    var showAccess by remember { mutableStateOf(false) }
    var showSenders by remember { mutableStateOf(false) }
    val counts = remember(messages) { messages.groupingBy { it.status }.eachCount() }
    val shown by key(filter) {
        remember(filter) { viewModel.inboxForStatus(filter) }.collectAsStateWithLifecycle(initialValue = emptyList())
    }
    val ruleMap = remember(rules) { rules.associateBy { it.key } }
    fun senderScope(source: String, sender: String): Pair<String, String> =
        FinanceMessageParser.appPackage(source, sender)?.let { "APP" to it } ?: (source to sender)
    var showRules by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme = darkColorScheme(primary = AppColors.Accent, surface = AppColors.Card)) {
        LazyColumn(
            modifier.background(AppColors.Background), state = listState, contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                TextButton(onClick = onBack) { Text("‹ Финансы") }
                Text("Уведомления", style = MaterialTheme.typography.headlineSmall)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = { showAccess = true }) { Text("Источники") }
                    TextButton(onClick = { showSenders = true }) { Text("Отправители") }
                    TextButton(onClick = { showRules = true }) { Text("Правила") }
                    TextButton(onClick = { showImport = true }) { Text("Вставить текст") }
                }
                TextButton(onClick = { showCategories = true }) { Text("Категории расходов") }
                error?.let { Text(it, color = AppColors.Red) }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("PENDING" to "В обработке", "POSTED" to "Учтено", "SAVED" to "Сохранено", "IGNORED" to "Не нужно").forEach { (status, label) ->
                        TextButton(onClick = { filter = status }) {
                            Text("$label (${counts[status] ?: 0})", color = if (status == filter) AppColors.Accent else AppColors.SecondaryText)
                        }
                    }
                }
            }
            if (shown.isEmpty()) item {
                Text(when (filter) {
                    "POSTED" -> "Здесь появятся подтверждённые уведомления с операциями."
                    "SAVED" -> "Здесь появятся сообщения, сохранённые без операции в финансах."
                    "IGNORED" -> "Здесь останутся отмеченные ненужными сообщения; их похожие копии удаляются."
                    else -> "Новых сообщений для проверки пока нет."
                }, color = AppColors.SecondaryText)
            }
            items(shown, key = { it.id }) { message ->
                Card(colors = CardDefaults.cardColors(containerColor = AppColors.Card)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("${sourceLabel(message.source)} · ${message.sender}", color = AppColors.SecondaryText)
                        val scope = senderScope(message.source, message.sender)
                        val senderRule = ruleMap[FinanceMessageParser.senderKey(scope.first, scope.second)]
                            ?: ruleMap[FinanceMessageParser.senderKey(message.source, message.sender)]
                        if (senderRule != null) Text(
                            (senderRule.bankName?.let { "$it · " }.orEmpty()) + if (senderRule.action == "FINANCIAL") "Финансовый отправитель" else "Сбор отключён",
                            color = AppColors.SecondaryText
                        )
                        TextButton(onClick = { senderSettings = senderScope(message.source, message.sender) }) { Text("Настроить отправителя") }
                        Text(message.title.ifBlank { "Сообщение" }, color = AppColors.MainText)
                        Text(message.body, maxLines = 5, color = AppColors.SecondaryText)
                        Text(CalendarDateUtils.formatDate(FinanceMessageParser.date(message.receivedAt)), color = AppColors.SecondaryText)
                        message.proposedAmountCents?.let { amount ->
                            val direction = if (message.proposedTransfer) "Перевод" else when (message.proposedIncome) { true -> "Доход"; false -> "Расход"; null -> "Уточни тип операции" }
                            Text("$direction · ${ShiftRules.money(amount)}", color = AppColors.MainText)
                        }
                        if (message.automatic) Text("Добавлено по твоему правилу", color = AppColors.Green)
                        if (message.possibleDuplicate) Text("Есть похожая операция за последние две минуты. Проверь, не повторяется ли сообщение.", color = AppColors.Yellow)
                        Row {
                            TextButton(onClick = { selected = message }) { Text(if (message.status == "POSTED") "Изменить" else "Проверить") }
                            TextButton(onClick = { ignoring = message }) { Text("Не нужно") }
                            TextButton(onClick = { deleting = message }) { Text("Удалить") }
                        }
                    }
                }
            }
        }
        selected?.let { message ->
            ReviewMessageDialog(message, rules.find { it.key == message.ruleKey }, categories, sources, error, incomeCategories,
                onKeep = { title, body -> viewModel.keepInbox(message.id, title, body) { selected = null; filter = "SAVED" } },
                onDismiss = { selected = null }) { title, body, income, amount, date, category, source, learn, textFilter, transfer ->
                viewModel.confirmInbox(message.id, title, body, income, amount, date, category, source?.key, source?.name, learn, textFilter, transfer) { selected = null; filter = "POSTED" }
            }
        }
        ignoring?.let { message ->
            AlertDialog(
                onDismissRequest = { ignoring = null }, title = { Text("Считать сообщение ненужным?") },
                text = { Column {
                    Text("Выбранное сообщение останется в «Не нужно». «Удалить похожие» очистит остальные неучтённые сообщения этого шаблона от того же отправителя и отключит их дальнейший сбор. Другие учтённые операции сохранятся. Операция выбранного сообщения, если она есть, будет удалена.")
                    error?.let { Text(it, color = AppColors.Red) }
                } },
                confirmButton = { TextButton(onClick = { viewModel.ignoreInbox(message.id, true) { ignoring = null } }) { Text("Удалить похожие") } },
                dismissButton = {
                    Row {
                        TextButton(onClick = { viewModel.ignoreInbox(message.id, false) { ignoring = null } }) { Text("Только это") }
                        TextButton(onClick = { ignoring = null }) { Text("Отмена") }
                    }
                }
            )
        }
        deleting?.let { message ->
            AlertDialog(
                onDismissRequest = { deleting = null }, title = { Text("Удалить сообщение и его операцию?") },
                text = { Text(if (rules.find { it.key == message.ruleKey }?.action in listOf("IGNORE", "KEEP"))
                    "Текст удалится. Запомненное правило сохранится; изменить его можно во вкладке «Правила»."
                    else "Текст и связанная операция удалятся. Следующее похожее сообщение придёт на проверку.") },
                confirmButton = { TextButton(onClick = { viewModel.deleteInbox(message.id); deleting = null }) { Text("Удалить") } },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } }
            )
        }
        if (showAccess) CaptureAccessDialog { showAccess = false }
        if (showSenders) FinanceSendersDialog(messages, rules, error,
            onSettings = { source, sender -> senderSettings = source to sender; showSenders = false },
            onBlock = { source, sender -> viewModel.configureFinanceSender(source, sender, "", false) {} },
            onDismiss = { showSenders = false })
        if (showRules) ImportRulesDialog(rules, categories, viewModel::forgetImportRule,
            onSender = { source, sender -> senderSettings = senderScope(source, sender); showRules = false },
            onEdit = { rule -> messages.firstOrNull { it.ruleKey == rule.key }?.let { selected = it; showRules = false } }) { showRules = false }
        senderSettings?.let { (source, sender) ->
            SenderSettingsDialog(source, sender,
                ruleMap[FinanceMessageParser.senderKey(source, sender)]
                    ?: if (source == "APP") ruleMap[FinanceMessageParser.senderKey("NOTIFICATION", sender)] else null, error,
                onDismiss = { senderSettings = null }) { bank, financial ->
                viewModel.configureFinanceSender(source, sender, bank, financial) { senderSettings = null }
            }
        }
        if (showImport) PasteMessageDialog { showImport = false }
        if (showCategories) ExpenseCategoriesDialog(viewModel) { showCategories = false }
    }
}

@Composable
private fun ReviewMessageDialog(
    message: FinanceInboxEntity,
    existingRule: FinanceImportRuleEntity?,
    categories: List<ExpenseCategoryEntity>,
    sources: List<FinanceSource>,
    error: String?,
    incomeCategories: List<IncomeCategoryEntity>,
    onKeep: (String, String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Boolean, Long, String, Int?, FinanceSource?, Boolean, String?, Boolean) -> Unit
) {
    var step by remember(message.id) { mutableStateOf(0) }
    var title by remember(message.id) { mutableStateOf(message.title) }
    var body by remember(message.id) { mutableStateOf(message.body) }
    val reparsed = remember(message.id) { FinanceMessageParser.message(message.title, message.body, message.source) }
    var income by remember(message.id) { mutableStateOf(message.proposedIncome ?: reparsed.income) }
    var transfer by remember(message.id) { mutableStateOf(
        if (message.status == "POSTED" || existingRule?.transfer != null) message.proposedTransfer
        else message.proposedTransfer || reparsed.transfer
    ) }
    var amount by remember(message.id) { mutableStateOf((message.proposedAmountCents ?: reparsed.preferredAmount)?.let { java.math.BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty()) }
    var date by remember(message.id) { mutableStateOf(message.transactionDate ?: FinanceMessageParser.date(message.receivedAt)) }
    var category by remember(message.id) { mutableStateOf(message.expenseCategoryId) }
    var source by remember(message.id) { mutableStateOf(sources.find { it.key == message.incomeSourceKey } ?: message.incomeSourceKey?.let { FinanceSource(it, message.incomeSourceName.orEmpty(), 0L) }) }
    var learn by remember(message.id) { mutableStateOf(true) }
    var sourceMenu by remember { mutableStateOf(false) }
    var textFilter by remember(message.id) { mutableStateOf(existingRule?.textFilter.orEmpty()) }
    var customIncome by remember(message.id) { mutableStateOf(if (message.incomeSourceKey?.startsWith("income:") == true) message.incomeSourceName.orEmpty() else "") }
    val filterValid = textFilter.isBlank() || FinanceMessageParser.matchesFilter(textFilter, title, body)
    val cents = ShiftRules.cents(amount)
    val draft = listOf(title, body, income, transfer, amount, date, category, source, learn, textFilter, customIncome)
    val initial = remember(message.id) { draft }
    var validation by remember { mutableStateOf(false) }
    fun save() {
        if ((income == null && !transfer) || cents == null || cents <= 0 || date > CalendarDateUtils.today() || !filterValid) validation = true
        else onConfirm(title, body, if (transfer) false else checkNotNull(income), cents, date, category,
            if (customIncome.isNotBlank()) FinanceSource("income:" + FinanceMessageParser.hash(customIncome.trim().lowercase(java.util.Locale.ROOT)), customIncome.trim(), 0L) else source,
            learn, textFilter.takeIf { it.isNotBlank() }, transfer)
    }
    val dismiss = rememberDismissGuard(draft != initial, ::save, onDismiss)
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (step == 0) "Проверка операции" else "Категория и обучение") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (step == 0) {
                    OutlinedTextField(title, { title = it }, label = { Text("Заголовок") }, singleLine = true)
                    OutlinedTextField(body, { body = it }, label = { Text("Текст сообщения") }, minLines = 2, maxLines = 6)
                    TextButton(onClick = {
                        val parsed = FinanceMessageParser.message(title, body, message.source)
                        income = parsed.income
                        transfer = parsed.transfer
                        amount = parsed.preferredAmount?.let { java.math.BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty()
                    }) { Text("Разобрать текст заново") }
                    FinanceDirectionButtons(income, transfer) { nextIncome, nextTransfer -> income = nextIncome; transfer = nextTransfer }
                    OutlinedTextField(amount, { amount = it }, label = { Text("Сумма, ₽") }, singleLine = true)
                    PlanningDateField("Дата операции", date) { date = it }
                    if (message.possibleDuplicate) Text("Проверь похожую операцию в истории перед подтверждением.", color = AppColors.Yellow)
                    TextButton(onClick = { onKeep(title, body) }) { Text("Сохранить без операции в финансах") }
                    if (message.body.contains("перевод", ignoreCase = true)) Text("Тип «Перевод» сохраняет операцию в истории, но не меняет доход, расход и остаток.", color = AppColors.SecondaryText)
                    if (message.source == "EMAIL" && cents == null) Text("В заголовке нет суммы. Для Ozon пока введи итог из чека вручную.", color = AppColors.Yellow)
                    Text("Автоматическая дата — день получения сообщения. Проверь направление и сумму. Если валюта другая, введи сумму в рублях вручную.", color = AppColors.SecondaryText)
                } else {
                    Text("${if (transfer) "Перевод" else if (income == true) "Доход" else "Расход"} · ${ShiftRules.money(cents ?: 0)}")
                    if (transfer) Text("Не учитывается в доходах, расходах, остатке и выплатах по работам.")
                    if (!transfer && income == false) ExpenseCategoryPicker(categories, category) { category = it }
                    else if (!transfer) {
                        Box {
                            OutlinedButton(onClick = { sourceMenu = true }) { Text(source?.name ?: "Прочий доход") }
                            DropdownMenu(sourceMenu, onDismissRequest = { sourceMenu = false }) {
                                DropdownMenuItem(text = { Text("Прочий доход") }, onClick = { source = null; customIncome = ""; sourceMenu = false })
                                sources.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { source = item; customIncome = ""; sourceMenu = false }) }
                            }
                        }
                    }
                    if (!transfer && income == true && incomeCategories.isNotEmpty()) ChoiceField("Категория дохода", customIncome.ifBlank { "Выбрать категорию" },
                        listOf("" to "Без категории") + incomeCategories.map { it.name to it.name }) {
                        customIncome = it; source = null
                    }
                    if (!transfer && income == true) OutlinedTextField(customIncome, {
                        customIncome = it
                        if (it.isNotBlank()) source = null
                    }, label = { Text("Своя категория дохода (необязательно)") }, singleLine = true)
                    OutlinedTextField(textFilter, { textFilter = it }, label = { Text("Текст для правила (необязательно)") },
                        supportingText = { Text("Например: из Т-Банка и на новой строке от Иванова. Все строки должны встречаться в сообщении. Для магазина достаточно АГРОТОРГ. Учитывается тот же тип операции.") },
                        isError = !filterValid)
                    if (!filterValid) Text("Этот текст должен присутствовать в текущем сообщении.", color = AppColors.Red)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(learn, onCheckedChange = { learn = it })
                        Text("Запомнить для похожих сообщений")
                    }
                    val matchingAmounts = FinanceMessageParser.message(title, body, message.source).amounts.count { !it.balance && it.cents == cents }
                    if (learn && matchingAmounts != 1) {
                        Text("Сумма не выделяется однозначно. Похожие сообщения будут приходить на проверку.", color = AppColors.Yellow)
                    }
                    Text("Похожий источник и шаблон будут получать выбранные тип и категорию. Если сумму нельзя однозначно прочитать, сообщение снова придёт на проверку.", color = AppColors.SecondaryText)
                }
                if (validation) Text("Проверь направление, сумму, дату и текст правила.", color = AppColors.Red)
                error?.let { Text(it, color = AppColors.Red) }
            }
        },
        confirmButton = {
            TextButton(enabled = (income != null || transfer) && cents != null && cents > 0 && date <= CalendarDateUtils.today() && filterValid, onClick = {
                if (step == 0) step = 1 else save()
            }) { Text(if (step == 0) "Да, всё верно" else "Сохранить") }
        },
        dismissButton = { TextButton(onClick = { if (step > 0) step = 0 else dismiss() }) { Text(if (step > 0) "Назад" else "Отмена") } }
    )
}

@Composable
private fun CaptureAccessDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var healthRevision by remember { mutableStateOf(0) }
    DisposableEffect(context) {
        val stopObserving = FinanceCaptureSettings.observe(context) { healthRevision++ }
        onDispose { stopObserving() }
    }
    val connected = remember(healthRevision) { FinanceCaptureSettings.isConnected(context) }
    var notificationAccess by remember { mutableStateOf(FinanceCaptureSettings.hasNotificationAccess(context)) }
    var sms by remember { mutableStateOf(FinanceCaptureSettings.enabled(context, "SMS")) }
    var email by remember { mutableStateOf(FinanceCaptureSettings.enabled(context, "EMAIL")) }
    var notifications by remember { mutableStateOf(FinanceCaptureSettings.enabled(context, "NOTIFICATION")) }
    var smsPermission by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAccess = FinanceCaptureSettings.hasNotificationAccess(context)
                smsPermission = context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        smsPermission = granted
        sms = granted
        FinanceCaptureSettings.setEnabled(context, "SMS", granted)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Источники сообщений") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CaptureToggle("Новые SMS", sms && smsPermission) { enabled ->
                    if (enabled && !smsPermission) permission.launch(Manifest.permission.RECEIVE_SMS)
                    else { sms = enabled; FinanceCaptureSettings.setEnabled(context, "SMS", enabled) }
                }
                if (!smsPermission) Text("Если Android не разрешает SMS, сообщения можно получать через уведомления SMS-приложения или вставлять вручную.")
                CaptureToggle("Почта через уведомления", email) { email = it; FinanceCaptureSettings.setEnabled(context, "EMAIL", it) }
                CaptureToggle("Пуш-уведомления приложений", notifications) { notifications = it; FinanceCaptureSettings.setEnabled(context, "NOTIFICATION", it) }
                Text("Яндекс Пэй, банки и другие приложения. Новые сообщения попадают на проверку; ненужных отправителей можно отключить, а для операций — задать правила.")
                Text(if (notificationAccess) "Доступ к уведомлениям разрешён" else "Требуется доступ к уведомлениям в Android")
                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Настройки доступа Android") }
                Text(if (connected) "Последнее состояние обработчика: подключён" else "Последнее состояние обработчика: отключён")
                OutlinedButton(enabled = notificationAccess, onClick = { FinanceCaptureSettings.reconnect(context) }) { Text("Переподключить обработчик") }
                Text("Последний проверенный пуш:")
                Text(remember(healthRevision) { FinanceCaptureSettings.pushDetails(context) }, color = AppColors.SecondaryText)
                listOf("SMS" to "SMS", "NOTIFICATION" to "Пуши", "EMAIL" to "Почта").forEach { (key, label) ->
                    val last = FinanceCaptureSettings.lastProcessed(context, key)
                    Text("$label: " + if (last > 0) "последняя обработка " + java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(last)) else "ещё не обрабатывались")
                    FinanceCaptureSettings.lastError(context, key)?.let { Text("$label: ошибка сохранения ($it)", color = AppColors.Red) }
                }
                Text("Включи уведомления в почтовом клиенте. Импортируется видимый текст новых сообщений. Правила и разбор выполняются на телефоне.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } }
    )
}

@Composable
private fun CaptureToggle(label: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(enabled, onCheckedChange = onChange)
    }
}

@Composable
private fun ImportRulesDialog(rules: List<FinanceImportRuleEntity>, categories: List<ExpenseCategoryEntity>, onForget: (String) -> Unit, onSender: (String, String) -> Unit, onEdit: (FinanceImportRuleEntity) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Запомненные правила") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rules.isEmpty()) Text("Подтверди или отклони первое сообщение, чтобы создать правило.")
                rules.forEach { rule ->
                    Text("${sourceLabel(rule.source)} · ${rule.sender}")
                    Text(when (rule.action) { "IGNORE" -> "Пропускать"; "AUTO" -> "Учитывать автоматически"; "KEEP" -> "Сохранять как нужное без операции"; "FINANCIAL" -> "Финансовый отправитель"; "BLOCK_SENDER" -> "Не собирать сообщения отправителя"; else -> "Проверять вручную" })
                    if (rule.income != null) {
                        Text(if (rule.transfer == true) "Перевод · без влияния на баланс" else if (rule.income) "Доход · ${rule.incomeSourceName ?: "Прочий доход"}"
                            else "Расход · ${categories.find { it.id == rule.expenseCategoryId }?.name ?: "Без категории"}")
                    }
                    rule.bankName?.let { Text("Банк: $it") }
                    rule.textFilter?.let { Text("Содержит: $it") }
                    Text(rule.template.take(250), color = AppColors.SecondaryText)
                    if (rule.action in listOf("FINANCIAL", "BLOCK_SENDER")) TextButton(onClick = { onSender(rule.source, rule.sender) }) { Text("Изменить отправителя") }
                    else TextButton(onClick = { onEdit(rule) }) { Text("Изменить по сообщению") }
                    TextButton(onClick = { onForget(rule.key) }) { Text("Забыть правило") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
private fun PasteMessageDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sender by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    fun save() {
        if (body.isBlank()) { error = true; return }
                scope.launch {
                    try {
                        val at = System.currentTimeMillis()
                        FinanceMessageCapture.receive(context.applicationContext, "MANUAL", sender.ifBlank { "Вставленный текст" }, title, body, at, "manual:$at:${FinanceMessageParser.hash(body)}")
                        onDismiss()
                    } catch (failure: Exception) {
                        if (failure is kotlinx.coroutines.CancellationException) throw failure
                        error = true
                    }
                }
    }
    val dismiss = rememberDismissGuard(sender.isNotBlank() || title.isNotBlank() || body.isNotBlank(), ::save, onDismiss)
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Импорт текста сообщения") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(sender, { sender = it }, label = { Text("Источник или отправитель") })
                OutlinedTextField(title, { title = it }, label = { Text("Заголовок") })
                OutlinedTextField(body, { body = it }, label = { Text("Текст SMS или письма") }, minLines = 3, maxLines = 6)
                Text("Вставленный и переданный вручную текст всегда приходит на проверку.", color = AppColors.SecondaryText)
                if (error) Text("Не удалось сохранить текст", color = AppColors.Red)
            }
        },
        confirmButton = {
            TextButton(enabled = body.isNotBlank(), onClick = ::save) { Text("В обработку") }

        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Отмена") } }
    )
}

private fun sourceLabel(source: String): String = when (source) {
    "APP" -> "Приложение"
    "SMS" -> "SMS"
    "EMAIL" -> "Почта"
    "NOTIFICATION" -> "Уведомление"
    else -> "Импорт текста"
}


@Composable
private fun FinanceDirectionButtons(income: Boolean?, transfer: Boolean, onChange: (Boolean, Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(Triple(false, false, "Расход"), Triple(true, false, "Доход"), Triple(false, true, "Перевод")).forEach { (value, neutral, label) ->
            val active = if (neutral) transfer else !transfer && income == value
            OutlinedButton(
                onClick = { onChange(value, neutral) },
                modifier = Modifier.weight(1f).semantics { selected = active },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                border = BorderStroke(1.dp, if (active) Color.White else Color.Gray),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.MainText)
            ) { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false) }
        }
    }
}

@Composable
private fun SenderSettingsDialog(
    source: String, sender: String, rule: FinanceImportRuleEntity?, error: String?,
    onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit
) {
    var bank by remember(source, sender) { mutableStateOf(rule?.bankName.orEmpty()) }
    var financial by remember(source, sender) { mutableStateOf(rule?.action != "BLOCK_SENDER") }
    val dismiss = rememberDismissGuard(bank != rule?.bankName.orEmpty() || financial != (rule?.action != "BLOCK_SENDER"),
        { onSave(bank, financial) }, onDismiss)
    AlertDialog(onDismissRequest = dismiss, title = { Text("Отправитель") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${sourceLabel(source)} · $sender")
            OutlinedTextField(bank, { bank = it }, label = { Text("Банк или организация") }, singleLine = true)
            CaptureToggle("Финансовый отправитель", financial) { financial = it }
            Text(if (financial) "Новые сообщения будут обрабатываться. Категорию и автоматический учёт задавай отдельно для конкретных операций."
                else "Новые сообщения этого отправителя собираться не будут. Уже накопленные неучтённые сообщения удалятся независимо от содержания. Учтённые операции сохранятся. Включить обратно можно в правилах.")
            error?.let { Text(it, color = AppColors.Red) }
        }
    }, confirmButton = { TextButton(onClick = { onSave(bank, financial) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Отмена") } })
}
