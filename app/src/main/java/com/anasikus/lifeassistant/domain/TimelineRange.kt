package com.anasikus.lifeassistant.domain

/** Минуты от начала дня. Перенос сохраняет длительность, край меняет окончание. */
data class TimelineRange(val startMinute: Int, val durationMinutes: Int) {
    val endMinute: Int get() = startMinute + durationMinutes

    fun moveBy(deltaMinutes: Int): TimelineRange = copy(
        startMinute = (startMinute + deltaMinutes).coerceIn(0, (1440 - durationMinutes).coerceAtLeast(0))
    )

    fun resizeBy(deltaMinutes: Int): TimelineRange = copy(
        durationMinutes = (durationMinutes + deltaMinutes).coerceIn(15, (1440 - startMinute).coerceAtLeast(15))
    )
}
