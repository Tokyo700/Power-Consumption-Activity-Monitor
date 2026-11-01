package com.aditya.powermonitor.ui.debug

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.WorkInfo
import com.aditya.powermonitor.PowerMonitorApp
import com.aditya.powermonitor.data.collector.AppUsage
import com.aditya.powermonitor.data.collector.BatteryCollector
import com.aditya.powermonitor.data.collector.BatteryReading
import com.aditya.powermonitor.data.collector.CpuSample
import com.aditya.powermonitor.data.collector.SelfCpuSampler
import com.aditya.powermonitor.data.collector.UsageCollector
import com.aditya.powermonitor.data.db.entity.AppUsageRecord
import com.aditya.powermonitor.data.db.entity.BatterySnapshot
import com.aditya.powermonitor.data.repository.MonitorRepository
import com.aditya.powermonitor.util.TimeFormat
import com.aditya.powermonitor.worker.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class UsageWindow(val label: String) {
    LAST_15_MIN("15 min"),
    LAST_HOUR("1 hour"),
    TODAY("Today");

    fun startMs(now: Long): Long = when (this) {
        LAST_15_MIN -> now - 15 * 60_000L
        LAST_HOUR -> now - 60 * 60_000L
        TODAY -> TimeFormat.startOfDay(now)
    }
}

data class DebugUiState(
    val window: UsageWindow = UsageWindow.LAST_HOUR,
    val isLoading: Boolean = true,
    val battery: BatteryReading? = null,
    val cpu: CpuSample? = null,
    val apps: List<AppUsage> = emptyList(),
    val isLogging: Boolean = false,
    val logMessage: String? = null,
)

/** What's stored in the database, kept live by Room's Flows. */
data class SavedState(
    val snapshotCount: Int = 0,
    val recordCount: Int = 0,
    val latestSnapshots: List<BatterySnapshot> = emptyList(),
    val latestRecords: List<AppUsageRecord> = emptyList(),
)

/**
 * Temporary screen state: live collector readings (Step 3) plus a view of what's been saved
 * to the database (Step 4). The real Dashboard will replace it.
 */
class DebugViewModel(
    private val battery: BatteryCollector,
    private val usage: UsageCollector,
    private val cpu: SelfCpuSampler,
    private val repository: MonitorRepository,
    private val app: Application,
) : ViewModel() {

    private val _state = MutableStateFlow(DebugUiState())
    val state: StateFlow<DebugUiState> = _state.asStateFlow()

    /**
     * combine() merges four database Flows into one SavedState. stateIn() turns it into a
     * StateFlow that stays subscribed while the screen is visible (plus 5 s, so a screen
     * rotation doesn't restart the queries).
     */
    val saved: StateFlow<SavedState> = combine(
        repository.observeSnapshotCount(),
        repository.observeUsageCount(),
        repository.observeLatestSnapshots(limit = 5),
        repository.observeRecentUsage(limit = 10),
    ) { snapshots, records, latestSnapshots, latestRecords ->
        SavedState(snapshots, records, latestSnapshots, latestRecords)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavedState())

    /** Status of the 15-minute background job, straight from WorkManager. */
    val periodicWork: StateFlow<WorkInfo?> = WorkScheduler.observePeriodic(app)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        refresh()
    }

    /** Same work as Log now, but through WorkManager, to prove the background path works. */
    fun runWorkerNow() {
        WorkScheduler.runOnce(app)
        _state.update { it.copy(logMessage = "Worker queued; rows appear when it finishes.") }
    }

    fun logNow() {
        _state.update { it.copy(isLogging = true) }
        viewModelScope.launch {
            val message = try {
                val r = repository.logSnapshot()
                "Saved ${r.appsLogged} apps over ${(r.intervalEnd - r.intervalStart) / 60_000} min, " +
                    "battery drop ${r.batteryDropPercent}%"
            } catch (e: Exception) {
                "Logging failed: ${e.message}"
            }
            _state.update { it.copy(isLogging = false, logMessage = message) }
        }
    }

    fun selectWindow(window: UsageWindow) {
        _state.update { it.copy(window = window) }
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true) }
        // viewModelScope cancels this coroutine automatically if the screen goes away.
        viewModelScope.launch {
            val window = _state.value.window
            // Replaying events and reading /proc is disk work: keep it off the main (UI)
            // thread so scrolling stays smooth.
            val (b, c, a) = withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                Triple(battery.read(), cpu.sample(), usage.collect(window.startMs(now), now))
            }
            _state.update { it.copy(isLoading = false, battery = b, cpu = c, apps = a) }
        }
    }

    companion object {
        /** Tells viewModel() how to build this class, pulling its parts from AppContainer. */
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PowerMonitorApp
                val container = app.container
                DebugViewModel(
                    container.batteryCollector,
                    container.usageCollector,
                    container.selfCpuSampler,
                    container.repository,
                    app,
                )
            }
        }
    }
}
