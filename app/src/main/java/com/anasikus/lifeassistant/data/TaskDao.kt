package com.anasikus.lifeassistant.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.anasikus.lifeassistant.domain.*

@Dao
interface TaskDao {
    @Query("SELECT * FROM task_groups ORDER BY name")
    fun groups(): Flow<List<TaskGroupEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGroup(group: TaskGroupEntity)
    @Query("UPDATE tasks SET groupId=NULL WHERE groupId=:id")
    suspend fun detachGroup(id: Int)
    @Query("DELETE FROM task_groups WHERE id=:id")
    suspend fun removeGroup(id: Int)
    @Transaction
    suspend fun deleteGroup(id: Int) { detachGroup(id); removeGroup(id) }
    @Query("SELECT * FROM tasks")
    suspend fun rows(): List<TaskEntity>
    @Query("SELECT * FROM task_sessions ORDER BY date,startMinute,id")
    fun sessions(): Flow<List<TaskSessionEntity>>
    @Query("SELECT * FROM task_sessions")
    suspend fun sessionRows(): List<TaskSessionEntity>
    @Query("SELECT * FROM task_sessions WHERE id=:id")
    suspend fun session(id: Long): TaskSessionEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun writeSession(value: TaskSessionEntity)
    @Query("DELETE FROM task_sessions WHERE id=:id")
    suspend fun deleteSession(id: Long)
    @Query("DELETE FROM task_sessions WHERE taskId=:id AND accounted=0")
    suspend fun clearPendingSessions(id: Int)
    @Query("DELETE FROM task_sessions WHERE taskId=:id")
    suspend fun deleteSessions(id: Int)
    @Query("SELECT * FROM calendar_events")
    suspend fun events(): List<CalendarEventEntity>
    @Query("SELECT * FROM calendar_event_overrides")
    suspend fun overrides(): List<CalendarEventOverrideEntity>
    @Query("SELECT * FROM shift_patterns WHERE id=1")
    suspend fun pattern(): ShiftPatternEntity?
    @Query("SELECT * FROM shift_days")
    suspend fun shifts(): List<ShiftDayEntity>
    @Query("SELECT * FROM calendar_task_placements")
    suspend fun placements(): List<CalendarTaskPlacementEntity>
    @Query("SELECT (SELECT count(*) FROM calendar_events)+(SELECT count(*) FROM calendar_event_overrides)+(SELECT count(*) FROM shift_patterns)+(SELECT count(*) FROM shift_days)+(SELECT count(*) FROM calendar_task_placements)")
    fun planningChanges(): Flow<Int>

    @Transaction
    suspend fun reconcile(today: String, minute: Int) {
        val before = rows().associateBy { it.id }.toMutableMap()
        val old = sessionRows()
        for (slot in old.filter { !it.accounted && TaskSessionPlanner.ended(it, today, minute) }) {
            val task = before[slot.taskId] ?: continue
            if (!task.completed && !task.frozen && !task.archived) {
                val updated = task.copy(spentMinutes = minOf(task.estimatedDurationMinutes, task.spentMinutes + slot.durationMinutes))
                updateTask(updated); before[task.id] = updated
            }
            writeSession(slot.copy(accounted = true))
        }
        archiveCompletedBefore(today)
        val tasks = rows()
        val activeIds = tasks.filter { !it.completed && !it.archived && !it.frozen && !it.isProject && TaskSessionPlanner.remaining(it) > 0 }.map { it.id }.toSet()
        val all = sessionRows()
        // Freeze/archive/completion release pending windows, and preserve already accounted history.
        all.filter { !it.accounted && it.taskId !in activeIds }.forEach { deleteSession(it.id) }
        val retained = all.filter { it.accounted || it.taskId in activeIds && (!it.automatic || it.date < today || it.date == today && it.startMinute <= minute) }
        val end = CalendarDateUtils.addDays(today, 119)
        val occurrences = CalendarEventRules.expand(events(), today, end, overrides()).toMutableList()
        val shiftPattern = pattern()
        ShiftRules.days(today, end, shiftPattern, shifts()).filter { it.status == "WORK" && !it.predicted }.forEach { day ->
            val event = CalendarEventEntity(id = -1, title = "", startDate = day.date, startMinute = day.startMinute, durationMinutes = day.durationMinutes)
            occurrences.addAll(CalendarEventRules.expand(listOf(event), today, end))
        }
        val proposed = TaskSessionPlanner.plan(tasks, occurrences, retained, today, minute, end, placements())
        val retainedIds = retained.map { it.id }.toSet()
        val replaceable = all.filter { it.id !in retainedIds && it.taskId in activeIds }
        val existingByKey = replaceable.associateBy { listOf(it.taskId, it.date, it.startMinute, it.durationMinutes) }
        val future = proposed.filter { it.id == 0L }.map { slot ->
            existingByKey[listOf(slot.taskId, slot.date, slot.startMinute, slot.durationMinutes)] ?: slot
        }
        val newIds = future.map { it.id }.toSet()
        replaceable.filter { it.id !in newIds }.forEach { deleteSession(it.id) }
        future.filter { it.id == 0L }.forEach { writeSession(it) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun writeHiddenPlacement(value: CalendarTaskPlacementEntity)
    @Transaction
    suspend fun skipSession(id: Long) {
        val slot = session(id) ?: return
        if (slot.accounted) return
        writeHiddenPlacement(CalendarTaskPlacementEntity(slot.taskId, slot.date, slot.startMinute, slot.durationMinutes, hidden = true))
        sessionRows().filter { it.taskId == slot.taskId && it.date == slot.date && !it.accounted }.forEach { deleteSession(it.id) }
    }

    @Transaction
    suspend fun moveSession(id: Long, date: String, start: Int, duration: Int) {
        val slot = session(id) ?: return
        require(!slot.accounted)
        val task = task(slot.taskId) ?: return
        val others = sessionRows().filter { it.taskId == task.id && !it.accounted && it.id != id }
        val reserved = others.filter { !it.automatic || it.date < date || it.date == date && it.startMinute <= start }.sumOf { it.durationMinutes }
        val available = (TaskSessionPlanner.remaining(task) - reserved).coerceAtLeast(1)
        val actual = duration.coerceIn(1, (TaskSessionPlanner.MAX_BUDGET - task.spentMinutes - reserved).coerceIn(1, 1440))
        if (actual > available) updateTask(task.copy(estimatedDurationMinutes = minOf(TaskSessionPlanner.MAX_BUDGET, task.estimatedDurationMinutes + actual - available)))
        writeSession(slot.copy(date = date, startMinute = start.coerceIn(0, 1440 - actual), durationMinutes = actual, automatic = false))
        // Future automatic blocks are regenerated from the remaining budget.
        others.filter { it.automatic && (it.date > date || it.date == date && it.startMinute > start) }.forEach { deleteSession(it.id) }
    }

    @Transaction
    suspend fun split(id: Int, expectedRemaining: Int, drafts: List<TaskSplitDraft>) {
        val parent = task(id) ?: return
        require(parent.parentId == null && !parent.isProject && !parent.completed && !parent.archived && !parent.frozen)
        require(TaskSessionPlanner.remaining(parent) == expectedRemaining)
        require(drafts.size in 2..12 && drafts.all { it.title.isNotBlank() && it.minutes >= 15 })
        require(drafts.sumOf { it.minutes } == expectedRemaining)
        clearPendingSessions(id); deletePlacement(id)
        updateTask(parent.copy(isProject = true))
        drafts.forEach { draft -> insertTask(parent.copy(id = 0, title = draft.title.trim(),
            description = draft.description, estimatedDurationMinutes = draft.minutes, spentMinutes = 0,
            parentId = parent.id, completed = false, completedAt = null, isProject = false)) }
    }

    @Query("SELECT * FROM tasks ORDER BY id ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun task(id: Int): TaskEntity?

    @Insert
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("UPDATE tasks SET archived = 1 WHERE completed = 1 AND completedAt IS NOT NULL AND completedAt < :today AND archived = 0 AND autoArchiveSuppressed = 0")
    suspend fun archiveCompletedBefore(today: String)

    @Query("UPDATE tasks SET archived = :archived, autoArchiveSuppressed = :restored WHERE id = :id")
    suspend fun setArchiveState(id: Int, archived: Boolean, restored: Boolean)

    @Query("DELETE FROM calendar_task_placements WHERE taskId = :id")
    suspend fun deletePlacement(id: Int)

    @Query("DELETE FROM calendar_task_placements WHERE taskId = :id AND date < :today")
    suspend fun deletePastPlacement(id: Int, today: String)

    @Query("UPDATE calendar_task_placements SET durationMinutes = :duration, startMinute = min(startMinute, 1440 - :duration) WHERE taskId = :id")
    suspend fun updatePlacementDuration(id: Int, duration: Int)

    @Query("UPDATE tasks SET frozen = :frozen WHERE id = :id")
    suspend fun setFrozen(id: Int, frozen: Boolean)

    @Transaction
    suspend fun freeze(id: Int, frozen: Boolean, today: String) {
        setFrozen(id, frozen)
        rows().filter { it.parentId == id }.forEach { setFrozen(it.id, frozen) }
        if (!frozen) deletePastPlacement(id, today)
    }

    @Transaction
    suspend fun editTask(value: TaskEntity, adjustSpent: Boolean = false) {
        val old = task(value.id) ?: return
        val completionChanged = old.completed != value.completed || old.completedAt != value.completedAt
        val spent = if (adjustSpent) value.spentMinutes else old.spentMinutes
        val budget = if (old.isProject) old.estimatedDurationMinutes else maxOf(value.estimatedDurationMinutes, spent)
        updateTask(value.copy(archived = old.archived, frozen = old.frozen, spentMinutes = spent, estimatedDurationMinutes = budget,
            isProject = old.isProject, parentId = old.parentId,
            autoArchiveSuppressed = if (completionChanged) false else old.autoArchiveSuppressed))
        if (old.estimatedDurationMinutes != value.estimatedDurationMinutes) {
            clearPendingSessions(value.id); deletePlacement(value.id)
        }
        if (adjustSpent) clearPendingSessions(value.id)
        if (completionChanged) {
            rows().filter { it.parentId == value.id }.forEach { child ->
                updateTask(child.copy(completed = value.completed, completedAt = value.completedAt))
            }
        }
        if (value.groupId != old.groupId) rows().filter { it.parentId == value.id }.forEach { updateTask(it.copy(groupId = value.groupId)) }
        old.parentId?.let { parentId ->
            val parent = task(parentId)
            val children = rows().filter { it.parentId == parentId }
            if (parent != null && children.isNotEmpty()) {
                val complete = children.all { it.completed }
                updateTask(parent.copy(completed = complete, completedAt = if (complete) children.mapNotNull { it.completedAt }.maxOrNull() else null,
                    archived = parent.archived && complete, autoArchiveSuppressed = false))
            }
        }
    }

    @Transaction
    suspend fun archive(id: Int, archived: Boolean, today: String) {
        setArchiveState(id, archived, restored = !archived)
        rows().filter { it.parentId == id }.forEach { setArchiveState(it.id, archived, restored = !archived) }
        if (!archived) deletePastPlacement(id, today)
    }

    @Transaction
    suspend fun removeTask(value: TaskEntity) {
        rows().filter { it.parentId == value.id }.forEach { child -> deletePlacement(child.id); deleteSessions(child.id); deleteTask(child) }
        deletePlacement(value.id); deleteSessions(value.id)
        deleteTask(value)
        value.parentId?.let { id ->
            val parent = task(id)
            val children = rows().filter { it.parentId == id }
            if (parent != null && children.isEmpty()) updateTask(parent.copy(isProject = false, completed = false, completedAt = null))
            else if (parent != null && children.all { it.completed }) updateTask(parent.copy(completed = true, completedAt = children.mapNotNull { it.completedAt }.maxOrNull()))
        }
    }
}
