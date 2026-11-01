package com.aditya.powermonitor.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aditya.powermonitor.data.collector.BatteryReading
import com.aditya.powermonitor.data.db.entity.AppUsageTotal
import com.aditya.powermonitor.data.db.entity.BatterySnapshot
import com.aditya.powermonitor.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenHistory: (packageName: String?) -> Unit,
    onOpenDebug: () -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Power Monitor") },
                actions = {
                    TextButton(onClick = { onOpenHistory(null) }) { Text("History") }
                    TextButton(onClick = onOpenDebug) { Text("Debug") }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { BatteryHeader(state.liveBattery, state.snapshots.lastOrNull()) }
            item { BatteryChartCard(state.snapshots, state.dayStart) }
            item { TodaySummary(state) }
            item {
                Text("Top apps today", style = MaterialTheme.typography.titleMedium)
            }
            if (state.topApps.isEmpty()) {
                item {
                    Text(
                        "No app usage logged yet today. A snapshot is saved about every " +
                            "15 minutes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // Bars are scaled to the top app's share (or its time, if nothing drained).
                val maxDrain = state.topApps.maxOf { it.estimatedDrainPercent }
                val maxFg = state.topApps.maxOf { it.foregroundMs }.coerceAtLeast(1)
                items(state.topApps, key = { it.packageName }) { app ->
                    val fraction =
                        if (maxDrain > 0f) app.estimatedDrainPercent / maxDrain
                        else app.foregroundMs.toFloat() / maxFg
                    TopAppRow(app, fraction, onClick = { onOpenHistory(app.packageName) })
                }
            }
        }
    }
}

@Composable
private fun BatteryHeader(live: BatteryReading?, lastLogged: BatterySnapshot?) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                live?.let { "${it.levelPercent}%" } ?: "—",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Column(Modifier.padding(start = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (live != null) {
                    Text(
                        if (live.isCharging) "Charging · ${live.plugType}" else "On battery",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "%.1f °C · %d mV".format(live.temperatureC, live.voltageMv),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    lastLogged?.let { "Last logged ${TimeFormat.clockTime(it.timestamp)}" }
                        ?: "Nothing logged today",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BatteryChartCard(snapshots: List<BatterySnapshot>, dayStart: Long) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Battery level today", style = MaterialTheme.typography.titleMedium)
            if (snapshots.size < 2) {
                Text(
                    "The chart appears after two snapshots.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                BatteryChart(snapshots, dayStart)
                Box(Modifier.fillMaxWidth()) {
                    ChartLabel("00:00", Modifier.align(Alignment.CenterStart))
                    ChartLabel("12:00", Modifier.align(Alignment.Center))
                    ChartLabel("24:00", Modifier.align(Alignment.CenterEnd))
                }
            }
        }
    }
}

/**
 * A minimal line chart drawn with Canvas: x is time of day, y is battery level (0–100%).
 * Gridlines mark 0, 50 and 100%.
 */
@Composable
private fun BatteryChart(snapshots: List<BatterySnapshot>, dayStart: Long) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val dayMs = 24 * 60 * 60_000f
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        listOf(0f, 0.5f, 1f).forEach { f ->
            drawLine(gridColor, Offset(0f, size.height * f), Offset(size.width, size.height * f))
        }
        val path = Path()
        snapshots.forEachIndexed { i, s ->
            val x = ((s.timestamp - dayStart) / dayMs).coerceIn(0f, 1f) * size.width
            val y = (1f - s.levelPercent / 100f) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun ChartLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TodaySummary(state: DashboardUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Today", style = MaterialTheme.typography.titleMedium)
            SummaryRow("Screen time (all apps)", TimeFormat.duration(state.totalForegroundMs))
            SummaryRow("Battery used (unplugged)", "${state.batteryUsedPercent}%")
            SummaryRow("  attributed to apps", "%.1f%%".format(state.attributedPercent))
            SummaryRow(
                "  idle / screen off",
                "%.1f%%".format(
                    (state.batteryUsedPercent - state.attributedPercent).coerceAtLeast(0f),
                ),
            )
        }
    }
}

@Composable
private fun SummaryRow(name: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TopAppRow(app: AppUsageTotal, fraction: Float, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.appLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "%.1f%%".format(app.estimatedDrainPercent),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                drawStopIndicator = {},
            )
            Text(
                buildString {
                    append(TimeFormat.duration(app.foregroundMs)).append(" on screen")
                    if (app.foregroundServiceMs > 0) {
                        append(" · ").append(TimeFormat.duration(app.foregroundServiceMs))
                            .append(" in background")
                    }
                    append(" · opened ").append(app.launchCount).append("×")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
