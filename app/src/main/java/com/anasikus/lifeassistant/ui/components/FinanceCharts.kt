package com.anasikus.lifeassistant.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anasikus.lifeassistant.domain.CalendarDateUtils
import com.anasikus.lifeassistant.domain.FinanceCategoryTotal
import com.anasikus.lifeassistant.domain.FinanceChartPoint
import com.anasikus.lifeassistant.domain.ShiftRules
import com.anasikus.lifeassistant.ui.AppColors
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

private val categoryColors = listOf(
    Color(0xFFA855F7), Color(0xFF38BDF8), Color(0xFFFB923C), Color(0xFF4ADE80),
    Color(0xFFF472B6), Color(0xFFFACC15), Color(0xFF2DD4BF), Color(0xFF818CF8),
    Color(0xFFF87171), Color(0xFFA3E635)
)

private fun number(value: Double): String = NumberFormat.getNumberInstance(Locale("ru", "RU")).apply {
    maximumFractionDigits = 1
}.format(value)

private fun axisMoney(cents: Long): String {
    val rubles = cents / 100.0
    return when {
        rubles >= 1_000_000 -> number(rubles / 1_000_000) + " млн"
        rubles >= 1_000 -> number(rubles / 1_000) + " тыс"
        else -> number(rubles)
    }
}

@Composable
fun ExpenseDonutChart(categories: List<FinanceCategoryTotal>) {
    val total = categories.sumOf { it.amount }
    var selected by remember(categories) { mutableStateOf<Int?>(null) }
    if (total <= 0L) {
        Text("За этот период расходов нет", color = AppColors.SecondaryText)
        return
    }
    val current = selected?.let { categories.getOrNull(it) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(192.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().semantics {
                contentDescription = "Расходы по категориям. Всего ${ShiftRules.money(total)}"
            }.pointerInput(categories) {
                detectTapGestures { tap ->
                    val dx = tap.x - size.width / 2f
                    val dy = tap.y - size.height / 2f
                    val radius = minOf(size.width, size.height) / 2f
                    if (hypot(dx.toDouble(), dy.toDouble()) < radius - 24.dp.toPx() || hypot(dx.toDouble(), dy.toDouble()) > radius) {
                        selected = null
                    } else {
                        val angle = (atan2(dy.toDouble(), dx.toDouble()) * 180 / PI + 450) % 360
                        var edge = 0.0
                        val index = categories.indexOfFirst { edge += it.amount.toDouble() / total * 360; angle <= edge }
                        selected = index.takeIf { it >= 0 }
                    }
                }
            }) {
                val stroke = 24.dp.toPx()
                var start = -90f
                categories.forEachIndexed { index, category ->
                    val sweep = (category.amount.toDouble() / total * 360).toFloat()
                    drawArc(
                        categoryColors[index % categoryColors.size].copy(alpha = if (selected == null || selected == index) 1f else 0.3f),
                        startAngle = start + 0.6f, sweepAngle = (sweep - 1.2f).coerceAtLeast(0f), useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke)
                    )
                    start += sweep
                }
            }
            Column(Modifier.width(128.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(current?.name ?: "Всего расходов", style = MaterialTheme.typography.labelMedium,
                    color = AppColors.SecondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(ShiftRules.money(current?.amount ?: total), style = MaterialTheme.typography.titleSmall,
                    color = AppColors.MainText, maxLines = 2)
            }
        }
        Text("Нажми на сектор или категорию", style = MaterialTheme.typography.labelSmall, color = AppColors.SecondaryText)
        categories.forEachIndexed { index, category ->
            val percent = category.amount.toDouble() / total * 100
            Row(Modifier.fillMaxWidth().clickable { selected = if (selected == index) null else index }.padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Canvas(Modifier.size(10.dp)) { drawCircle(categoryColors[index % categoryColors.size]) }
                Text(category.name, Modifier.weight(1f), color = AppColors.MainText)
                Column(horizontalAlignment = Alignment.End) {
                    Text(ShiftRules.money(category.amount), color = AppColors.MainText)
                    Text(if (percent < 0.1) "< 0,1%" else number(percent) + "%", style = MaterialTheme.typography.labelSmall, color = AppColors.SecondaryText)
                }
            }
        }
    }
}

@Composable
fun FinanceComparisonChart(points: List<FinanceChartPoint>, green: List<Long>, greenLabel: String) {
    if (points.size < 2 || green.size != points.size) {
        Text("В выбранном периоде пока нет данных", color = AppColors.SecondaryText)
        return
    }
    var selected by remember(points, green) { mutableStateOf(points.lastIndex) }
    val maximum = maxOf(green.maxOrNull() ?: 0L, points.maxOf { it.expense }).coerceAtLeast(100L)
    val selectedPoint = points[selected]
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("━ $greenLabel", color = AppColors.Green, style = MaterialTheme.typography.labelMedium)
            Text("┅ Потрачено", color = AppColors.Red, style = MaterialTheme.typography.labelMedium)
        }
        Text("₽", color = AppColors.SecondaryText, style = MaterialTheme.typography.labelSmall)
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(48.dp).height(180.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(maximum, maximum / 2, 0L).forEach { Text(axisMoney(it), color = AppColors.SecondaryText, style = MaterialTheme.typography.labelSmall) }
            }
            Canvas(Modifier.weight(1f).height(180.dp).padding(vertical = 8.dp).semantics {
                contentDescription = "$greenLabel: ${ShiftRules.money(green.last())}; потрачено: ${ShiftRules.money(points.last().expense)}"
            }.pointerInput(points, green) {
                detectTapGestures { tap ->
                    selected = (tap.x / size.width.coerceAtLeast(1) * points.lastIndex).roundToInt().coerceIn(points.indices)
                }
            }) {
                fun position(index: Int, amount: Long) = Offset(
                    index.toFloat() / points.lastIndex * size.width,
                    size.height * (1 - amount.toDouble() / maximum).toFloat()
                )
                repeat(3) { index ->
                    val y = size.height * index / 2f
                    drawLine(AppColors.SecondaryText.copy(alpha = 0.2f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                fun line(values: List<Long>, color: Color, dashed: Boolean) {
                    val path = Path()
                    values.forEachIndexed { index, amount ->
                        val p = position(index, amount)
                        if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    }
                    drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
                        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())) else null))
                }
                line(green, AppColors.Green, false)
                line(points.map { it.expense }, AppColors.Red, true)
                val x = position(selected, 0).x
                drawLine(AppColors.SecondaryText.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                drawCircle(AppColors.Green, 4.dp.toPx(), position(selected, green[selected]))
                drawCircle(AppColors.Red, 4.dp.toPx(), position(selected, selectedPoint.expense))
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Начало", style = MaterialTheme.typography.labelSmall, color = AppColors.SecondaryText)
            if (points.size > 2) Text(CalendarDateUtils.formatDate(points[points.size / 2].date).take(5), style = MaterialTheme.typography.labelSmall, color = AppColors.SecondaryText)
            Text(CalendarDateUtils.formatDate(points.last().date).take(5), style = MaterialTheme.typography.labelSmall, color = AppColors.SecondaryText)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = selected > 0, onClick = { selected-- }, contentPadding = PaddingValues(4.dp)) { Text("‹") }
            Column(Modifier.weight(1f)) {
                Text(if (selected == 0) "Начало периода" else CalendarDateUtils.formatDate(selectedPoint.date), style = MaterialTheme.typography.labelMedium)
                Text("$greenLabel: ${ShiftRules.money(green[selected])}", color = AppColors.Green, style = MaterialTheme.typography.bodySmall)
                Text("Потрачено: ${ShiftRules.money(selectedPoint.expense)}", color = AppColors.Red, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(enabled = selected < points.lastIndex, onClick = { selected++ }, contentPadding = PaddingValues(4.dp)) { Text("›") }
        }
        Text("Нажми на график или используй стрелки, чтобы посмотреть значения.", color = AppColors.SecondaryText, style = MaterialTheme.typography.labelSmall)
    }
}
