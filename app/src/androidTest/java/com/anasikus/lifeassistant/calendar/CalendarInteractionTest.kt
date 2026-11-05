package com.anasikus.lifeassistant.calendar

import android.widget.TimePicker
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import com.anasikus.lifeassistant.data.CalendarEventEntity
import com.anasikus.lifeassistant.domain.TimelineRange
import com.anasikus.lifeassistant.ui.components.CalendarEventDialog
import com.anasikus.lifeassistant.ui.components.CalendarTimelineCard
import com.anasikus.lifeassistant.ui.components.TimePickerField
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CalendarInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun secondDragStillUpdatesPreviewBeforeFingerIsReleased() {
        val range = mutableStateOf(TimelineRange(0, 180))
        val saved = mutableListOf<TimelineRange>()
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    CalendarTimelineCard(
                        itemKey = "test", title = "Задача", range = range.value,
                        laneWidth = 260.dp, lane = 0, color = Color.DarkGray,
                        onClick = {}, onSave = { saved.add(it); range.value = it }
                    )
                }
            }
        }
        val distance = with(compose.density) { 60.dp.toPx() }
        fun beginMove() {
            compose.onNodeWithTag("calendar-body-test").performTouchInput {
                down(center)
                advanceEventTime(700)
                moveBy(Offset(0f, distance))
            }
        }
        beginMove()
        compose.onNodeWithText("01:00–04:00").assertIsDisplayed()
        compose.onNodeWithTag("calendar-body-test").performTouchInput { up() }
        compose.waitForIdle()

        beginMove()
        // Проверяем ДО сохранения: старый баг обновлял только отсоединённый State.
        compose.onNodeWithText("02:00–05:00").assertIsDisplayed()
        compose.onNodeWithTag("calendar-body-test").performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(60, 120), saved.map { it.startMinute }) }
    }

    @Test fun delayedAcknowledgementDoesNotRollBackNewerDrag() {
        val source = mutableStateOf(TimelineRange(0, 180))
        val saved = mutableListOf<TimelineRange>()
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    CalendarTimelineCard(
                        itemKey = "test", title = "Задача", range = source.value,
                        laneWidth = 260.dp, lane = 0, color = Color.DarkGray,
                        onClick = {}, onSave = { saved.add(it) }
                    )
                }
            }
        }
        val distance = with(compose.density) { 60.dp.toPx() }
        repeat(2) {
            compose.onNodeWithTag("calendar-body-test").performTouchInput {
                down(center)
                advanceEventTime(700)
                moveBy(Offset(0f, distance))
                up()
            }
            compose.waitForIdle()
        }
        compose.runOnIdle { source.value = saved.first() }
        compose.onNodeWithText("02:00–05:00").assertIsDisplayed()
        compose.runOnIdle { source.value = saved.last() }
        compose.onNodeWithText("02:00–05:00").assertIsDisplayed()
    }

    @Test fun secondResizeStillUpdatesPreviewBeforeFingerIsReleased() {
        val range = mutableStateOf(TimelineRange(0, 180))
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    CalendarTimelineCard(
                        itemKey = "test", title = "Мероприятие", range = range.value,
                        laneWidth = 260.dp, lane = 0, color = Color.DarkGray,
                        onClick = {}, onSave = { range.value = it }
                    )
                }
            }
        }
        val distance = with(compose.density) { 30.dp.toPx() }
        fun beginResize() {
            compose.onNodeWithTag("calendar-resize-test").performTouchInput {
                down(center)
                moveBy(Offset(0f, distance))
            }
        }
        beginResize()
        compose.onNodeWithTag("calendar-resize-test").performTouchInput { up() }
        compose.waitForIdle()
        var previousDuration = 0
        compose.runOnIdle { previousDuration = range.value.durationMinutes }
        val oldLabel = "00:00–%02d:%02d".format(previousDuration / 60, previousDuration % 60)
        beginResize()
        compose.onNodeWithText(oldLabel).assertDoesNotExist()
        compose.onNodeWithTag("calendar-resize-test").performTouchInput { up() }
        compose.runOnIdle { org.junit.Assert.assertTrue(range.value.durationMinutes > previousDuration) }
    }

    @Test fun eventFormUsesDisplayedOccurrenceTimeAndDuration() {
        var saved: TimelineRange? = null
        var savedDate: String? = null
        compose.setContent {
            MaterialTheme {
                CalendarEventDialog(
                    event = CalendarEventEntity(title = "Работа", startDate = "2026-11-05", startMinute = 480, durationMinutes = 720),
                    initialDate = "2026-11-06", initialStartMinute = 475, initialDurationMinutes = 240,
                    onDismiss = {},
                    onSave = { _, _, date, start, duration, _ -> saved = TimelineRange(start, duration); savedDate = date }
                )
            }
        }
        compose.onNodeWithText("07:55").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Сохранить").performClick()
        compose.runOnIdle {
            assertEquals(TimelineRange(475, 240), saved)
            assertEquals("2026-11-06", savedDate)
        }
    }

    @Test fun bothTimeFieldsOpenNativeClockDialog() {
        compose.setContent {
            MaterialTheme {
                Column {
                    TimePickerField(label = "Начало (с)", minute = 480, onTimeSelected = {})
                    TimePickerField(label = "Окончание (до)", minute = 720, onTimeSelected = {}, endOfDayAtMidnight = true)
                }
            }
        }
        compose.onNodeWithText("Начало (с)").performClick()
        onView(isAssignableFrom(TimePicker::class.java)).check(matches(isDisplayed()))
        pressBack()
        compose.onNodeWithText("Окончание (до)").performClick()
        onView(isAssignableFrom(TimePicker::class.java)).check(matches(isDisplayed()))
        pressBack()
    }
}
