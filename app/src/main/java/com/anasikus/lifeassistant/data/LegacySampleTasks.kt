package com.anasikus.lifeassistant.data

/** No marker was stored with old demo tasks, so only the intact original set is removed. */
object LegacySampleTasks {
    val DELETE_UNCHANGED = """
        DELETE FROM tasks WHERE id IN (1, 2, 3)
        AND (
            SELECT COUNT(*) FROM tasks
            WHERE ((id = 1 AND title = 'Изучить Kotlin')
                OR (id = 2 AND title = 'Составить план на день')
                OR (id = 3 AND title = 'Отдохнуть после работы'))
                AND description = '' AND completed = 0 AND completedAt IS NULL
                AND startDate != '' AND startDate = deadline
                AND startDate = (SELECT startDate FROM tasks WHERE id = 1)
        ) = 3
        AND NOT EXISTS (
            SELECT 1 FROM calendar_task_placements WHERE taskId IN (1, 2, 3)
        )
    """.trimIndent()
}
