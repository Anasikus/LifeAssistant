package com.anasikus.lifeassistant.domain

object CalendarDisplay {
    fun duration(minutes: Int): String = when {
        minutes < 60 -> "$minutes мин"
        minutes % 60 == 0 -> "${minutes / 60} ч"
        else -> "${minutes / 60} ч ${minutes % 60} мин"
    }
    fun clock(minute: Int): String = "%02d:%02d".format((minute % 1440) / 60, minute % 60)
    fun interval(start: Int, duration: Int): String =
        "${clock(start)}–${clock(start + duration)}${if (start + duration >= 1440) " (+1 день)" else ""} · ${CalendarDisplay.duration(duration)}"
    fun endToDuration(start: Int, end: Int): Int = if (end > start) end - start else 1440 - start + end
}
