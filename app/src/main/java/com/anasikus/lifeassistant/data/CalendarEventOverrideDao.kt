
package com.anasikus.lifeassistant.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventOverrideDao {

    @Query("SELECT * FROM calendar_event_overrides")
    fun getAll(): Flow<List<CalendarEventOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(override: CalendarEventOverrideEntity)

    @Query(
        """
        DELETE FROM calendar_event_overrides
        WHERE eventId = :eventId AND occurrenceDate = :occurrenceDate
        """
    )
    suspend fun deleteOccurrence(
        eventId: Int,
        occurrenceDate: String
    )

    @Query(
        "DELETE FROM calendar_event_overrides WHERE eventId = :eventId"
    )
    suspend fun deleteForEvent(eventId: Int)
}
