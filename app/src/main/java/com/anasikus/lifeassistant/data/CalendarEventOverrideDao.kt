
package com.anasikus.lifeassistant.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventOverrideDao {

    @Query("SELECT * FROM calendar_event_overrides")
    fun getAll(): Flow<List<CalendarEventOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(override: CalendarEventOverrideEntity)

    @Update
    suspend fun updateEvent(event: CalendarEventEntity)

    /** Одна транзакция: описание серии и время выбранного экземпляра. */
    @Transaction
    suspend fun saveEditedOccurrence(
        event: CalendarEventEntity,
        occurrence: CalendarEventOverrideEntity?
    ) {
        updateEvent(event)
        if (occurrence == null) deleteForEvent(event.id) else save(occurrence)
    }

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
