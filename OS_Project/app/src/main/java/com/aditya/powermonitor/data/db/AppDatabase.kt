package com.aditya.powermonitor.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.aditya.powermonitor.data.db.dao.AppUsageDao
import com.aditya.powermonitor.data.db.dao.BatteryDao
import com.aditya.powermonitor.data.db.entity.AppUsageRecord
import com.aditya.powermonitor.data.db.entity.BatterySnapshot

/**
 * The SQLite database file (power_monitor.db, in the app's private storage). Bump `version`
 * whenever a table changes; exportSchema is off because we don't write migrations yet.
 */
@Database(
    entities = [BatterySnapshot::class, AppUsageRecord::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun batteryDao(): BatteryDao
    abstract fun appUsageDao(): AppUsageDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "power_monitor.db")
                // During development, wipe and recreate on a version bump instead of crashing.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
