
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
        CalendarEventOverrideEntity::class,
        EventCategoryEntity::class,
        ShiftPatternEntity::class,
        ShiftDayEntity::class,
        SpecialDateEntity::class,
        FinanceTransactionEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun calendarTaskPlacementDao(): CalendarTaskPlacementDao
    abstract fun calendarEventOverrideDao(): CalendarEventOverrideDao

    abstract fun calendarPlanningDao(): CalendarPlanningDao

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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE calendar_events ADD COLUMN categoryId INTEGER")
                database.execSQL("ALTER TABLE calendar_events ADD COLUMN oneTimePaymentCents INTEGER")
                database.execSQL("CREATE TABLE IF NOT EXISTS event_categories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, kind TEXT NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS shift_patterns (id INTEGER NOT NULL PRIMARY KEY, enabled INTEGER NOT NULL, mode TEXT NOT NULL, anchorDate TEXT NOT NULL, workDays INTEGER NOT NULL, offDays INTEGER NOT NULL, startMinute INTEGER NOT NULL, durationMinutes INTEGER NOT NULL, hourlyRateCents INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS shift_days (date TEXT NOT NULL PRIMARY KEY, status TEXT NOT NULL, startMinute INTEGER NOT NULL, durationMinutes INTEGER NOT NULL, hourlyRateCents INTEGER NOT NULL, predicted INTEGER NOT NULL, confidence INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS special_dates (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, date TEXT NOT NULL, yearly INTEGER NOT NULL, source TEXT NOT NULL)")
                listOf("Личное" to "GENERAL", "Работа" to "WORK", "Подработка" to "SIDE_JOB", "Праздники" to "HOLIDAY").forEach { (name, kind) ->
                    database.execSQL("INSERT INTO event_categories(name, kind) VALUES (?, ?)", arrayOf(name, kind))
                }
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE calendar_events ADD COLUMN hourlyRateCents INTEGER")
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS finance_transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL,
                        income INTEGER NOT NULL,
                        amountCents INTEGER NOT NULL,
                        note TEXT NOT NULL,
                        sourceKey TEXT,
                        sourceName TEXT
                    )""".trimIndent()
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
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            listOf("Личное" to "GENERAL", "Работа" to "WORK", "Подработка" to "SIDE_JOB", "Праздники" to "HOLIDAY").forEach { (name, kind) ->
                                db.execSQL("INSERT INTO event_categories(name, kind) VALUES (?, ?)", arrayOf(name, kind))
                            }
                        }
                    })
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6
                        )
                    .build()
                    .also {
                        INSTANCE = it
                    }
            }
        }
    }
}
