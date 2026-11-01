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
 * Builds per-app usage by replaying the raw UsageEvents stream for a window. The
 * pre-aggregated UsageStats buckets are daily or coarser, too coarse for 15-minute logging.
 */
class UsageCollector(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val labelCache = ConcurrentHashMap<String, String>()

    fun collect(startMs: Long, endMs: Long): List<AppUsage> {
        val trackers = HashMap<String, Tracker>()
        val events = usageStatsManager.queryEvents(startMs, endMs)
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val tracker = trackers.getOrPut(event.packageName) { Tracker(startMs) }
            val component = event.className ?: ""
            val t = event.timeStamp
            when (event.eventType) {
                ACTIVITY_RESUMED -> {
                    if (tracker.foreground.open(component, t)) tracker.launchCount++
                    tracker.lastUsed = maxOf(tracker.lastUsed, t)
                }
                ACTIVITY_PAUSED -> {
                    tracker.foreground.close(component, t)
                    tracker.lastUsed = maxOf(tracker.lastUsed, t)
                }
                FOREGROUND_SERVICE_START -> tracker.service.open(component, t)
                FOREGROUND_SERVICE_STOP -> tracker.service.close(component, t)
            }
        }

        return trackers.mapNotNull { (pkg, tracker) ->
            val fg = tracker.foreground.finish(endMs)
            val fgs = tracker.service.finish(endMs)
            if (fg == 0L && fgs == 0L) return@mapNotNull null
            AppUsage(
                packageName = pkg,
                label = labelFor(pkg),
                foregroundMs = fg,
                foregroundServiceMs = fgs,
                launchCount = tracker.launchCount,
                lastUsed = tracker.lastUsed,
            )
        }.sortedByDescending { it.foregroundMs + it.foregroundServiceMs }
    }

    private fun labelFor(pkg: String): String = labelCache.getOrPut(pkg) {
        val pm = context.packageManager
        try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg // Uninstalled since, or hidden from us.
        }
    }

    private class Tracker(windowStart: Long) {
        val foreground = IntervalTracker(windowStart)
        val service = IntervalTracker(windowStart)
        var launchCount = 0
        var lastUsed = 0L
    }

    /**
     * Sums the time during which at least one component (activity or service) of an app is
     * open. Components are tracked by class name so that switching between two screens of the
     * same app isn't counted twice.
     */
    private class IntervalTracker(private val windowStart: Long) {
        private val open = HashSet<String>()
        private var since = 0L
        private var sawAnyEvent = false
        var totalMs = 0L
            private set

        /** Returns true if the app went from "nothing open" to "something open". */
        fun open(component: String, t: Long): Boolean {
            sawAnyEvent = true
            val wasIdle = open.isEmpty()
            if (open.add(component) && wasIdle) {
                since = t
                return true
            }
            return false
        }

        fun close(component: String, t: Long) {
            if (!sawAnyEvent && open.isEmpty()) {
                // First event is a close: it was already open when the window started.
                totalMs += t - windowStart
            } else if (open.remove(component) && open.isEmpty()) {
                totalMs += t - since
            }
            sawAnyEvent = true
        }

        /** Counts anything still open as running until the end of the window. */
        fun finish(windowEnd: Long): Long {
            if (open.isNotEmpty()) {
                totalMs += windowEnd - since
                open.clear()
            }
            return totalMs
        }
    }

    private companion object {
        // Same values as UsageEvents.Event.ACTIVITY_RESUMED etc. Written out because those
        // names only exist from API 29, while the event values themselves are older.
        const val ACTIVITY_RESUMED = 1  // a.k.a. MOVE_TO_FOREGROUND before API 29
        const val ACTIVITY_PAUSED = 2   // a.k.a. MOVE_TO_BACKGROUND before API 29
        const val FOREGROUND_SERVICE_START = 19
        const val FOREGROUND_SERVICE_STOP = 20
    }
}
