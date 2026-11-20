package com.anasikus.lifeassistant.domain

import com.anasikus.lifeassistant.settings.AppText

object CalendarDisplay {
    fun duration(minutes: Int): String = when {
        minutes < 60 -> AppText.get("{arg0} мин", minutes)
        minutes % 60 == 0 -> AppText.get("{arg0} ч", minutes / 60)
        else -> AppText.get("{arg0} ч {arg1} мин", minutes / 60, minutes % 60)
    }
    fun clock(minute: Int): String = "%02d:%02d".format((minute % 1440) / 60, minute % 60)
    fun interval(start: Int, duration: Int): String =
        "${clock(start)}–${clock(start + duration)}${if (start + duration >= 1440) AppText.get(" (+1 день)") else ""} · ${CalendarDisplay.duration(duration)}"
    fun endToDuration(start: Int, end: Int): Int = if (end > start) end - start else 1440 - start + end
}
