package com.aditya.powermonitor.ui.history

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aditya.powermonitor.data.db.entity.AppUsageRecord
import com.aditya.powermonitor.data.db.entity.AppUsageTotal
import com.aditya.powermonitor.data.db.entity.BatterySnapshot
import com.aditya.powermonitor.data.export.CsvExport
import com.aditya.powermonitor.util.TimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // rememberSaveable survives rotation; the dialog's open/closed flag is pure UI state.
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    val filter = state.filter
    val context = LocalContext.current
    // A coroutine scope tied to this screen, for work started by a button tap.
    val scope = rememberCoroutineScope()

    val onExport: () -> Unit = {
        isExporting = true
        scope.launch {
            try {
                val export = viewModel.exportCsv()
                if (export.rowCount == 0) {
                    Toast.makeText(context, "Nothing to export in this range", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    shareCsv(context, export)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isExporting = false
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RangePreset.entries.forEach { preset ->
                    FilterChip(
                        selected = filter.preset == preset,
                        onClick = {
                            if (preset == RangePreset.CUSTOM) showDatePicker = true
                            else viewModel.selectPreset(preset)
                        },
                        label = { Text(preset.label) },
                    )
                }
            }
        }
        item {
            Text(
                rangeLabel(state.from, state.to),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppFilter(
                    selected = filter.packageName,
                    selectedLabel = state.records.firstOrNull()?.appLabel
                        ?: state.totals.firstOrNull { it.packageName == filter.packageName }?.appLabel,
                    choices = state.totals,
                    onSelect = viewModel::selectApp,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onExport, enabled = !isExporting) {
                    Text(if (isExporting) "Exporting…" else "Share CSV")
                }
            }
        }
        item { ModeToggle(filter.mode, viewModel::selectMode) }

        when {
            filter.mode == HistoryMode.BATTERY -> batteryLog(state.snapshots)
            filter.packageName != null -> appRecords(
                state.records,
                state.totals.firstOrNull { it.packageName == filter.packageName },
            )
            else -> appTotals(state.totals, onAppClick = viewModel::selectApp)
        }
    }

    if (showDatePicker) {
        RangePickerDialog(
            initialStart = filter.customStart,
            initialEnd = filter.customEnd,
            onDismiss = { showDatePicker = false },
            onConfirm = { start, end ->
                viewModel.selectCustomRange(start, end)
                showDatePicker = false
            },
        )
    }
}

/**
 * Opens Android's share sheet for the file. FLAG_GRANT_READ_URI_PERMISSION gives the app the
 * user picks temporary read access to this one file; ClipData carries that grant reliably
 * on newer Android versions.
 */
private fun shareCsv(context: Context, export: CsvExport) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, export.uri)
        putExtra(Intent.EXTRA_SUBJECT, export.fileName)
        clipData = ClipData.newRawUri(export.fileName, export.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share ${export.fileName}"))
}

/** A button that opens a menu of every app seen in the current range. */
@Composable
private fun AppFilter(
    selected: String?,
    selectedLabel: String?,
    choices: List<AppUsageTotal>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                "App: " + (if (selected == null) "All apps" else selectedLabel ?: selected),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("All apps") },
                onClick = { onSelect(null); expanded = false },
            )
            choices.sortedBy { it.appLabel.lowercase() }.forEach { app ->
                DropdownMenuItem(
                    text = { Text(app.appLabel) },
                    onClick = { onSelect(app.packageName); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeToggle(mode: HistoryMode, onSelect: (HistoryMode) -> Unit) {
    val options = listOf(HistoryMode.APPS to "App usage", HistoryMode.BATTERY to "Battery log")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = mode == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label) }
        }
    }
}

/** "All apps": one row per app, summed over the range. Tapping one filters to it. */
private fun LazyListScope.appTotals(totals: List<AppUsageTotal>, onAppClick: (String) -> Unit) {
    if (totals.isEmpty()) {
        item { EmptyText("No app usage logged in this range.") }
        return
    }
    item {
        Text(
            "${totals.size} apps · " +
                TimeFormat.duration(totals.sumOf { it.foregroundMs }) + " on screen · " +
                "%.1f%% battery attributed".format(totals.sumOf { it.estimatedDrainPercent.toDouble() }),
            style = MaterialTheme.typography.titleSmall,
        )
    }
    items(totals, key = { it.packageName }) { app ->
        Card(onClick = { onAppClick(app.packageName) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row {
                    Text(
                        app.appLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text("%.2f%%".format(app.estimatedDrainPercent))
                }
                Text(
                    usageSummary(app.foregroundMs, app.foregroundServiceMs, app.launchCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One app: its logged intervals, grouped under a header per day. */
private fun LazyListScope.appRecords(records: List<AppUsageRecord>, total: AppUsageTotal?) {
    if (records.isEmpty()) {
        item { EmptyText("This app has no logged usage in this range.") }
        return
    }
    total?.let {
        item {
            Text(
                "Total: " + usageSummary(it.foregroundMs, it.foregroundServiceMs, it.launchCount) +
                    " · %.2f%% battery".format(it.estimatedDrainPercent),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
    records.groupBy { TimeFormat.startOfDay(it.intervalEnd) }.forEach { (day, dayRecords) ->
        item(key = "day-$day") { DayHeader(day) }
        items(dayRecords, key = { it.id }) { r ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            TimeFormat.clockTime(r.intervalStart) + " – " +
                                TimeFormat.clockTime(r.intervalEnd),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            usageSummary(r.foregroundMs, r.foregroundServiceMs, r.launchCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("%.2f%%".format(r.estimatedDrainPercent))
                }
            }
        }
    }
}

/** Every battery snapshot in the range, grouped by day. */
private fun LazyListScope.batteryLog(snapshots: List<BatterySnapshot>) {
    if (snapshots.isEmpty()) {
        item { EmptyText("No battery snapshots in this range.") }
        return
    }
    snapshots.groupBy { TimeFormat.startOfDay(it.timestamp) }.forEach { (day, daySnapshots) ->
        item(key = "bday-$day") { DayHeader(day) }
        items(daySnapshots, key = { it.id }) { s ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp)) {
                    Text(
                        TimeFormat.clockTime(s.timestamp),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${s.levelPercent}%" +
                            (if (s.isCharging) " · charging" else "") +
                            " · %.1f °C".format(s.temperatureC),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePickerDialog(
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    // The Material date picker works in UTC midnights, so convert to and from LocalDate.
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart?.toUtcMillis(),
        initialSelectedEndDateMillis = initialEnd?.toUtcMillis(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = pickerState.selectedStartDateMillis != null,
                onClick = {
                    val start = pickerState.selectedStartDateMillis!!.toUtcDate()
                    val end = pickerState.selectedEndDateMillis?.toUtcDate() ?: start
                    onConfirm(start, end)
                },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DateRangePicker(state = pickerState, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DayHeader(dayStart: Long) {
    Text(
        TimeFormat.dayLabel(dayStart),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 16.dp),
    )
}

private fun usageSummary(foregroundMs: Long, serviceMs: Long, launches: Int) = buildString {
    append(TimeFormat.duration(foregroundMs)).append(" on screen")
    if (serviceMs > 0) append(" · ").append(TimeFormat.duration(serviceMs)).append(" background")
    if (launches > 0) append(" · opened ").append(launches).append("×")
}

private fun rangeLabel(from: Long, to: Long): String = when {
    to == Long.MAX_VALUE -> "Showing everything logged"
    to - from <= 24 * 60 * 60_000L + 60 * 60_000L -> TimeFormat.dayLabel(from) // one day (± DST)
    else -> TimeFormat.shortDate(from) + " – " + TimeFormat.shortDate(to - 1)
}

private fun LocalDate.toUtcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
