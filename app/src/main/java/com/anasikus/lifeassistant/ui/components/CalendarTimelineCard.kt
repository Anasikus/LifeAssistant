package com.anasikus.lifeassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.anasikus.lifeassistant.domain.TimelineRange
import com.anasikus.lifeassistant.ui.AppColors
import kotlin.math.roundToInt

/** Общие жесты для задачи и мероприятия. В БД пишем один раз по окончании жеста. */
@Composable
fun CalendarTimelineCard(
    itemKey: String,
    title: String,
    range: TimelineRange,
    laneWidth: Dp,
    lane: Int,
    color: Color,
    onClick: (TimelineRange) -> Unit,
    onSave: (TimelineRange) -> Unit
) {
    val density = LocalDensity.current
    // Объект MutableState должен оставаться тем же: pointerInput живёт дольше
    // отдельной рекомпозиции и продолжает обращаться к захваченному объекту.
    var preview by remember(itemKey) { mutableStateOf(range) }
    var interacting by remember(itemKey) { mutableStateOf(false) }
    var pendingRanges by remember(itemKey) { mutableStateOf(emptyList<TimelineRange>()) }
    LaunchedEffect(range) {
        // Room может подтвердить предыдущий жест уже после следующего.
        // Подтверждение старой записи не должно откатить последний preview.
        val acknowledged = pendingRanges.indexOfLast { it == range }
        pendingRanges = if (acknowledged >= 0) pendingRanges.drop(acknowledged + 1) else emptyList()
        if (!interacting && pendingRanges.isEmpty()) preview = range
    }
    val currentClick by rememberUpdatedState(onClick)
    val currentSave by rememberUpdatedState(onSave)

    Card(
        modifier = Modifier
            .offset(x = laneWidth * lane, y = preview.startMinute.dp)
            .width(laneWidth)
            .height(preview.durationMinutes.coerceAtLeast(30).dp)
            .padding(horizontal = 2.dp)
            .zIndex(if (interacting) 1f else 0f),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(Modifier.fillMaxSize()) {
            // Переносим за тело, изменяем размер за отдельную нижнюю ручку.
            // Ключ жеста не зависит от preview: рекомпозиция не прерывает drag.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("calendar-body-$itemKey")
                    .pointerInput(itemKey, density) {
                        var origin = preview
                        var totalPx = 0f
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                origin = preview
                                totalPx = 0f
                                interacting = true
                            },
                            onDrag = { change, amount ->
                                totalPx += amount.y
                                preview = origin.moveBy(with(density) { totalPx.toDp().value.roundToInt() })
                                change.consume()
                            },
                            onDragEnd = {
                                interacting = false
                                if (preview != origin) {
                                    pendingRanges = pendingRanges + preview
                                    currentSave(preview)
                                }
                            },
                            onDragCancel = { preview = origin; interacting = false }
                        )
                    }
                    .pointerInput(itemKey) {
                        detectTapGestures(onTap = { currentClick(preview) })
                    }
                    .padding(horizontal = 5.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "${clock(preview.startMinute)}–${clock(preview.endMinute)}",
                    color = AppColors.MainText,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = title,
                    color = AppColors.MainText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${preview.durationMinutes / 60} ч ${preview.durationMinutes % 60} мин",
                    color = AppColors.SecondaryText,
                    fontSize = 10.sp,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .testTag("calendar-resize-$itemKey")
                    .pointerInput(itemKey, density) {
                        var origin = preview
                        var totalPx = 0f
                        detectDragGestures(
                            onDragStart = {
                                origin = preview
                                totalPx = 0f
                                interacting = true
                            },
                            onDrag = { change, amount ->
                                totalPx += amount.y
                                preview = origin.resizeBy(with(density) { totalPx.toDp().value.roundToInt() })
                                change.consume()
                            },
                            onDragEnd = {
                                interacting = false
                                if (preview != origin) {
                                    pendingRanges = pendingRanges + preview
                                    currentSave(preview)
                                }
                            },
                            onDragCancel = { preview = origin; interacting = false }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.width(28.dp).height(3.dp)
                        .background(AppColors.SecondaryText, RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

private fun clock(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
