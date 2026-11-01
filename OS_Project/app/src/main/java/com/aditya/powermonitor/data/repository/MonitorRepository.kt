package com.aditya.powermonitor.data.repository

import androidx.room.withTransaction
import com.aditya.powermonitor.data.collector.AppUsage
import com.aditya.powermonitor.data.collector.BatteryCollector
import com.aditya.powermonitor.data.collector.BatteryReading
import com.aditya.powermonitor.data.collector.UsageCollector
import com.aditya.powermonitor.data.db.AppDatabase
import com.aditya.powermonitor.data.db.entity.AppUsageRecord
import com.aditya.powermonitor.data.db.entity.AppUsageTotal
import com.aditya.powermonitor.data.db.entity.BatterySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** What one logging run saved. */
data class LogResult(
    val intervalStart: Long,
    val intervalEnd: Long,
    val batteryDropPercent: Int,
    val appsLogged: Int,
)

/**
 * The single place that reads collectors and writes to the database. The debug screen's
 * "Log now" button and (from Step 5) the background worker both call logSnapshot().
 */
class MonitorRepository(
    private val db: AppDatabase,
    private val battery: BatteryCollector,
    private val usage: UsageCollector,
) {
    private val batteryDao = db.batteryDao()
    private val usageDao = db.appUsageDao()

    fun observeLatestSnapshots(limit: Int): Flow<List<BatterySnapshot>> =
        batteryDao.observeLatest(limit)

    fun observeSnapshotCount(): Flow<Int> = batteryDao.observeCount()

    fun observeRecentUsage(limit: Int): Flow<List<AppUsageRecord>> =
        usageDao.observeRecent(limit)

    fun observeUsageCount(): Flow<Int> = usageDao.observeCount()

    fun observeSnapshotsBetween(from: Long, to: Long): Flow<List<BatterySnapshot>> =
        batteryDao.observeBetween(from, to)

    fun observeAppTotals(from: Long, to: Long): Flow<List<AppUsageTotal>> =
        usageDao.observeTotals(from, to)

    fun observeRecordsForApp(
        packageName: String,
        from: Long,
        to: Long,
    ): Flow<List<AppUsageRecord>> = usageDao.observeRecordsForApp(packageName, from, to)

    suspend fun getSnapshotsBetween(from: Long, to: Long): List<BatterySnapshot> =
        batteryDao.getBetween(from, to)

    suspend fun getRecords(packageName: String?, from: Long, to: Long): List<AppUsageRecord> =
        usageDao.getRecords(packageName, from, to)

    /** Reads the battery right now without saving anything (for the dashboard header). */
    fun readBatteryNow(): BatteryReading = battery.read()

    /**
     * Logs the interval since the previous snapshot: the battery now, each app's usage in
     * that interval, and each app's estimated share of the battery drop.
     */
    suspend fun logSnapshot(): LogResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val previous = batteryDao.latest()
        val start = DrainEstimator.intervalStart(previous?.timestamp, now)

        val reading = battery.read()
        val apps = usage.collect(start, now)
        val drop = previous?.let {
            DrainEstimator.batteryDrop(
                it.levelPercent, it.isCharging, reading.levelPercent, reading.isCharging,
            )
        } ?: 0
        val shares = DrainEstimator.shares(apps, drop)

        // A transaction makes both inserts succeed or fail together.
        db.withTransaction {
            batteryDao.insert(reading.toEntity())
            usageDao.insertAll(
                apps.mapIndexed { i, app -> app.toEntity(start, now, shares[i]) },
            )
        }
        LogResult(start, now, drop, apps.size)
    }

    /** Deletes everything older than [keepDays] days. Returns how many rows were removed. */
    suspend fun deleteOlderThan(keepDays: Int): Int = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - keepDays * 24 * 60 * 60_000L
        db.withTransaction {
            batteryDao.deleteOlderThan(cutoff) + usageDao.deleteOlderThan(cutoff)
        }
    }

    private fun BatteryReading.toEntity() = BatterySnapshot(
        timestamp = timestamp,
        levelPercent = levelPercent,
        isCharging = isCharging,
        plugType = plugType,
        health = health,
        temperatureC = temperatureC,
        voltageMv = voltageMv,
        currentMicroA = currentMicroA,
        chargeCounterMicroAh = chargeCounterMicroAh,
    )

    private fun AppUsage.toEntity(start: Long, end: Long, share: Float) = AppUsageRecord(
        intervalStart = start,
        intervalEnd = end,
        packageName = packageName,
        appLabel = label,
        foregroundMs = foregroundMs,
        foregroundServiceMs = foregroundServiceMs,
        launchCount = launchCount,
        lastUsed = lastUsed,
        estimatedDrainPercent = share,
    )
}
