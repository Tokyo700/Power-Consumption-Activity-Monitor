package com.aditya.powermonitor.ui.history

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HistoryFilterTest {

    private val today = LocalDate.of(2026, 11, 1)

    private fun midnight(d: LocalDate) =
        d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun range(f: HistoryFilter) = f.range(today)

    @Test
    fun `today runs from this midnight to the next`() {
        assertEquals(
            midnight(today) to midnight(today.plusDays(1)),
            range(HistoryFilter(preset = RangePreset.TODAY)),
        )
    }

    @Test
    fun `yesterday is the whole previous day`() {
        assertEquals(
            midnight(today.minusDays(1)) to midnight(today),
            range(HistoryFilter(preset = RangePreset.YESTERDAY)),
        )
    }

    @Test
    fun `seven days includes today and the six days before it`() {
        assertEquals(
            midnight(today.minusDays(6)) to midnight(today.plusDays(1)),
            range(HistoryFilter(preset = RangePreset.LAST_7_DAYS)),
        )
    }

    @Test
    fun `custom range includes both end dates`() {
        val f = HistoryFilter(
            preset = RangePreset.CUSTOM,
            customStart = LocalDate.of(2026, 10, 20),
            customEnd = LocalDate.of(2026, 10, 22),
        )
        assertEquals(
            midnight(LocalDate.of(2026, 10, 20)) to midnight(LocalDate.of(2026, 10, 23)),
            range(f),
        )
    }

    @Test
    fun `custom range with only a start date is that single day`() {
        val day = LocalDate.of(2026, 10, 20)
        val f = HistoryFilter(preset = RangePreset.CUSTOM, customStart = day)
        assertEquals(midnight(day) to midnight(day.plusDays(1)), range(f))
    }

    @Test
    fun `all time is unbounded`() {
        assertEquals(0L to Long.MAX_VALUE, range(HistoryFilter(preset = RangePreset.ALL)))
    }
}
