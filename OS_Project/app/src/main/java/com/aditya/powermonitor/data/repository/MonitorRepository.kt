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
        // Start where the last run ended so intervals never overlap. If there is no previous
        // run, or it's very old (app was force-stopped, phone was off), cover a default window.
        val start = previous?.timestamp
            ?.takeIf { now - it in 1..MAX_INTERVAL_MS }
            ?: (now - DEFAULT_INTERVAL_MS)

        val reading = battery.read()
        val apps = usage.collect(start, now)
        val drop = batteryDrop(previous, reading)
        val shares = estimateShares(apps, drop)

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

    /** Percentage points lost since the previous snapshot; 0 if charging or no baseline. */
    private fun batteryDrop(previous: BatterySnapshot?, now: BatteryReading): Int {
        if (previous == null || previous.isCharging || now.isCharging) return 0
        return (previous.levelPercent - now.levelPercent).coerceAtLeast(0)
    }

    /**
     * Android doesn't expose per-app battery use, so we estimate it: split the interval's
     * drop between apps in proportion to how long they were active. Foreground time counts
     * fully (screen on, CPU busy); foreground-service time counts less because the screen
     * is usually off. The remainder of real drain (screen, radio, idle) is spread with it.
     */
    private fun estimateShares(apps: List<AppUsage>, dropPercent: Int): List<Float> {
        val weights = apps.map { it.foregroundMs + it.foregroundServiceMs * SERVICE_WEIGHT }
        val total = weights.sum()
        if (dropPercent == 0 || total <= 0.0) return List(apps.size) { 0f }
        return weights.map { (dropPercent * it / total).toFloat() }
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

    private companion object {
        const val DEFAULT_INTERVAL_MS = 15 * 60_000L
        const val MAX_INTERVAL_MS = 6 * 60 * 60_000L
        const val SERVICE_WEIGHT = 0.3
    }
}
