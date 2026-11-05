package com.anasikus.lifeassistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "event_categories")
data class EventCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    /** GENERAL, WORK, SIDE_JOB, HOLIDAY */
    val kind: String = "GENERAL"
)

@Entity(tableName = "shift_patterns")
data class ShiftPatternEntity(
    @PrimaryKey val id: Int = 1,
    val enabled: Boolean = true,
    /** WEEKDAYS — пн–пт; CYCLE — непрерывный цикл от anchorDate. */
    val mode: String = "WEEKDAYS",
    val anchorDate: String,
    val workDays: Int = 5,
    val offDays: Int = 2,
    val startMinute: Int = 480,
    val durationMinutes: Int = 720,
    val hourlyRateCents: Long = 0
)

@Entity(tableName = "shift_days")
data class ShiftDayEntity(
    @PrimaryKey val date: String,
    /** WORK, OFF, SICK, VACATION */
    val status: String,
    val startMinute: Int = 480,
    val durationMinutes: Int = 720,
    val hourlyRateCents: Long = 0,
    val predicted: Boolean = false,
    val confidence: Int = 100
)

@Entity(tableName = "special_dates")
data class SpecialDateEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val date: String,
    val yearly: Boolean = true,
    /** FAMILY или адрес импортированного календаря. */
    val source: String = "FAMILY"
)
