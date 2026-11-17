package com.anasikus.lifeassistant.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.anasikus.lifeassistant.AssistantScreen
import com.anasikus.lifeassistant.data.AppDatabase
import com.anasikus.lifeassistant.repository.CalendarRepository
import com.anasikus.lifeassistant.repository.TaskRepository
import com.anasikus.lifeassistant.viewmodel.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FinanceNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AppDatabase
    private val store = ViewModelStore()

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        val task = ViewModelProvider(store, TaskViewModelFactory(TaskRepository(db.taskDao())))[TaskViewModel::class.java]
        val repository = CalendarRepository(db.calendarEventDao(), db.calendarTaskPlacementDao(),
            db.calendarEventOverrideDao(), db.calendarPlanningDao(), db.financeInboxDao())
        val calendar = ViewModelProvider(store, CalendarViewModelFactory(repository))[CalendarViewModel::class.java]
        compose.setContent { MaterialTheme { AssistantScreen(task, calendar) } }
    }

    @After fun cleanup() {
        compose.runOnIdle { store.clear() }
        db.close()
    }

    @Test fun reselectingFinanceFromNotificationsReturnsToFinanceOverview() {
        compose.onNode(hasText("Финансы") and hasClickAction()).performClick()
        compose.onNode(hasText("Уведомления", substring = true) and hasClickAction()).performClick()
        compose.onNodeWithText("Отправители").assertIsDisplayed()
        compose.onNode(hasText("Финансы") and hasClickAction()).performClick()
        compose.onNodeWithText("Отправители").assertDoesNotExist()
        compose.onNodeWithText("Заработано").assertIsDisplayed()
        compose.onNodeWithText("Источники").assertDoesNotExist()
    }
}
