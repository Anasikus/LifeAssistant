
package com.anasikus.lifeassistant

import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import com.anasikus.lifeassistant.notifications.FinanceMessageCapture
import com.anasikus.lifeassistant.domain.FinanceMessageParser
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import com.anasikus.lifeassistant.notifications.PaymentReminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
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
import com.anasikus.lifeassistant.ui.screens.FinanceScreen
import com.anasikus.lifeassistant.ui.screens.FeaturePlaceholderScreen
import com.anasikus.lifeassistant.ui.screens.TasksScreen
import com.anasikus.lifeassistant.viewmodel.CalendarViewModel
import com.anasikus.lifeassistant.viewmodel.CalendarViewModelFactory
import com.anasikus.lifeassistant.viewmodel.TaskViewModel
import com.anasikus.lifeassistant.viewmodel.TaskViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) importSharedText(intent)

        val database = AppDatabase.getDatabase(applicationContext)
        val planning = database.calendarPlanningDao()
        lifecycleScope.launch(Dispatchers.IO) {
            combine(planning.paymentPlans(), planning.financeTransactions()) { plans, transactions ->
                plans to transactions
            }.collect { (plans, transactions) ->
                PaymentReminders.update(applicationContext, plans, transactions)
            }
        }

        val taskRepository = TaskRepository(database.taskDao())
        val taskFactory = TaskViewModelFactory(taskRepository)
        val taskViewModel = ViewModelProvider(this, taskFactory)
            .get(TaskViewModel::class.java)

        val calendarRepository = CalendarRepository(
            eventDao = database.calendarEventDao(),
            placementDao = database.calendarTaskPlacementDao(),
            overrideDao = database.calendarEventOverrideDao(),
            planning = database.calendarPlanningDao(),
            inbox = database.financeInboxDao()
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
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        importSharedText(intent)
    }

    private fun importSharedText(shared: Intent) {
        if (shared.action != Intent.ACTION_SEND || shared.type != "text/plain") return
        val body = shared.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() } ?: return
        val title = shared.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val at = System.currentTimeMillis()
                FinanceMessageCapture.receive(applicationContext, "MANUAL", "Переданный текст", title, body, at,
                    "share:$at:${FinanceMessageParser.hash(body)}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Текст добавлен в Финансы → Уведомления", Toast.LENGTH_LONG).show()
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Не удалось сохранить текст", Toast.LENGTH_LONG).show()
                }
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
        mutableStateOf(AppDestination.CALENDAR)
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

            AppDestination.FINANCE -> FinanceScreen(calendarViewModel, screenModifier)

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
