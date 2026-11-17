package com.anasikus.lifeassistant.calendar

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.anasikus.lifeassistant.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TaskArchivePersistenceTest {
    @Test fun archiveRolloverPreservesTodayAndRestoredTasksUntilRecompletion() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val dao = db.taskDao()
            dao.insertTask(TaskEntity(id = 1, title = "Old", completed = true, completedAt = "2026-11-16"))
            dao.insertTask(TaskEntity(id = 2, title = "Today", completed = true, completedAt = "2026-11-17"))
            dao.insertTask(TaskEntity(id = 3, title = "Pending"))
            dao.archiveCompletedBefore("2026-11-17")
            assertTrue(dao.task(1)!!.archived)
            assertFalse(dao.task(2)!!.archived)
            assertFalse(dao.task(3)!!.archived)
            dao.archive(1, false, "2026-11-17")
            dao.archiveCompletedBefore("2026-11-18")
            assertFalse(dao.task(1)!!.archived)
            assertTrue(dao.task(1)!!.autoArchiveSuppressed)
            assertTrue(dao.task(2)!!.archived)
            dao.editTask(dao.task(1)!!.copy(completed = false, completedAt = null))
            dao.editTask(dao.task(1)!!.copy(completed = true, completedAt = "2026-11-19"))
            dao.archiveCompletedBefore("2026-11-20")
            assertTrue(dao.task(1)!!.archived)

            db.calendarTaskPlacementDao().save(CalendarTaskPlacementEntity(3, "2026-11-16", 600, 60))
            dao.archive(3, true, "2026-11-17")
            assertTrue(dao.task(3)!!.archived)
            dao.archive(3, false, "2026-11-17")
            assertFalse(dao.task(3)!!.archived)
            assertTrue(db.calendarTaskPlacementDao().getAll().first().isEmpty())
        } finally { db.close() }
    }

    @Test fun durationEditsStaySynchronizedAndDeleteRemovesPlacement() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val dao = db.taskDao()
            dao.insertTask(TaskEntity(id = 1, title = "Task"))
            db.calendarTaskPlacementDao().saveWithEstimate(CalendarTaskPlacementEntity(1, "2026-11-17", 1320, 120))
            assertEquals(120, dao.task(1)!!.estimatedDurationMinutes)
            dao.editTask(dao.task(1)!!.copy(estimatedDurationMinutes = 180))
            val slot = db.calendarTaskPlacementDao().getAll().first().single()
            assertEquals(180, slot.durationMinutes)
            assertEquals(1260, slot.startMinute)
            dao.removeTask(dao.task(1)!!)
            assertNull(dao.task(1))
            assertTrue(db.calendarTaskPlacementDao().getAll().first().isEmpty())
        } finally { db.close() }
    }
}
