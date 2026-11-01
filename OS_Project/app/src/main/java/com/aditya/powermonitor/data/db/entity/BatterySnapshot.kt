package com.aditya.powermonitor.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One row per logging run: the battery's state at that moment. */
@Entity(tableName = "battery_snapshots", indices = [Index("timestamp")])
data class BatterySnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val levelPercent: Int,
    val isCharging: Boolean,
    val plugType: String,
    val health: String,
    val temperatureC: Float,
    val voltageMv: Int,
    val currentMicroA: Int?,
    val chargeCounterMicroAh: Int?,
)
