package com.anasikus.lifeassistant.domain

import com.anasikus.lifeassistant.data.TaskEntity

data class TaskProgress(val total: Int, val spent: Int) {
    val remaining get() = (total - spent).coerceAtLeast(0)
    companion object {
        fun of(task: TaskEntity, all: List<TaskEntity>): TaskProgress {
            val children = all.filter { it.parentId == task.id }
            return if (!task.isProject) TaskProgress(task.estimatedDurationMinutes, task.spentMinutes)
            else TaskProgress(task.spentMinutes + children.sumOf { it.estimatedDurationMinutes }, task.spentMinutes + children.sumOf { it.spentMinutes })
        }
    }
}
