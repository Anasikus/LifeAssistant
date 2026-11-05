package com.anasikus.lifeassistant.ui.navigation

enum class AppDestination(
    val title: String,
    val symbol: String
) {
    TASKS("Задачи", "✓"),
    CALENDAR("Календарь", "▦"),
    FINANCE("Финансы", "₽"),
    LECTURES("Лекции", "▤")
}