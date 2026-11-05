
package com.anasikus.lifeassistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskEntity::class,
        CalendarEventEntity::class,
        CalendarTaskPlacementEntity::class,
        CalendarEventOverrideEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun calendarTaskPlacementDao(): CalendarTaskPlacementDao
    abstract fun calendarEventOverrideDao(): CalendarEventOverrideDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE tasks ADD COLUMN description TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE tasks ADD COLUMN startDate TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE tasks ADD COLUMN deadline TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE tasks ADD COLUMN completedAt TEXT"
                )

                // Заполняем даты у ранее созданных задач.
                db.execSQL(
                    "UPDATE tasks SET startDate = date('now') WHERE startDate = ''"
                )
                db.execSQL(
                    "UPDATE tasks SET deadline = date('now') WHERE deadline = ''"
                )
                db.execSQL(
                    "UPDATE tasks SET completedAt = date('now') WHERE completed = 1"
                )
            }
        }


        val MIGRATION_2_3 = object : Migration(2, 3) {

            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS calendar_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                startDate TEXT NOT NULL,
                startMinute INTEGER NOT NULL,
                durationMinutes INTEGER NOT NULL,
                repeatDaysMask INTEGER NOT NULL
            )
            """.trimIndent()
                )
            }
        }


        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {

            override fun migrate(
                database: androidx.sqlite.db.SupportSQLiteDatabase
            ) {
                database.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS calendar_task_placements (
                taskId INTEGER NOT NULL PRIMARY KEY,
                date TEXT NOT NULL,
                startMinute INTEGER NOT NULL,
                durationMinutes INTEGER NOT NULL,
                hidden INTEGER NOT NULL
            )
            """.trimIndent()
                )

                database.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS calendar_event_overrides (
                eventId INTEGER NOT NULL,
                occurrenceDate TEXT NOT NULL,
                date TEXT NOT NULL,
                startMinute INTEGER NOT NULL,
                durationMinutes INTEGER NOT NULL,
                deleted INTEGER NOT NULL,
                PRIMARY KEY(eventId, occurrenceDate)
            )
            """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "life_assistant_database"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4
                        )
                    .build()
                    .also {
                        INSTANCE = it
                    }
            }
        }
    }
}
