package com.aditya.powermonitor.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aditya.powermonitor.PowerMonitorApp
import com.aditya.powermonitor.data.db.entity.AppUsageRecord
import com.aditya.powermonitor.data.db.entity.AppUsageTotal
import com.aditya.powermonitor.data.db.entity.BatterySnapshot
import com.aditya.powermonitor.data.repository.MonitorRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId

/** Quick date ranges; CUSTOM uses the dates picked in the date-range dialog. */
enum class RangePreset(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    LAST_7_DAYS("7 days"),
    LAST_30_DAYS("30 days"),
    ALL("All time"),
    CUSTOM("Custom…"),
}

enum class HistoryMode { APPS, BATTERY }

/** Everything the user can filter by. Dates are inclusive local calendar days. */
data class HistoryFilter(
    val preset: RangePreset = RangePreset.LAST_7_DAYS,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
    val packageName: String? = null,
    val mode: HistoryMode = HistoryMode.APPS,
) {
    /** The filter as a [from, to) range of epoch milliseconds. */
    fun range(today: LocalDate = LocalDate.now()): Pair<Long, Long> {
        val (first, last) = when (preset) {
            RangePreset.TODAY -> today to today
            RangePreset.YESTERDAY -> today.minusDays(1) to today.minusDays(1)
            RangePreset.LAST_7_DAYS -> today.minusDays(6) to today
            RangePreset.LAST_30_DAYS -> today.minusDays(29) to today
            RangePreset.ALL -> return 0L to Long.MAX_VALUE
            RangePreset.CUSTOM -> (customStart ?: today) to (customEnd ?: customStart ?: today)
        }
        return first.startMs() to last.plusDays(1).startMs()
    }

    private fun LocalDate.startMs() =
        atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

data class HistoryUiState(
    val filter: HistoryFilter = HistoryFilter(),
    val from: Long = 0,
    val to: Long = 0,
    /** Per-app totals over the range; also the choices in the app filter menu. */
    val totals: List<AppUsageTotal> = emptyList(),
    /** Individual rows for the selected app, newest first. Empty when no app is selected. */
    val records: List<AppUsageRecord> = emptyList(),
    /** Battery snapshots in the range, newest first. */
    val snapshots: List<BatterySnapshot> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val repository: MonitorRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Navigation arguments arrive in the SavedStateHandle; the Dashboard passes "pkg" when
    // you tap one of its apps.
    private val filter = MutableStateFlow(
        HistoryFilter(packageName = savedStateHandle.get<String>(ARG_PACKAGE)),
    )

    val state: StateFlow<HistoryUiState> = filter.flatMapLatest { f ->
        val (from, to) = f.range()
        combine(
            repository.observeAppTotals(from, to),
            f.packageName
                ?.let { repository.observeRecordsForApp(it, from, to) }
                ?: flowOf(emptyList()),
            if (f.mode == HistoryMode.BATTERY) {
                repository.observeSnapshotsBetween(from, to)
            } else {
                flowOf(emptyList())
            },
        ) { totals, records, snapshots ->
            HistoryUiState(f, from, to, totals, records, snapshots.asReversed())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun selectPreset(preset: RangePreset) = filter.update { it.copy(preset = preset) }

    fun selectCustomRange(start: LocalDate, end: LocalDate) = filter.update {
        it.copy(preset = RangePreset.CUSTOM, customStart = start, customEnd = end)
    }

    fun selectApp(packageName: String?) = filter.update { it.copy(packageName = packageName) }

    fun selectMode(mode: HistoryMode) = filter.update { it.copy(mode = mode) }

    companion object {
        const val ARG_PACKAGE = "pkg"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PowerMonitorApp
                HistoryViewModel(app.container.repository, createSavedStateHandle())
            }
        }
    }
}
