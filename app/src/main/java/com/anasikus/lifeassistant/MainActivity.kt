
package com.anasikus.lifeassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.anasikus.lifeassistant.data.AppDatabase
import com.anasikus.lifeassistant.repository.CalendarRepository
import com.anasikus.lifeassistant.repository.TaskRepository
import com.anasikus.lifeassistant.ui.AppColors
import com.anasikus.lifeassistant.ui.navigation.AppDestination
import com.anasikus.lifeassistant.ui.navigation.BottomNavigationBar
import com.anasikus.lifeassistant.ui.screens.CalendarScreen
import com.anasikus.lifeassistant.ui.screens.FeaturePlaceholderScreen
import com.anasikus.lifeassistant.ui.screens.TasksScreen
import com.anasikus.lifeassistant.viewmodel.CalendarViewModel
import com.anasikus.lifeassistant.viewmodel.CalendarViewModelFactory
import com.anasikus.lifeassistant.viewmodel.TaskViewModel
import com.anasikus.lifeassistant.viewmodel.TaskViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getDatabase(applicationContext)

        val taskRepository = TaskRepository(database.taskDao())
        val taskFactory = TaskViewModelFactory(taskRepository)
        val taskViewModel = ViewModelProvider(this, taskFactory)
            .get(TaskViewModel::class.java)

        val calendarRepository = CalendarRepository(
            eventDao = database.calendarEventDao(),
            placementDao = database.calendarTaskPlacementDao(),
            overrideDao = database.calendarEventOverrideDao()
        )

        val calendarFactory =
            CalendarViewModelFactory(calendarRepository)

        val calendarViewModel =
            ViewModelProvider(this, calendarFactory)
                .get(CalendarViewModel::class.java)

        setContent {
            MaterialTheme {
                AssistantScreen(
                    taskViewModel = taskViewModel,
                    calendarViewModel = calendarViewModel
                )
            }
        }
    }
}

@Composable
fun AssistantScreen(
    taskViewModel: TaskViewModel,
    calendarViewModel: CalendarViewModel
) {
    var selectedDestination by remember {
        mutableStateOf(AppDestination.TASKS)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AppColors.Background,
        bottomBar = {
            BottomNavigationBar(
                selectedDestination = selectedDestination,
                onDestinationSelected = { destination ->
                    selectedDestination = destination
                }
            )
        }
    ) { innerPadding ->

        val screenModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when (selectedDestination) {
            AppDestination.TASKS -> {
                TasksScreen(
                    viewModel = taskViewModel,
                    modifier = screenModifier
                )
            }

            AppDestination.CALENDAR -> {
                CalendarScreen(
                    viewModel = calendarViewModel,
                    taskViewModel = taskViewModel,
                    modifier = screenModifier
                )
            }

            AppDestination.FINANCE -> {
                FeaturePlaceholderScreen(
                    title = "Финансы",
                    description = "Учитывай доходы и расходы, планируй бюджет и отслеживай свои траты.",
                    modifier = screenModifier
                )
            }

            AppDestination.LECTURES -> {
                FeaturePlaceholderScreen(
                    title = "Лекции",
                    description = "Храни конспекты, учебные материалы и полезные заметки в одном месте.",
                    modifier = screenModifier
                )
            }
        }
    }
}
