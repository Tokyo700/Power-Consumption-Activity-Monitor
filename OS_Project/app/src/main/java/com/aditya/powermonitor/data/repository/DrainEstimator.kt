package com.aditya.powermonitor.data.repository

import com.aditya.powermonitor.data.collector.AppUsage

/**
 * The battery-estimate rules, kept free of Android and database calls so they can be
 * unit-tested. Used by MonitorRepository when logging and by the Dashboard for daily totals.
 */
object DrainEstimator {
    const val DEFAULT_INTERVAL_MS = 15 * 60_000L
    const val MAX_INTERVAL_MS = 6 * 60 * 60_000L

    /** How much a minute of foreground-service time counts relative to a foreground minute. */
    const val SERVICE_WEIGHT = 0.3

    /**
     * Where a new logging interval starts: at the previous snapshot, so intervals never
     * overlap. With no previous snapshot, or one that is too old or in the future (clock
     * changed), it covers just the default window.
     */
    fun intervalStart(previousTimestamp: Long?, now: Long): Long =
        previousTimestamp
            ?.takeIf { now - it in 1..MAX_INTERVAL_MS }
            ?: (now - DEFAULT_INTERVAL_MS)

    /** Percentage points lost between two readings; 0 if charging at either end. */
    fun batteryDrop(
        previousLevel: Int,
        previousCharging: Boolean,
        currentLevel: Int,
        currentCharging: Boolean,
    ): Int {
        if (previousCharging || currentCharging) return 0
        return (previousLevel - currentLevel).coerceAtLeast(0)
    }

    /**
     * Android doesn't expose per-app battery use, so we estimate it: split the interval's
     * drop between apps in proportion to how long they were active. Foreground time counts
     * fully (screen on, CPU busy); foreground-service time counts less because the screen
     * is usually off. Returns one share per app, in the same order, summing to dropPercent
     * (or all zeros when nothing can be attributed).
     */
    fun shares(apps: List<AppUsage>, dropPercent: Int): List<Float> {
        val weights = apps.map { it.foregroundMs + it.foregroundServiceMs * SERVICE_WEIGHT }
        val total = weights.sum()
        if (dropPercent == 0 || total <= 0.0) return List(apps.size) { 0f }
        return weights.map { (dropPercent * it / total).toFloat() }
    }
}
