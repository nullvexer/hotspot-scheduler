package com.iranjan.hotspotscheduler.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RoutineEntity::class, UsageDayEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao
    abstract fun usageDao(): UsageDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE routines ADD COLUMN mobileData INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE routines ADD COLUMN hotspotPassword TEXT")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "hotspot_scheduler.db")
                .addMigrations(*MIGRATIONS)
                // NO fallbackToDestructiveMigration(): every row here is a user-authored
                // schedule, so silently dropping the table on a missing migration is worse than
                // failing loudly. A future version bump MUST add a migration here.
                .build()
    }
}
