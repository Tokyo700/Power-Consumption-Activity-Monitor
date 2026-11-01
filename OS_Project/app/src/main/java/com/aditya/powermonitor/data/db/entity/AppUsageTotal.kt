package com.aditya.powermonitor.data.db.entity

/**
 * Sum of an app's AppUsageRecord rows over a time range. Not a table: Room fills it from the
 * columns a GROUP BY query returns, matching them by name.
 */
data class AppUsageTotal(
    val packageName: String,
    val appLabel: String,
    val foregroundMs: Long,
    val foregroundServiceMs: Long,
    val launchCount: Int,
    val estimatedDrainPercent: Float,
)
