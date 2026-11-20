package com.anasikus.lifeassistant.settings

object SectionOrder {
    val known = listOf("CALENDAR", "TASKS", "FINANCE", "LECTURES", "SETTINGS")
    fun normalize(saved: String): List<String> = (saved.split(',').filter { it in known } + known).distinct()
    fun move(order: List<String>, name: String, direction: Int): List<String> {
        val result = normalize(order.joinToString(",")).toMutableList()
        val old = result.indexOf(name); val next = old + direction
        if (old >= 0 && next in result.indices) java.util.Collections.swap(result, old, next)
        return result
    }
}
