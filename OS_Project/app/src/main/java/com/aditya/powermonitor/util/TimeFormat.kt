package com.aditya.powermonitor.util

import java.text.DateFormat
import java.util.Calendar
import java.util.Date

object TimeFormat {

    /** 3725000 -> "1h 2m 5s"; drops leading zero units. */
    fun duration(ms: Long): String {
        val totalSeconds = ms / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return when {
            h > 0 -> "${h}h ${m}m ${s}s"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    /** Local midnight at the start of the day containing epochMs. */
    fun startOfDay(epochMs: Long): Long = Calendar.getInstance().apply {
        timeInMillis = epochMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun clockTime(epochMs: Long): String =
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMs))

    /** "Sat, 1 Nov 2026" style label (format follows the phone's locale). */
    fun dayLabel(epochMs: Long): String =
        DateFormat.getDateInstance(DateFormat.FULL).format(Date(epochMs))

    fun shortDate(epochMs: Long): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMs))
}
