package com.aditya.powermonitor.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aditya.powermonitor.data.db.entity.BatterySnapshot
import kotlinx.coroutines.flow.Flow

/**
 * Room generates the implementation of this interface at compile time and checks every SQL
 * query against the tables. Functions returning Flow re-emit whenever the table changes.
 */
@Dao
interface BatteryDao {
    @Insert
    suspend fun insert(snapshot: BatterySnapshot): Long

    @Query("SELECT * FROM battery_snapshots ORDER BY timestamp DESC LIMIT 1")
    suspend fun latest(): BatterySnapshot?

    @Query("SELECT * FROM battery_snapshots ORDER BY timestamp DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<BatterySnapshot>>

    @Query(
        "SELECT * FROM battery_snapshots WHERE timestamp >= :from AND timestamp < :to " +
            "ORDER BY timestamp ASC"
    )
    fun observeBetween(from: Long, to: Long): Flow<List<BatterySnapshot>>

    @Query("SELECT COUNT(*) FROM battery_snapshots")
    fun observeCount(): Flow<Int>
}
