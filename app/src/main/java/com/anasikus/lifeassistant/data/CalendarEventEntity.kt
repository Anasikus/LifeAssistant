
package com.anasikus.lifeassistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val title: String,

    val description: String = "",

    // Дата первого события в формате yyyy-MM-dd.
    val startDate: String,

    // Время начала в минутах от начала суток.
    // Например, 06:30 = 390, 14:00 = 840.
    val startMinute: Int,

    // Продолжительность мероприятия в минутах.
    val durationMinutes: Int = 60,

    // Маска дней недели: понедельник — бит 0,
    // вторник — бит 1, ..., воскресенье — бит 6.
    // 0 означает однократное мероприятие.
    val repeatDaysMask: Int = 0,
    val categoryId: Int? = null,
    /** Плановая разовая выплата, в копейках; не банковская операция. */
    val oneTimePaymentCents: Long? = null,
    val hourlyRateCents: Long? = null
)
