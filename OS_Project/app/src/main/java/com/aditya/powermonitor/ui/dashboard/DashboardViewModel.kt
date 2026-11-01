package com.aditya.powermonitor.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aditya.powermonitor.PowerMonitorApp
import com.aditya.powermonitor.data.collector.BatteryReading
import com.aditya.powermonitor.data.db.entity.AppUsageTotal
import com.aditya.powermonitor.data.db.entity.BatterySnapshot
import com.aditya.powermonitor.data.repository.DrainEstimator
import com.aditya.powermonitor.data.repository.MonitorRepository
import com.aditya.powermonitor.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DashboardUiState(
    val dayStart: Long = 0,
    val liveBattery: BatteryReading? = null,
    /** Today's logged snapshots, oldest first, for the chart. */
    val snapshots: List<BatterySnapshot> = emptyList(),
    /** Percentage points lost while unplugged today, from consecutive snapshots. */
    val batteryUsedPercent: Int = 0,
    /** The part of batteryUsedPercent shared out to apps; the rest was screen-off/idle. */
    val attributedPercent: Float = 0f,
    val totalForegroundMs: Long = 0,
    val topApps: List<AppUsageTotal> = emptyList(),
)

class DashboardViewModel(private val repository: MonitorRepository) : ViewModel() {

    private val dayStart = MutableStateFlow(TimeFormat.startOfDay(System.currentTimeMillis()))
    private val liveBattery = MutableStateFlow<BatteryReading?>(null)

    /**
     * flatMapLatest swaps to new database queries whenever dayStart changes (e.g. the app is
     * reopened after midnight), cancelling the old ones.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<DashboardUiState> = combine(
        dayStart,
        liveBattery,
        dayStart.flatMapLatest { repository.observeSnapshotsBetween(it, it + DAY_MS) },
        dayStart.flatMapLatest { repository.observeAppTotals(it, it + DAY_MS) },
    ) { day, live, snapshots, totals ->
        DashboardUiState(
            dayStart = day,
            liveBattery = live,
            snapshots = snapshots,
            batteryUsedPercent = batteryUsed(snapshots),
            attributedPercent = totals.sumOf { it.estimatedDrainPercent.toDouble() }.toFloat(),
            totalForegroundMs = totals.sumOf { it.foregroundMs },
            topApps = totals.take(TOP_APPS),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    /** Called when the screen comes to the front: re-read the battery, roll over the day. */
    fun refresh() {
        dayStart.value = TimeFormat.startOfDay(System.currentTimeMillis())
        viewModelScope.launch {
            liveBattery.value = withContext(Dispatchers.IO) { repository.readBatteryNow() }
        }
    }

    /** Same rule as logging: only count drops between two unplugged snapshots. */
    private fun batteryUsed(snapshots: List<BatterySnapshot>): Int =
        snapshots.zipWithNext().sumOf { (a, b) ->
            DrainEstimator.batteryDrop(a.levelPercent, a.isCharging, b.levelPercent, b.isCharging)
        }

    companion object {
        private const val DAY_MS = 24 * 60 * 60_000L
        private const val TOP_APPS = 10

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PowerMonitorApp
                DashboardViewModel(app.container.repository)
            }
        }
    }
}
