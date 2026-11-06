package com.anasikus.lifeassistant.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anasikus.lifeassistant.data.*
import com.anasikus.lifeassistant.domain.*
import com.anasikus.lifeassistant.ui.AppColors
import com.anasikus.lifeassistant.viewmodel.CalendarViewModel
import java.util.Calendar

@Composable
fun CalendarPlanningDialog(viewModel: CalendarViewModel, selectedDate: String, onDismiss: () -> Unit) {
    val categories by viewModel.categories.collectAsState()
    val dates by viewModel.specialDates.collectAsState()
    val message by viewModel.planningMessage.collectAsState()
    val importing by viewModel.importing.collectAsState()
    var tab by remember { mutableStateOf(0) }
    MaterialTheme(colorScheme = darkColorScheme(primary = AppColors.Accent, surface = AppColors.Card, background = AppColors.Background, onSurface = AppColors.MainText)) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Card,
        title = { Text("Настройки календаря", color = AppColors.MainText) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    listOf("Категории", "Праздники").forEachIndexed { index, label ->
                        TextButton(onClick = { tab = index }) { Text(label, color = if (tab == index) AppColors.Accent else AppColors.SecondaryText, fontSize = 11.sp) }
                    }
                }
                when (tab) {
                    0 -> CategorySettings(categories, viewModel::saveCategory, viewModel::deleteCategory)
                    1 -> SpecialDateSettings(selectedDate, dates, importing, message, viewModel)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть", color = AppColors.Accent) } }
    )
    }
}

@Composable
private fun CategorySettings(categories: List<EventCategoryEntity>, onSave: (EventCategoryEntity) -> Unit, onDelete: (Int) -> Unit) {
    var editing by remember { mutableStateOf<EventCategoryEntity?>(null) }
    var deleting by remember { mutableStateOf<EventCategoryEntity?>(null) }
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("GENERAL") }
    categories.forEach { category ->
        Text(category.name, color = AppColors.MainText)
        Row {
            TextButton(onClick = { editing = category; name = category.name; kind = category.kind }) { Text("Изменить") }
            TextButton(onClick = { deleting = category }) { Text("Удалить", color = AppColors.Red) }
        }
    }
    PlanningInput(name, { name = it }, "Название категории")
    Text("Тип определяет поведение категории:", color = AppColors.SecondaryText)
    listOf("GENERAL" to "Мероприятие", "WORK" to "График смен", "SIDE_JOB" to "Разовая подработка", "HOLIDAY" to "Праздник").forEach { (value, label) ->
        TextButton(onClick = { kind = value }) { Text("${if (kind == value) "●" else "○"} $label") }
    }
    Button(onClick = {
        onSave(EventCategoryEntity(editing?.id ?: 0, name.trim(), kind))
        editing = null; name = ""; kind = "GENERAL"
    }, enabled = name.isNotBlank() && categories.none { it.id != editing?.id && it.name.equals(name.trim(), ignoreCase = true) }) { Text(if (editing == null) "Добавить категорию" else "Сохранить категорию") }
    if (editing != null) TextButton(onClick = { editing = null; name = "" }) { Text("Отмена редактирования") }
    Text("Удаление категории сохранит мероприятия без категории и не удалит график смен.", color = AppColors.SecondaryText, fontSize = 11.sp)
    deleting?.let { category ->
        AlertDialog(
            onDismissRequest = { deleting = null }, title = { Text("Удалить «${category.name}»?") },
            text = { Text("Мероприятия сохранятся без категории.") },
            confirmButton = { TextButton(onClick = { onDelete(category.id); deleting = null; if (editing?.id == category.id) { editing = null; name = "" } }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } }
        )
    }
}

@Composable
internal fun ShiftSettings(date: String, pattern: ShiftPatternEntity?, shifts: List<ShiftDayEntity>, viewModel: CalendarViewModel) {
    var tab by remember { mutableStateOf(0) }
    Row {
        listOf("Шаблон", "День", "Прогноз").forEachIndexed { index, label ->
            TextButton(onClick = { tab = index }) { Text(label, color = if (index == tab) AppColors.Accent else AppColors.SecondaryText) }
        }
    }
    when (tab) {
        0 -> PatternEditor(date, pattern, viewModel::savePattern)
        1 -> ShiftDayEditor(date, pattern, shifts, viewModel::saveShift, viewModel::deleteShift)
        2 -> {
            var forecast by remember(shifts, date) { mutableStateOf<ShiftRules.Forecast?>(null) }
            var checked by remember(shifts, date) { mutableStateOf(false) }
            val start = CalendarDateUtils.startOfMonth(CalendarDateUtils.addMonths(date, 1))
            val end = CalendarDateUtils.endOfMonth(start)
            Text("Прогноз на ${CalendarDateUtils.formatMonth(start)}", color = AppColors.MainText)
            Text("Нужны хотя бы 28 подтверждённых рабочих/выходных дней из двух месяцев. Пробелы не считаются выходными. Отпуск и больничный не используются для поиска цикла.", color = AppColors.SecondaryText, fontSize = 12.sp)
            Button(onClick = { forecast = ShiftRules.forecast(shifts, start, end); checked = true }) { Text("Проверить закономерность") }
            if (checked && forecast == null) Text("Надёжный повторяющийся цикл не найден. Заполни график вручную или задай шаблон.", color = AppColors.Yellow)
            forecast?.let { prediction ->
                Text("Предположение на ${prediction.days.size} дней: цикл ${prediction.period} дней. Совпадение с историей ${prediction.matchPercent}%; это не гарантия.", color = AppColors.Yellow)
                prediction.days.forEach { day -> Text("${CalendarDateUtils.formatDate(day.date)} — ${shiftStatus(day.status)} ?", color = AppColors.SecondaryText, fontSize = 12.sp) }
                Button(onClick = { viewModel.applyForecast(prediction.days); forecast = null; checked = false }) { Text("Добавить как предположение") }
                Text("Подтверждённые дни не перезаписываются. Подтвердить прогноз можно в редакторе дня.", color = AppColors.SecondaryText, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PatternEditor(date: String, pattern: ShiftPatternEntity?, onSave: (ShiftPatternEntity) -> Unit) {
    var mode by remember(pattern) { mutableStateOf(pattern?.mode ?: "WEEKDAYS") }
    var anchor by remember(pattern) { mutableStateOf(pattern?.anchorDate ?: CalendarDateUtils.startOfMonth(date)) }
    var work by remember(pattern) { mutableStateOf((pattern?.workDays ?: 5).toString()) }
    var off by remember(pattern) { mutableStateOf((pattern?.offDays ?: 2).toString()) }
    var start by remember(pattern) { mutableStateOf(pattern?.startMinute ?: 480) }
    var duration by remember(pattern) { mutableStateOf(pattern?.durationMinutes ?: 720) }
    var rate by remember(pattern) { mutableStateOf(java.math.BigDecimal.valueOf(pattern?.hourlyRateCents ?: 0, 2).toPlainString()) }
    var result by remember { mutableStateOf<String?>(null) }
    TextButton(onClick = { mode = "WEEKDAYS" }) { Text("${if (mode == "WEEKDAYS") "●" else "○"} 5/2, понедельник–пятница") }
    TextButton(onClick = { mode = "CYCLE" }) { Text("${if (mode == "CYCLE") "●" else "○"} Повторяющийся цикл, например 2/2") }
    PlanningDateField("Начало графика / первый рабочий день цикла", anchor) { anchor = it }
    if (mode == "CYCLE") {
        PlanningInput(work, { work = it.filter(Char::isDigit).take(2) }, "Рабочих дней подряд")
        PlanningInput(off, { off = it.filter(Char::isDigit).take(2) }, "Выходных подряд")
    }
    ShiftTimeInputs(start, duration, rate, { start = it }, { duration = it }, { rate = it })
    Button(onClick = {
        val cents = ShiftRules.cents(rate)
        val workCount = if (mode == "WEEKDAYS") 5 else work.toIntOrNull(); val offCount = if (mode == "WEEKDAYS") 2 else off.toIntOrNull()
        if (cents == null || workCount == null || offCount == null || workCount !in 1..31 || offCount !in 1..31 || duration !in 15..1440) {
            result = "Проверь ставку, длительность и число дней (1–31)."
        } else {
            onSave(ShiftPatternEntity(anchorDate = anchor, mode = mode, workDays = workCount, offDays = offCount, startMinute = start, durationMinutes = duration, hourlyRateCents = cents))
            result = "Шаблон сохранён. Индивидуальные отметки дней имеют приоритет."
        }
    }) { Text("Сохранить график") }
    if (pattern != null) TextButton(onClick = { onSave(pattern.copy(enabled = !pattern.enabled)) }) { Text(if (pattern.enabled) "Отключить шаблон" else "Включить шаблон") }
    result?.let { Text(it, color = AppColors.SecondaryText) }
}

@Composable
private fun ShiftDayEditor(initialDate: String, pattern: ShiftPatternEntity?, shifts: List<ShiftDayEntity>, onSave: (ShiftDayEntity) -> Unit, onReset: (String) -> Unit) {
    var date by remember { mutableStateOf(initialDate) }
    val day = ShiftRules.days(date, date, pattern, shifts).firstOrNull()
    var status by remember(date, day) { mutableStateOf(day?.status ?: "WORK") }
    var start by remember(date, day) { mutableStateOf(day?.startMinute ?: 480) }
    var duration by remember(date, day) { mutableStateOf(day?.durationMinutes ?: 720) }
    var rate by remember(date, day) { mutableStateOf(java.math.BigDecimal.valueOf(day?.hourlyRateCents ?: 0, 2).toPlainString()) }
    var message by remember(date) { mutableStateOf<String?>(null) }
    PlanningDateField("День графика", date) { date = it }
    listOf("WORK", "OFF", "SICK", "VACATION").forEach { value ->
        TextButton(onClick = { status = value }) { Text("${if (status == value) "●" else "○"} ${shiftStatus(value)}") }
    }
    if (day?.predicted == true) Text("Это предположение. Сохранение подтвердит выбранный день.", color = AppColors.Yellow)
    if (status == "WORK") ShiftTimeInputs(start, duration, rate, { start = it }, { duration = it }, { rate = it })
    Button(onClick = {
        val cents = ShiftRules.cents(rate)
        if (cents == null || duration !in 15..1440) message = "Проверь ставку и длительность."
        else { onSave(ShiftDayEntity(date, status, start, duration, cents)); message = "День сохранён и подтверждён." }
    }) { Text("Сохранить день") }
    TextButton(onClick = { onReset(date); message = "Индивидуальная отметка удалена. Используется шаблон, если он включён." }) { Text("Убрать индивидуальную отметку") }
    message?.let { Text(it, color = AppColors.SecondaryText) }
}

@Composable
private fun ShiftTimeInputs(start: Int, duration: Int, rate: String, onStart: (Int) -> Unit, onDuration: (Int) -> Unit, onRate: (String) -> Unit) {
    TimePickerField("Начало смены", start, onStart)
    TimePickerField("Окончание смены", (start + duration) % 1440, { end -> onDuration(CalendarDisplay.endToDuration(start, end)) })
    Text(CalendarDisplay.interval(start, duration), color = AppColors.MainText)
    PlanningInput(rate, onRate, "Ставка в час, ₽")
    Text("Сумма плановая: длительность × ставка. Смена относится ко дню начала. Получение денег, больничные и отпуск не рассчитываются.", color = AppColors.SecondaryText, fontSize = 11.sp)
}

@Composable
private fun SpecialDateSettings(date: String, values: List<SpecialDateEntity>, importing: Boolean, message: String?, viewModel: CalendarViewModel) {
    var editing by remember { mutableStateOf<SpecialDateEntity?>(null) }
    var name by remember { mutableStateOf("") }
    var chosenDate by remember { mutableStateOf(date) }
    var yearly by remember { mutableStateOf(true) }
    var url by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<SpecialDateEntity?>(null) }
    Text("Семейные и памятные даты сохраняются в базе. Ежегодные повторяются без ограничения срока.", color = AppColors.SecondaryText)
    PlanningInput(name, { name = it }, "День рождения, годовщина, праздник")
    PlanningDateField("Дата", chosenDate) { chosenDate = it }
    Row { Checkbox(checked = yearly, onCheckedChange = { yearly = it }); Text("Каждый год", color = AppColors.MainText) }
    Button(onClick = {
        viewModel.saveSpecialDate(SpecialDateEntity(editing?.id ?: 0, name.trim(), chosenDate, yearly, editing?.source ?: "FAMILY"))
        name = ""; editing = null
    }, enabled = name.isNotBlank()) { Text(if (editing == null) "Добавить памятную дату" else "Сохранить дату") }
    if (editing != null) TextButton(onClick = { editing = null; name = "" }) { Text("Отмена редактирования") }
    LazyColumn(Modifier.fillMaxWidth().height(220.dp)) {
      items(values, key = { it.id }) { value ->
        Text("${value.title} · ${CalendarDateUtils.formatDate(value.date)}${if (value.yearly) " · ежегодно" else ""}", color = AppColors.MainText, fontSize = 12.sp)
        Row {
            TextButton(onClick = { editing = value; name = value.title; chosenDate = value.date; yearly = value.yearly }) { Text("Изменить") }
            TextButton(onClick = { deleting = value }) { Text("Удалить", color = AppColors.Red) }
        }
    }
    }
    Text("Международные календари", color = AppColors.MainText)
    Button(onClick = { viewModel.importHolidays(null) }, enabled = !importing) { Text("Добавить подборку дат ООН (12)") }
    Text("Подборка, а не полный список. Источник: un.org. Праздник не меняет статус смены автоматически.", color = AppColors.SecondaryText, fontSize = 11.sp)
    PlanningInput(url, { url = it }, "Прямая HTTPS-ссылка на .ics")
    Button(onClick = { viewModel.importHolidays(url) }, enabled = !importing && url.isNotBlank()) { Text(if (importing) "Загрузка…" else "Импортировать ICS") }
    Text("Поддерживаются даты на весь день и простое ежегодное повторение. Сложные правила и события со временем пропускаются.", color = AppColors.SecondaryText, fontSize = 11.sp)
    message?.let { Text(it, color = AppColors.SecondaryText) }
    deleting?.let { value ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Удалить «${value.title}»?") },
            confirmButton = { TextButton(onClick = { viewModel.deleteSpecialDate(value.id); deleting = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } })
    }
}

@Composable
fun PlanningDateField(label: String, date: String, onSelected: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val calendar = CalendarDateUtils.parseCalendar(date)
        DatePickerDialog(context, { _, year, month, day -> onSelected("%04d-%02d-%02d".format(java.util.Locale.US, year, month + 1, day)) }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }) { Text("$label: ${CalendarDateUtils.formatDate(date)}", color = AppColors.MainText) }
}

@Composable
private fun PlanningInput(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = AppColors.MainText, unfocusedTextColor = AppColors.MainText, focusedLabelColor = AppColors.Accent, unfocusedLabelColor = AppColors.SecondaryText))
}

fun shiftStatus(value: String): String = when (value) { "WORK" -> "Рабочий"; "OFF" -> "Выходной"; "SICK" -> "Больничный"; "VACATION" -> "Отпуск"; else -> value }
