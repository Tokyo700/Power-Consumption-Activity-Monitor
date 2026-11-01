package com.aditya.powermonitor.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkInfo
import com.aditya.powermonitor.data.collector.AppUsage
import com.aditya.powermonitor.data.collector.BatteryReading
import com.aditya.powermonitor.data.collector.CpuSample
import com.aditya.powermonitor.data.db.entity.AppUsageRecord
import com.aditya.powermonitor.data.db.entity.BatterySnapshot
import com.aditya.powermonitor.util.TimeFormat

@Composable
fun DebugScreen(
    modifier: Modifier = Modifier,
    viewModel: DebugViewModel = viewModel(factory = DebugViewModel.Factory),
) {
    // Collects the StateFlow only while the screen is visible, and redraws on every change.
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val periodicWork by viewModel.periodicWork.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Live readings",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                if (state.isLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                TextButton(onClick = viewModel::refresh) { Text("Refresh") }
            }
        }
        item {
            SavedCard(
                saved = saved,
                isLogging = state.isLogging,
                logMessage = state.logMessage,
                onLogNow = viewModel::logNow,
                onRunWorker = viewModel::runWorkerNow,
                onRunCleanup = viewModel::runCleanupNow,
            )
        }
        item { WorkerCard(periodicWork) }
        state.battery?.let { item { BatteryCard(it) } }
        state.cpu?.let { item { CpuCard(it) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UsageWindow.entries.forEach { w ->
                    FilterChip(
                        selected = w == state.window,
                        onClick = { viewModel.selectWindow(w) },
                        label = { Text(w.label) },
                    )
                }
            }
        }
        item {
            Text(
                "${state.apps.size} apps active in this window",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        items(state.apps, key = { it.packageName }) { AppRow(it) }
    }
}

@Composable
private fun SavedCard(
    saved: SavedState,
    isLogging: Boolean,
    logMessage: String?,
    onLogNow: () -> Unit,
    onRunWorker: () -> Unit,
    onRunCleanup: () -> Unit,
) {
    InfoCard("Saved in database") {
        Field("Battery snapshots", saved.snapshotCount.toString())
        Field("App usage rows", saved.recordCount.toString())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onLogNow, enabled = !isLogging) { Text("Log now") }
            OutlinedButton(onClick = onRunWorker, modifier = Modifier.padding(start = 8.dp)) {
                Text("Run worker")
            }
            if (isLogging) {
                CircularProgressIndicator(
                    Modifier.padding(start = 12.dp).size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        OutlinedButton(onClick = onRunCleanup) { Text("Run cleanup (delete > 30 days)") }
        logMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        if (saved.latestSnapshots.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("Latest snapshots", style = MaterialTheme.typography.labelLarge)
            saved.latestSnapshots.forEach { SnapshotLine(it) }
        }
        if (saved.latestRecords.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("Latest app rows", style = MaterialTheme.typography.labelLarge)
            saved.latestRecords.forEach { RecordLine(it) }
        }
    }
}

@Composable
private fun WorkerCard(info: WorkInfo?) {
    InfoCard("Background logging") {
        if (info == null) {
            Field("Status", "not scheduled")
            return@InfoCard
        }
        // ENQUEUED = waiting for its next slot, RUNNING = logging right now.
        Field("Status", info.state.name)
        val next = info.nextScheduleTimeMillis
        if (info.state == WorkInfo.State.ENQUEUED && next != Long.MAX_VALUE) {
            Field("Next run (about)", TimeFormat.clockTime(next))
        }
        Field("Every", "15 min")
    }
}

@Composable
private fun SnapshotLine(s: BatterySnapshot) {
    Field(
        TimeFormat.clockTime(s.timestamp),
        "${s.levelPercent}%" + (if (s.isCharging) " ⚡" else "") + " · %.1f °C".format(s.temperatureC),
    )
}

@Composable
private fun RecordLine(r: AppUsageRecord) {
    Field(
        r.appLabel,
        TimeFormat.duration(r.foregroundMs) + " · %.2f%%".format(r.estimatedDrainPercent),
    )
}

@Composable
private fun BatteryCard(b: BatteryReading) {
    InfoCard("Battery") {
        Field("Level", "${b.levelPercent}%")
        Field("Status", if (b.isCharging) "Charging (${b.plugType})" else b.plugType)
        Field("Health", b.health)
        Field("Temperature", "%.1f °C".format(b.temperatureC))
        Field("Voltage", "${b.voltageMv} mV")
        Field("Current now", b.currentMicroA?.let { "${it / 1000} mA" } ?: "not reported")
        Field(
            "Charge counter",
            b.chargeCounterMicroAh?.let { "${it / 1000} mAh" } ?: "not reported",
        )
    }
}

@Composable
private fun CpuCard(c: CpuSample) {
    InfoCard("This app's CPU") {
        Field("CPU time used", TimeFormat.duration(c.processCpuMs))
        Field("Process uptime", TimeFormat.duration(c.processUptimeMs))
        Field("Average load", "%.2f%% of %d cores".format(c.averageCpuPercent, c.cores))
        Field(
            "/proc/self/stat",
            if (c.userMs != null) "user ${c.userMs} ms, kernel ${c.kernelMs} ms" else "unreadable",
        )
        Field("/proc/stat readable", yesNo(c.canReadSystemProcStat))
        Field("/proc/1/stat readable", yesNo(c.canReadOtherProcess))
    }
}

@Composable
private fun AppRow(app: AppUsage) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                app.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Field("Foreground", TimeFormat.duration(app.foregroundMs))
            if (app.foregroundServiceMs > 0) {
                Field("Foreground service", TimeFormat.duration(app.foregroundServiceMs))
            }
            Field("Opened", "${app.launchCount}×")
            if (app.lastUsed > 0) Field("Last used", TimeFormat.clockTime(app.lastUsed))
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Field(name: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun yesNo(b: Boolean) = if (b) "yes" else "no (blocked by OS)"
