
package com.anasikus.lifeassistant.data

import androidx.room.Entity

/**
 * Исключение или изменение одного экземпляра мероприятия.
 *
 * occurrenceDate — дата исходного экземпляра повторения.
 * date — дата, на которую экземпляр фактически перенесён.
 */
@Entity(
    tableName = "calendar_event_overrides",
    primaryKeys = ["eventId", "occurrenceDate"]
)
data class CalendarEventOverrideEntity(
    val eventId: Int,
    val occurrenceDate: String,
    val date: String,
    val startMinute: Int,
    val durationMinutes: Int,
    val deleted: Boolean = false,
    val payMode: String? = null,
    val payAmountCents: Long? = null
)
