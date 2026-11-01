package com.aditya.powermonitor.data.collector

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/** One point-in-time reading of the battery's state. */
data class BatteryReading(
    val timestamp: Long,
    val levelPercent: Int,
    val isCharging: Boolean,
    val plugType: String,
    val health: String,
    val temperatureC: Float,
    val voltageMv: Int,
    /** Instantaneous current in microamps; null if the device doesn't report it. */
    val currentMicroA: Int?,
    /** Remaining charge in microamp-hours; null if the device doesn't report it. */
    val chargeCounterMicroAh: Int?,
)

class BatteryCollector(private val context: Context) {

    fun read(): BatteryReading {
        // ACTION_BATTERY_CHANGED is a "sticky" broadcast: the system keeps the last one, and
        // registering with a null receiver just hands it back without subscribing to updates.
        val intent: Intent? = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        // Temperature is reported in tenths of a degree Celsius.
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0

        return BatteryReading(
            timestamp = System.currentTimeMillis(),
            levelPercent = if (level >= 0 && scale > 0) level * 100 / scale else -1,
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
            plugType = plugTypeName(plugged),
            health = healthName(health),
            temperatureC = tempTenths / 10f,
            voltageMv = voltage,
            currentMicroA = manager.propertyOrNull(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            chargeCounterMicroAh =
                manager.propertyOrNull(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
        )
    }

    /** Unsupported properties come back as Int.MIN_VALUE (or 0 on some phones). */
    private fun BatteryManager.propertyOrNull(id: Int): Int? =
        getIntProperty(id).takeIf { it != Int.MIN_VALUE && it != 0 }

    private fun plugTypeName(plugged: Int) = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> "AC"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
        0 -> "Unplugged"
        else -> "Other"
    }

    private fun healthName(health: Int) = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
        else -> "Unknown"
    }
}
