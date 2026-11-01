package com.aditya.powermonitor.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.aditya.powermonitor.data.db.entity.AppUsageRecord
import com.aditya.powermonitor.data.db.entity.BatterySnapshot
import com.aditya.powermonitor.data.repository.MonitorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A written CSV file, ready to hand to the share sheet. */
data class CsvExport(val uri: Uri, val fileName: String, val rowCount: Int)

/**
 * Writes history rows to a CSV file in the app's cache and returns a content:// Uri for it.
 * Other apps can't read our private files directly; FileProvider gives them temporary,
 * read-only access to just this file when we share it.
 */
class CsvExporter(
    private val context: Context,
    private val repository: MonitorRepository,
) {
    private val dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val zone = ZoneId.systemDefault()

    /** App usage rows in [from, to), for one app or (packageName = null) all of them. */
    suspend fun exportUsage(packageName: String?, from: Long, to: Long, label: String): CsvExport =
        withContext(Dispatchers.IO) {
            val rows = repository.getRecords(packageName, from, to)
            write("usage_$label.csv", USAGE_HEADER, rows.map(::usageLine))
        }

    /** Battery snapshots in [from, to). */
    suspend fun exportBattery(from: Long, to: Long, label: String): CsvExport =
        withContext(Dispatchers.IO) {
            val rows = repository.getSnapshotsBetween(from, to)
            write("battery_$label.csv", BATTERY_HEADER, rows.map(::batteryLine))
        }

    private fun write(fileName: String, header: List<String>, lines: List<List<Any?>>): CsvExport {
        // Matches the <cache-path> in res/xml/file_paths.xml. Old exports are removed first so
        // the folder doesn't fill up.
        val dir = File(context.cacheDir, "exports").apply {
            deleteRecursively()
            mkdirs()
        }
        val file = File(dir, fileName)
        file.bufferedWriter().use { out ->
            out.appendLine(header.joinToString(","))
            lines.forEach { out.appendLine(Csv.line(it)) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return CsvExport(uri, fileName, lines.size)
    }

    private fun usageLine(r: AppUsageRecord): List<Any?> = listOf(
        time(r.intervalStart),
        time(r.intervalEnd),
        r.packageName,
        r.appLabel,
        r.foregroundMs / 1000,
        r.foregroundServiceMs / 1000,
        r.launchCount,
        if (r.lastUsed > 0) time(r.lastUsed) else "",
        // Locale.US forces a "." decimal point, which spreadsheets expect in CSV.
        "%.4f".format(Locale.US, r.estimatedDrainPercent),
    )

    private fun batteryLine(s: BatterySnapshot): List<Any?> = listOf(
        time(s.timestamp),
        s.levelPercent,
        s.isCharging,
        s.plugType,
        s.health,
        s.temperatureC,
        s.voltageMv,
        s.currentMicroA?.let { it / 1000 },
        s.chargeCounterMicroAh?.let { it / 1000 },
    )

    private fun time(epochMs: Long): String =
        dateTime.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    private companion object {
        val USAGE_HEADER = listOf(
            "interval_start", "interval_end", "package", "app", "foreground_s",
            "foreground_service_s", "launches", "last_used", "est_battery_percent",
        )
        val BATTERY_HEADER = listOf(
            "time", "level_percent", "charging", "plug", "health", "temperature_c",
            "voltage_mv", "current_ma", "charge_mah",
        )
    }
}
