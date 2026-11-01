package com.aditya.powermonitor.data.collector

/**
 * The parts of an android.app.usage.UsageEvents.Event we use. A plain class, so the
 * aggregation below can be unit-tested without a phone.
 */
data class RawUsageEvent(
    val packageName: String,
    val className: String?,
    val type: Int,
    val timestamp: Long,
)

/** One app's activity in a window, before its display name is looked up. */
data class AppActivity(
    val packageName: String,
    val foregroundMs: Long,
    val foregroundServiceMs: Long,
    val launchCount: Int,
    val lastUsed: Long,
)

/**
 * Turns a time-ordered stream of usage events into per-app totals for [windowStart, windowEnd).
 * Pure Kotlin: no Android calls, so it's covered by unit tests.
 */
object UsageAggregator {
    // Same values as UsageEvents.Event.ACTIVITY_RESUMED etc. Written out because those
    // names only exist from API 29, while the event values themselves are older.
    const val ACTIVITY_RESUMED = 1  // a.k.a. MOVE_TO_FOREGROUND before API 29
    const val ACTIVITY_PAUSED = 2   // a.k.a. MOVE_TO_BACKGROUND before API 29
    const val FOREGROUND_SERVICE_START = 19
    const val FOREGROUND_SERVICE_STOP = 20

    /**
     * Moving between two screens of one app logs "A paused" then "B resumed" a few hundred
     * milliseconds apart. A reopen within this gap is the same visit, not a new launch.
     */
    const val SAME_VISIT_GAP_MS = 2_000L

    fun aggregate(
        events: Sequence<RawUsageEvent>,
        windowStart: Long,
        windowEnd: Long,
    ): List<AppActivity> {
        val trackers = HashMap<String, Tracker>()
        for (event in events) {
            val tracker = trackers.getOrPut(event.packageName) { Tracker(windowStart) }
            val component = event.className ?: ""
            val t = event.timestamp
            when (event.type) {
                ACTIVITY_RESUMED -> {
                    val cameToFront = tracker.foreground.open(component, t)
                    if (cameToFront && t - tracker.foreground.lastClosedAt > SAME_VISIT_GAP_MS) {
                        tracker.launchCount++
                    }
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
            val fg = tracker.foreground.finish(windowEnd)
            val fgs = tracker.service.finish(windowEnd)
            if (fg == 0L && fgs == 0L) return@mapNotNull null
            AppActivity(pkg, fg, fgs, tracker.launchCount, tracker.lastUsed)
        }.sortedByDescending { it.foregroundMs + it.foregroundServiceMs }
    }

    private class Tracker(windowStart: Long) {
        val foreground = IntervalTracker(windowStart)
        val service = IntervalTracker(windowStart)
        var launchCount = 0
        var lastUsed = 0L
    }

    /**
     * Sums the time during which at least one component (activity or service) of an app is
     * open. Components are tracked by class name so that two screens of the same app open at
     * once aren't counted twice.
     */
    private class IntervalTracker(private val windowStart: Long) {
        private val open = HashSet<String>()
        private var since = 0L
        private var sawAnyEvent = false
        var totalMs = 0L
            private set

        /** When the app last went from "something open" to "nothing open". */
        var lastClosedAt = Long.MIN_VALUE / 2
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
                lastClosedAt = t
            } else if (open.remove(component) && open.isEmpty()) {
                totalMs += t - since
                lastClosedAt = t
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
}
