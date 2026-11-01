package com.aditya.powermonitor.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * How one app was used between two logging runs, plus its estimated share of the battery
 * drop in that interval. Intervals don't overlap, so summing rows gives totals.
 */
@Entity(
    tableName = "app_usage_records",
    indices = [Index("intervalStart"), Index("packageName")],
)
data class AppUsageRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val intervalStart: Long,
    val intervalEnd: Long,
    val packageName: String,
    val appLabel: String,
    val foregroundMs: Long,
    val foregroundServiceMs: Long,
    val launchCount: Int,
    val lastUsed: Long,
    /** Percentage points of battery attributed to this app (an estimate; see repository). */
    val estimatedDrainPercent: Float,
)
