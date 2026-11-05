package com.anasikus.lifeassistant.calendar

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.anasikus.lifeassistant.data.AppDatabase
import com.anasikus.lifeassistant.data.CalendarEventEntity
import com.anasikus.lifeassistant.data.CalendarEventOverrideEntity
import com.anasikus.lifeassistant.domain.CalendarEventRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class EventOccurrencePersistenceTest {
    @Test fun formSaveReplacesDraggedTimeInSameOccurrenceRecord() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        try {
            val event = CalendarEventEntity(id = 42, title = "Работа", startDate = "2026-11-05", startMinute = 480, durationMinutes = 720)
            val dao = database.calendarEventOverrideDao()
            database.calendarEventDao().insertEvent(event)
            val dragged = CalendarEventOverrideEntity(42, event.startDate, event.startDate, 475, 720)
            dao.save(dragged)
            dao.saveEditedOccurrence(event.copy(title = "Новая работа"), dragged.copy(startMinute = 610, durationMinutes = 240))

            val storedEvents = database.calendarEventDao().getAllEvents().first()
            val storedOverrides = dao.getAll().first()
            val result = CalendarEventRules.expand(storedEvents, event.startDate, event.startDate, storedOverrides).single()
            assertEquals(610, result.startMinute)
            assertEquals(240, result.durationMinutes)
            assertEquals("Новая работа", result.event.title)
            assertEquals(1, storedOverrides.size)
        } finally {
            database.close()
        }
    }
}
