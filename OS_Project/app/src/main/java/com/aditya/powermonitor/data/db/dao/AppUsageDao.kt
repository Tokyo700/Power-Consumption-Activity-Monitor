package com.aditya.powermonitor.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aditya.powermonitor.data.db.entity.AppUsageRecord
import com.aditya.powermonitor.data.db.entity.AppUsageTotal
import kotlinx.coroutines.flow.Flow

@Dao
interface AppUsageDao {
    @Insert
    suspend fun insertAll(records: List<AppUsageRecord>)

    @Query("SELECT * FROM app_usage_records ORDER BY intervalEnd DESC, foregroundMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AppUsageRecord>>

    /**
     * Per-app totals for intervals that overlap [from, to). An interval that straddles
     * midnight counts toward both days; with 15-minute intervals the error is small.
     */
    @Query(
        """
        SELECT packageName,
               MAX(appLabel) AS appLabel,
               SUM(foregroundMs) AS foregroundMs,
               SUM(foregroundServiceMs) AS foregroundServiceMs,
               SUM(launchCount) AS launchCount,
               SUM(estimatedDrainPercent) AS estimatedDrainPercent
        FROM app_usage_records
        WHERE intervalEnd > :from AND intervalStart < :to
        GROUP BY packageName
        ORDER BY estimatedDrainPercent DESC, foregroundMs DESC
        """
    )
    fun observeTotals(from: Long, to: Long): Flow<List<AppUsageTotal>>

    /** One app's individual interval rows in [from, to), newest first. */
    @Query(
        """
        SELECT * FROM app_usage_records
        WHERE packageName = :packageName AND intervalEnd > :from AND intervalStart < :to
        ORDER BY intervalEnd DESC
        """
    )
    fun observeRecordsForApp(packageName: String, from: Long, to: Long): Flow<List<AppUsageRecord>>

    /** One-time read for CSV export; a null packageName means every app. */
    @Query(
        """
        SELECT * FROM app_usage_records
        WHERE (:packageName IS NULL OR packageName = :packageName)
          AND intervalEnd > :from AND intervalStart < :to
        ORDER BY intervalStart ASC, foregroundMs DESC
        """
    )
    suspend fun getRecords(packageName: String?, from: Long, to: Long): List<AppUsageRecord>

    /** Returns how many rows were deleted. */
    @Query("DELETE FROM app_usage_records WHERE intervalEnd < :before")
    suspend fun deleteOlderThan(before: Long): Int

    @Query("SELECT COUNT(*) FROM app_usage_records")
    fun observeCount(): Flow<Int>
}
