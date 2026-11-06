package com.anasikus.lifeassistant.calendar

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.anasikus.lifeassistant.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlanningPersistenceTest {
    @Test fun migrationFromVersionFourPreservesExistingDataAndValidatesRoomSchema() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "planning-migration-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { old ->
            old.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, startDate TEXT NOT NULL, deadline TEXT NOT NULL, completed INTEGER NOT NULL, completedAt TEXT)")
            old.execSQL("CREATE TABLE calendar_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, startDate TEXT NOT NULL, startMinute INTEGER NOT NULL, durationMinutes INTEGER NOT NULL, repeatDaysMask INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE calendar_task_placements (taskId INTEGER NOT NULL PRIMARY KEY, date TEXT NOT NULL, startMinute INTEGER NOT NULL, durationMinutes INTEGER NOT NULL, hidden INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE calendar_event_overrides (eventId INTEGER NOT NULL, occurrenceDate TEXT NOT NULL, date TEXT NOT NULL, startMinute INTEGER NOT NULL, durationMinutes INTEGER NOT NULL, deleted INTEGER NOT NULL, PRIMARY KEY(eventId, occurrenceDate))")
            old.execSQL("INSERT INTO calendar_events VALUES (42, 'Работа', '', '2026-11-05', 480, 720, 0)")
            old.version = 4
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6).build()
        try {
            val event = db.calendarEventDao().getAllEvents().first().single()
            assertEquals(720, event.durationMinutes)
            assertNull(event.categoryId)
            assertEquals(4, db.calendarPlanningDao().categories().first().size)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun categoryDeletionDetachesButDoesNotDeleteEventsOrPayments() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val dao = db.calendarPlanningDao()
            dao.saveCategory(EventCategoryEntity(42, "Подработка", "SIDE_JOB"))
            db.calendarEventDao().insertEvent(CalendarEventEntity(7, "Заказ", startDate = "2026-11-05", startMinute = 480, categoryId = 42, oneTimePaymentCents = 100000))
            dao.deleteCategory(42)
            val event = db.calendarEventDao().getAllEvents().first().single()
            assertNull(event.categoryId)
            assertEquals(100000L, event.oneTimePaymentCents!!)
            assertTrue(dao.categories().first().isEmpty())
        } finally { db.close() }
    }

    @Test fun forecastCannotOverwriteConfirmedSickDay() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val dao = db.calendarPlanningDao()
            dao.saveShift(ShiftDayEntity("2026-11-05", "SICK"))
            dao.saveForecast(listOf(ShiftDayEntity("2026-11-05", "WORK", predicted = true), ShiftDayEntity("2026-11-06", "WORK", predicted = true)))
            assertEquals("SICK", dao.shift("2026-11-05")!!.status)
            assertTrue(dao.shift("2026-11-06")!!.predicted)
        } finally { db.close() }
    }

    @Test fun convertingRepeatedWorkRemovesOnlySelectedOccurrence() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val event = CalendarEventEntity(7, "Работа", startDate = "2026-11-05", startMinute = 480, durationMinutes = 720, repeatDaysMask = 127)
            db.calendarEventDao().insertEvent(event)
            db.calendarPlanningDao().moveEventToShift(ShiftDayEntity("2026-11-05", "WORK"), event, "2026-11-05")
            assertEquals(1, db.calendarEventDao().getAllEvents().first().size)
            assertTrue(db.calendarEventOverrideDao().getAll().first().single().deleted)
            assertEquals("WORK", db.calendarPlanningDao().shift("2026-11-05")!!.status)
        } finally { db.close() }
    }
}
