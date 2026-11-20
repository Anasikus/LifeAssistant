package com.anasikus.lifeassistant.ui.screens

import com.anasikus.lifeassistant.settings.AppText
import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.anasikus.lifeassistant.settings.AppPreferences
import com.anasikus.lifeassistant.ui.AppColors
import com.anasikus.lifeassistant.ui.components.ChoiceField
import com.anasikus.lifeassistant.ui.navigation.AppDestination

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier.background(AppColors.Background).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(AppText.get("Настройки"), style = MaterialTheme.typography.headlineSmall, color = AppColors.MainText)
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(AppText.get("Язык"), style = MaterialTheme.typography.titleMedium)
                ChoiceField(AppText.get("Язык приложения"), if (AppPreferences.language == "ru") AppText.get("Русский") else "English",
                    listOf("ru" to AppText.get("Русский"), "en" to "English")) { language ->
                    if (language != AppPreferences.language) { AppPreferences.updateLanguage(language); generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()?.recreate() }
                }
                Text(AppText.get("Язык меняется только у интерфейса. Названия задач, категорий, заметки и сообщения остаются как ты их ввела."), color = AppColors.SecondaryText)
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(AppText.get("Оформление"), style = MaterialTheme.typography.titleMedium)
                ChoiceField(AppText.get("Тема"), if (AppPreferences.dark) AppText.get("Тёмная") else AppText.get("Светлая"), listOf("DARK" to AppText.get("Тёмная"), "LIGHT" to AppText.get("Светлая"))) { value ->
                    AppPreferences.updateDarkTheme(value == "DARK")
                    generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()?.recreate()
                }
                val accents = listOf("PURPLE" to AppText.get("Фиолетовый"), "BLUE" to AppText.get("Синий"), "GREEN" to AppText.get("Зелёный"), "ROSE" to AppText.get("Розовый"), "ORANGE" to AppText.get("Оранжевый"))
                ChoiceField(AppText.get("Основной цвет"), accents.first { it.first == AppPreferences.accent }.second, accents) { AppPreferences.updateAccent(it) }
                Text(AppText.get("Порядок разделов"), style = MaterialTheme.typography.titleSmall)
                AppPreferences.order.forEachIndexed { index, name ->
                    val destination = AppDestination.valueOf(name)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(destination.title, Modifier.weight(1f))
                        TextButton(enabled = index > 0, onClick = { AppPreferences.move(name, -1) }) { Text("↑") }
                        TextButton(enabled = index < AppPreferences.order.lastIndex, onClick = { AppPreferences.move(name, 1) }) { Text("↓") }
                    }
                }
            }
        }
    }
}
