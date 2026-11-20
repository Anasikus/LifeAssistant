package com.anasikus.lifeassistant.calendar

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.anasikus.lifeassistant.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TaskSessionsPersistenceTest {
    @Test fun elapsedWindowsAreCountedOnceAndManualWindowsSurviveReplanning() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val dao = db.taskDao()
            dao.insertTask(TaskEntity(id = 1, title = "48 часов", estimatedDurationMinutes = 2880))
            dao.reconcile("2026-11-20", 420)
            val first = dao.sessionRows().first { it.date == "2026-11-20" }
            dao.moveSession(first.id, first.date, 600, 120)
            dao.reconcile("2026-11-20", 600)
            assertEquals(120, dao.session(first.id)!!.durationMinutes)
            assertFalse(dao.session(first.id)!!.automatic)
            dao.reconcile("2026-11-20", 720)
            val spent = dao.task(1)!!.spentMinutes
            assertTrue(spent >= 120)
            dao.reconcile("2026-11-20", 720)
            assertEquals(spent, dao.task(1)!!.spentMinutes)
            assertEquals(2880, spent + dao.sessionRows().filter { !it.accounted }.sumOf { it.durationMinutes })
        } finally { db.close() }
    }

    @Test fun splitIsAtomicAndParentIsNotScheduledAlongsideItsChildren() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val dao = db.taskDao()
            dao.saveGroup(TaskGroupEntity(1, "Разработка"))
            dao.insertTask(TaskEntity(id = 1, title = "Приложение", estimatedDurationMinutes = 2880, groupId = 1))
            val drafts = com.anasikus.lifeassistant.domain.TaskDecomposer.suggest(dao.task(1)!!)
            dao.split(1, 2880, drafts)
            dao.reconcile("2026-11-20", 600)
            assertTrue(dao.task(1)!!.isProject)
            assertFalse(dao.sessionRows().any { it.taskId == 1 })
            val children = dao.rows().filter { it.parentId == 1 }
            assertEquals(2880, children.sumOf { it.estimatedDurationMinutes })
            assertTrue(children.all { it.groupId == 1 })
            dao.deleteGroup(1)
            assertTrue(dao.rows().all { it.groupId == null })
        } finally { db.close() }
    }

    @Test fun workIncomeGetsItsJobCategoryAutomatically() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val dao = db.calendarPlanningDao()
            dao.addFinanceTransaction(FinanceTransactionEntity(date = "2026-11-20", income = true, amountCents = 160000,
                note = "", sourceKey = "event:1", sourceName = "Работа на Озоне"))
            val row = dao.financeTransactions().first().single()
            assertNotNull(row.incomeCategoryId)
            assertEquals("Работа на Озоне", dao.incomeCategories().first().single().name)
            assertEquals("event:1", row.sourceKey)
        } finally { db.close() }
    }
}
