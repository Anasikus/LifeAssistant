
package com.anasikus.lifeassistant.repository

import com.anasikus.lifeassistant.data.CalendarEventDao
import com.anasikus.lifeassistant.data.CalendarEventEntity
import com.anasikus.lifeassistant.data.CalendarEventOverrideDao
import com.anasikus.lifeassistant.data.CalendarEventOverrideEntity
import com.anasikus.lifeassistant.data.CalendarTaskPlacementDao
import com.anasikus.lifeassistant.data.CalendarTaskPlacementEntity
import kotlinx.coroutines.flow.Flow

class CalendarRepository(
    private val eventDao: CalendarEventDao,
    private val placementDao: CalendarTaskPlacementDao,
    private val overrideDao: CalendarEventOverrideDao
) {

    val allEvents: Flow<List<CalendarEventEntity>> =
        eventDao.getAllEvents()

    val allPlacements: Flow<List<CalendarTaskPlacementEntity>> =
        placementDao.getAll()

    val allEventOverrides: Flow<List<CalendarEventOverrideEntity>> =
        overrideDao.getAll()

    suspend fun addEvent(event: CalendarEventEntity) {
        eventDao.insertEvent(event)
    }

    suspend fun updateEvent(event: CalendarEventEntity) {
        eventDao.updateEvent(event)
    }

    suspend fun deleteEvent(event: CalendarEventEntity) {
        eventDao.deleteEvent(event)
        overrideDao.deleteForEvent(event.id)
    }

    suspend fun savePlacement(
        placement: CalendarTaskPlacementEntity
    ) {
        placementDao.save(placement)
    }

    suspend fun deletePlacement(taskId: Int) {
        placementDao.deleteForTask(taskId)
    }

    suspend fun saveEventOverride(
        override: CalendarEventOverrideEntity
    ) {
        overrideDao.save(override)
    }
}
