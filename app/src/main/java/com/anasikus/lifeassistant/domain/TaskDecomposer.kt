package com.anasikus.lifeassistant.domain

import com.anasikus.lifeassistant.settings.AppText
import com.anasikus.lifeassistant.data.TaskEntity

data class TaskSplitDraft(val title: String, val minutes: Int, val description: String = "")

/** Local editable proposals; no external model or personal-data upload. */
object TaskDecomposer {
    fun suggest(task: TaskEntity): List<TaskSplitDraft> {
        val remaining = TaskSessionPlanner.remaining(task)
        if (remaining < 30) return emptyList()
        val lines = task.description.lines().map { it.trim().trimStart('-', '*', '•', ' ') }.filter { it.length > 3 }
        val development = listOf("разработ", "приложен", "сайт", "раздел", "develop", "app", "website", "feature")
            .any { (task.title + " " + task.description).contains(it, ignoreCase = true) }
        val names = if (lines.size >= 2) lines.take(8) else if (development) listOf(
            AppText.get("Уточнить требования и сценарии"), AppText.get("Спроектировать данные и поведение"), AppText.get("Реализовать основную логику"),
            AppText.get("Сделать интерфейс и связать с данными"), AppText.get("Проверить ошибки и крайние случаи"), AppText.get("Исправить замечания и проверить результат"))
        else listOf(AppText.get("Определить ожидаемый результат"), AppText.get("Подготовить всё необходимое"), AppText.get("Выполнить основную часть"), AppText.get("Проверить и завершить"))
        return distribute(names.take(minOf(names.size, remaining / 15)), remaining)
    }
    fun distribute(names: List<String>, minutes: Int): List<TaskSplitDraft> {
        require(names.size >= 2 && minutes >= names.size * 15)
        return names.mapIndexed { index, name -> TaskSplitDraft(name, minutes / names.size + if (index < minutes % names.size) 1 else 0) }
    }
}
