package com.anasikus.lifeassistant.ui.navigation

import com.anasikus.lifeassistant.settings.AppText

enum class AppDestination(
    private val russianTitle: String,
    val symbol: String
) {
    TASKS("Задачи", "✓"),
    CALENDAR("Календарь", "▦"),
    FINANCE("Финансы", "₽"),
    LECTURES("Лекции", "▤"),
    SETTINGS("Настройки", "⚙");

    val title: String get() = when (this) {
        TASKS -> AppText.get("Задачи"); CALENDAR -> AppText.get("Календарь"); FINANCE -> AppText.get("Финансы"); LECTURES -> AppText.get("Лекции"); SETTINGS -> AppText.get("Настройки")
    }
}
