package com.anasikus.lifeassistant.repository

import com.anasikus.lifeassistant.data.SpecialDateEntity
import com.anasikus.lifeassistant.domain.CalendarDateUtils
import java.net.HttpURLConnection
import java.net.URL

object HolidayImporter {
    const val UN_SOURCE = "https://www.un.org/en/observances/list-days-weeks"
    /** Небольшая явно обозначенная подборка фиксированных дат, не производственный календарь. */
    fun internationalSelection(): List<SpecialDateEntity> = listOf(
        "01-04" to "Всемирный день азбуки Брайля",
        "02-21" to "Международный день родного языка",
        "03-08" to "Международный женский день",
        "03-22" to "Всемирный день водных ресурсов",
        "04-07" to "Всемирный день здоровья",
        "05-15" to "Международный день семей",
        "06-05" to "Всемирный день окружающей среды",
        "09-21" to "Международный день мира",
        "10-01" to "Международный день пожилых людей",
        "10-05" to "Всемирный день учителей",
        "11-16" to "Международный день толерантности",
        "12-10" to "День прав человека"
    ).map { (date, title) -> SpecialDateEntity(title = title, date = "2000-$date", source = UN_SOURCE) }

    fun download(address: String): List<SpecialDateEntity> {
        val url = URL(address.trim())
        require(url.protocol == "https") { "Нужна HTTPS-ссылка на ICS-календарь." }
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.instanceFollowRedirects = false
        try {
            require(connection.responseCode == 200) { "Календарь вернул HTTP ${connection.responseCode}. Нужна прямая ссылка без перенаправления." }
            val bytes = connection.inputStream.use { it.readBytesLimited(2 * 1024 * 1024) }
            return parse(bytes.toString(Charsets.UTF_8), address.trim()).also {
                require(it.size <= 500) { "В календаре больше 500 дат. Выбери календарь меньшего размера." }
                require(it.isNotEmpty()) { "Не найдены поддерживаемые даты. Нужен ICS с событиями на весь день; сложные правила повторения пропускаются." }
            }
        } finally { connection.disconnect() }
    }
    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit) { "Календарь больше 2 МБ." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
    fun parse(text: String, source: String): List<SpecialDateEntity> {
        require(text.contains("BEGIN:VCALENDAR")) { "Ответ не является ICS-календарём." }
        val unfolded = text.replace("\r\n", "\n").replace(Regex("\n[ \\t]"), "")
        return Regex("BEGIN:VEVENT\\s*([\\s\\S]*?)END:VEVENT").findAll(unfolded).mapNotNull { match ->
            val fields = match.groupValues[1].lines().mapNotNull { line ->
                val colon = line.indexOf(':')
                if (colon < 0) null else line.substring(0, colon).substringBefore(';') to line.substring(colon + 1).trim()
            }.toMap()
            val start = fields["DTSTART"] ?: return@mapNotNull null
            if (!start.matches(Regex("[0-9]{8}"))) return@mapNotNull null
            val title = fields["SUMMARY"]?.replace("\\n", " ")?.replace("\\,", ",")?.replace("\\;", ";")?.replace("\\\\", "\\") ?: return@mapNotNull null
            if (fields["STATUS"] == "CANCELLED") return@mapNotNull null
            if (fields.keys.any { it in listOf("EXDATE", "RDATE", "RECURRENCE-ID") }) return@mapNotNull null
            val recurrence = fields["RRULE"]
            if (recurrence != null && recurrence != "FREQ=YEARLY" && recurrence != "FREQ=YEARLY;INTERVAL=1") return@mapNotNull null
            val date = "${start.take(4)}-${start.substring(4, 6)}-${start.takeLast(2)}"
            if (runCatching { CalendarDateUtils.parseCalendar(date) }.isFailure) return@mapNotNull null
            val end = fields["DTEND"]?.takeIf { it.matches(Regex("[0-9]{8}")) }?.let {
                "${it.take(4)}-${it.substring(4, 6)}-${it.takeLast(2)}"
            } ?: CalendarDateUtils.addDays(date, 1)
            val span = runCatching { CalendarDateUtils.daysBetween(date, end) }.getOrNull() ?: return@mapNotNull null
            if (span !in 1L..366L) return@mapNotNull null
            CalendarDateUtils.datesBetween(date, CalendarDateUtils.addDays(end, -1)).map { day ->
                SpecialDateEntity(title = title.take(200), date = day, yearly = recurrence != null, source = source)
            }
        }.flatMap { it.asSequence() }.distinctBy { Triple(it.title, it.date, it.source) }.toList()
    }
}
