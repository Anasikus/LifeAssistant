
package com.anasikus.lifeassistant.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CalendarDateUtils {

    private fun dateFormat(): SimpleDateFormat {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
        }
    }

    fun today(): String = dateFormat().format(Date())

    fun parseCalendar(date: String): Calendar {
        val parsed = dateFormat().parse(date)

        return Calendar.getInstance().apply {
            time = parsed ?: Date()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    fun formatDate(date: String): String {
        return runCatching {
            val parsed = dateFormat().parse(date)
            if (parsed == null) date
            else SimpleDateFormat(
                "dd.MM.yyyy",
                Locale.getDefault()
            ).format(parsed)
        }.getOrDefault(date)
    }

    fun formatLongDate(date: String): String {
        return SimpleDateFormat(
            "EEEE, d MMMM",
            Locale("ru", "RU")
        ).format(parseCalendar(date).time)
    }

    fun formatShortDate(date: String): String {
        return SimpleDateFormat(
            "d MMM",
            Locale("ru", "RU")
        ).format(parseCalendar(date).time)
    }

    fun formatMonth(date: String): String {
        return SimpleDateFormat(
            "LLLL yyyy",
            Locale("ru", "RU")
        ).format(parseCalendar(date).time)
    }

    fun addDays(date: String, days: Int): String {
        return parseCalendar(date).apply {
            add(Calendar.DAY_OF_MONTH, days)
        }.let { dateFormat().format(it.time) }
    }

    fun addMonths(date: String, months: Int): String {
        return parseCalendar(date).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, months)
        }.let { dateFormat().format(it.time) }
    }

    fun addYears(date: String, years: Int): String {
        return parseCalendar(date).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.MONTH, Calendar.JANUARY)
            add(Calendar.YEAR, years)
        }.let { dateFormat().format(it.time) }
    }

    fun startOfWeek(date: String): String {
        val calendar = parseCalendar(date)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        // Переводим воскресенье в конец недели,
        // остальные дни — к понедельнику.
        val daysSinceMonday = (dayOfWeek + 5) % 7
        calendar.add(Calendar.DAY_OF_MONTH, -daysSinceMonday)

        return dateFormat().format(calendar.time)
    }

    fun startOfMonth(date: String): String {
        return parseCalendar(date).apply {
            set(Calendar.DAY_OF_MONTH, 1)
        }.let { dateFormat().format(it.time) }
    }

    fun endOfMonth(date: String): String {
        return parseCalendar(date).apply {
            set(
                Calendar.DAY_OF_MONTH,
                getActualMaximum(Calendar.DAY_OF_MONTH)
            )
        }.let { dateFormat().format(it.time) }
    }

    fun startOfYear(date: String): String {
        return parseCalendar(date).apply {
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
        }.let { dateFormat().format(it.time) }
    }

    fun endOfYear(date: String): String {
        return parseCalendar(date).apply {
            set(Calendar.MONTH, Calendar.DECEMBER)
            set(Calendar.DAY_OF_MONTH, 31)
        }.let { dateFormat().format(it.time) }
    }

    fun weekdayIndex(date: String): Int {
        // Понедельник = 0, воскресенье = 6.
        return (parseCalendar(date).get(Calendar.DAY_OF_WEEK) + 5) % 7
    }

    fun datesBetween(start: String, end: String): List<String> {
        if (start > end) return emptyList()

        val result = mutableListOf<String>()
        var current = start

        while (current <= end) {
            result.add(current)
            current = addDays(current, 1)
        }

        return result
    }

    fun monthGridDates(date: String): List<String> {
        val monthStart = startOfMonth(date)
        val monthEnd = endOfMonth(date)

        val gridStart = startOfWeek(monthStart)
        val gridEnd = addDays(startOfWeek(monthEnd), 6)

        return datesBetween(gridStart, gridEnd)
    }

    fun daysBetween(from: String, to: String): Long {
        // UTC используется, чтобы переходы на летнее/зимнее
        // время не искажали число календарных дней.
        val utcFormat = SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.US
        ).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }

        val fromMillis = utcFormat.parse(from)?.time ?: return 0
        val toMillis = utcFormat.parse(to)?.time ?: return 0

        return (toMillis - fromMillis) / 86_400_000L
    }
}
