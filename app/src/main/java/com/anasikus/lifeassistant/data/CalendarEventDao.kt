
package com.anasikus.lifeassistant.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {

    @Query(
        "SELECT * FROM calendar_events ORDER BY startDate, startMinute"
    )
    fun getAllEvents(): Flow<List<CalendarEventEntity>>

    @Insert
    suspend fun insertEvent(event: CalendarEventEntity)

    @Update
    suspend fun updateEvent(event: CalendarEventEntity)

    @Delete
    suspend fun deleteEvent(event: CalendarEventEntity)
}
