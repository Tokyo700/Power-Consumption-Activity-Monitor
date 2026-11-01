package com.aditya.powermonitor.data.collector

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentHashMap

/** How one app was used during a time window. */
data class AppUsage(
    val packageName: String,
    val label: String,
    /** Time with at least one of the app's screens visible and in front. */
    val foregroundMs: Long,
    /**
     * Time a foreground service was running (music, navigation, uploads...). This is the
     * closest thing to "background activity" Android exposes; only reported on API 29+.
     */
    val foregroundServiceMs: Long,
    /** How many times the app came to the front. */
    val launchCount: Int,
    val lastUsed: Long,
)

/**
 * Reads the raw UsageEvents stream for a window and hands it to UsageAggregator. The
 * pre-aggregated UsageStats buckets are daily or coarser, too coarse for 15-minute logging.
 */
class UsageCollector(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val labelCache = ConcurrentHashMap<String, String>()

    fun collect(startMs: Long, endMs: Long): List<AppUsage> =
        UsageAggregator.aggregate(readEvents(startMs, endMs), startMs, endMs).map {
            AppUsage(
                packageName = it.packageName,
                label = labelFor(it.packageName),
                foregroundMs = it.foregroundMs,
                foregroundServiceMs = it.foregroundServiceMs,
                launchCount = it.launchCount,
                lastUsed = it.lastUsed,
            )
        }

    /** The system reuses one Event object while iterating, so copy each into a RawUsageEvent. */
    private fun readEvents(startMs: Long, endMs: Long): Sequence<RawUsageEvent> = sequence {
        val events = usageStatsManager.queryEvents(startMs, endMs)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            yield(RawUsageEvent(event.packageName, event.className, event.eventType, event.timeStamp))
        }
    }

    private fun labelFor(pkg: String): String = labelCache.getOrPut(pkg) {
        val pm = context.packageManager
        try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg // Uninstalled since, or hidden from us.
        }
    }
}
