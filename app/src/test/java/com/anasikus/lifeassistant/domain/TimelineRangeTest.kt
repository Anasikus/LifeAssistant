package com.anasikus.lifeassistant.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineRangeTest {
    @Test fun movingUpdatesBothTimesAndKeepsFourHourDuration() {
        val moved = TimelineRange(720, 240).moveBy(37)
        assertEquals(757, moved.startMinute)
        assertEquals(997, moved.endMinute)
        assertEquals(240, moved.durationMinutes)
    }

    @Test fun draggingCannotMoveOutsideDay() {
        val shift = TimelineRange(480, 720)
        assertEquals(TimelineRange(0, 720), shift.moveBy(-1000))
        assertEquals(TimelineRange(720, 720), shift.moveBy(1000))
    }

    @Test fun stretchingUpdatesEndAndAllowsTwelveHourShift() {
        val resized = TimelineRange(480, 240).resizeBy(480)
        assertEquals(480, resized.startMinute)
        assertEquals(720, resized.durationMinutes)
        assertEquals(1200, resized.endMinute)
    }

    @Test fun resizeStopsAtMidnightAndMinimumDuration() {
        assertEquals(1440, TimelineRange(1200, 120).resizeBy(1000).endMinute)
        assertEquals(15, TimelineRange(720, 240).resizeBy(-1000).durationMinutes)
    }
}
