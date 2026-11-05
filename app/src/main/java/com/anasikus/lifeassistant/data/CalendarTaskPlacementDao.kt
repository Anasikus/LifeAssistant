
package com.anasikus.lifeassistant.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarTaskPlacementDao {

    @Query("SELECT * FROM calendar_task_placements")
    fun getAll(): Flow<List<CalendarTaskPlacementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(placement: CalendarTaskPlacementEntity)

    @Query(
        "DELETE FROM calendar_task_placements WHERE taskId = :taskId"
    )
    suspend fun deleteForTask(taskId: Int)
}
