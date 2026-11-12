package com.anasikus.lifeassistant.calendar

import android.database.sqlite.SQLiteDatabase
import com.anasikus.lifeassistant.data.LegacySampleTasks
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacySampleTasksTest {
    private fun withSamples(check: (SQLiteDatabase) -> Unit) {
        SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY, title TEXT, description TEXT, startDate TEXT, deadline TEXT, completed INTEGER, completedAt TEXT)")
            db.execSQL("CREATE TABLE calendar_task_placements (taskId INTEGER PRIMARY KEY)")
            listOf("Изучить Kotlin", "Составить план на день", "Отдохнуть после работы").forEachIndexed { index, title ->
                db.execSQL("INSERT INTO tasks VALUES (?, ?, '', '2026-11-12', '2026-11-12', 0, NULL)", arrayOf(index + 1, title))
            }
            check(db)
        }
    }

    private fun count(db: SQLiteDatabase): Int = db.rawQuery("SELECT COUNT(*) FROM tasks", null).use {
        it.moveToFirst()
        it.getInt(0)
    }

    @Test fun removesIntactSamplesOnceAndPreservesUserTaskEvenWithSameTitle() = withSamples { db ->
        db.execSQL("INSERT INTO tasks VALUES (4, 'Изучить Kotlin', '', '2026-11-12', '2026-11-12', 0, NULL)")
        repeat(2) { db.execSQL(LegacySampleTasks.DELETE_UNCHANGED) }
        assertEquals(1, count(db))
        db.rawQuery("SELECT id FROM tasks", null).use { it.moveToFirst(); assertEquals(4, it.getInt(0)) }
    }

    @Test fun editedSamplesArePreserved() = withSamples { db ->
        db.execSQL("UPDATE tasks SET description = 'Мои заметки' WHERE id = 1")
        db.execSQL(LegacySampleTasks.DELETE_UNCHANGED)
        assertEquals(3, count(db))
    }

    @Test fun scheduledSamplesArePreserved() = withSamples { db ->
        db.execSQL("INSERT INTO calendar_task_placements VALUES (1)")
        db.execSQL(LegacySampleTasks.DELETE_UNCHANGED)
        assertEquals(3, count(db))
    }
}
