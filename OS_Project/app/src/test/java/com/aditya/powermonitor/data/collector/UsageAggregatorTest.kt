package com.aditya.powermonitor.data.collector

import com.aditya.powermonitor.data.collector.UsageAggregator.ACTIVITY_PAUSED
import com.aditya.powermonitor.data.collector.UsageAggregator.ACTIVITY_RESUMED
import com.aditya.powermonitor.data.collector.UsageAggregator.FOREGROUND_SERVICE_START
import com.aditya.powermonitor.data.collector.UsageAggregator.FOREGROUND_SERVICE_STOP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageAggregatorTest {

    private val start = 1_000_000L
    private val end = start + 15 * 60_000L

    private fun ev(pkg: String, cls: String, type: Int, t: Long) =
        RawUsageEvent(pkg, cls, type, start + t)

    private fun run(vararg events: RawUsageEvent) =
        UsageAggregator.aggregate(events.asSequence(), start, end)

    @Test
    fun `single visit counts its duration and one launch`() {
        val result = run(
            ev("app.a", "Main", ACTIVITY_RESUMED, 10_000),
            ev("app.a", "Main", ACTIVITY_PAUSED, 70_000),
        )
        val a = result.single()
        assertEquals(60_000L, a.foregroundMs)
        assertEquals(1, a.launchCount)
        assertEquals(start + 70_000, a.lastUsed)
    }

    @Test
    fun `switching screens inside one app is one visit`() {
        // Real Android order: A paused, then B resumed a moment later.
        val result = run(
            ev("app.a", "ScreenA", ACTIVITY_RESUMED, 0),
            ev("app.a", "ScreenA", ACTIVITY_PAUSED, 30_000),
            ev("app.a", "ScreenB", ACTIVITY_RESUMED, 30_300),
            ev("app.a", "ScreenB", ACTIVITY_PAUSED, 90_000),
        )
        val a = result.single()
        assertEquals(1, a.launchCount)
        // The 300 ms gap isn't on screen, so it's not counted.
        assertEquals(89_700L, a.foregroundMs)
    }

    @Test
    fun `reopening after a real break counts as a new launch`() {
        val result = run(
            ev("app.a", "Main", ACTIVITY_RESUMED, 0),
            ev("app.a", "Main", ACTIVITY_PAUSED, 10_000),
            ev("app.a", "Main", ACTIVITY_RESUMED, 60_000),
            ev("app.a", "Main", ACTIVITY_PAUSED, 70_000),
        )
        val a = result.single()
        assertEquals(2, a.launchCount)
        assertEquals(20_000L, a.foregroundMs)
    }

    @Test
    fun `overlapping screens of one app are not double counted`() {
        val result = run(
            ev("app.a", "ScreenA", ACTIVITY_RESUMED, 0),
            ev("app.a", "ScreenB", ACTIVITY_RESUMED, 5_000), // e.g. split screen
            ev("app.a", "ScreenA", ACTIVITY_PAUSED, 20_000),
            ev("app.a", "ScreenB", ACTIVITY_PAUSED, 40_000),
        )
        assertEquals(40_000L, result.single().foregroundMs)
    }

    @Test
    fun `duplicate resume of the same screen is ignored`() {
        val result = run(
            ev("app.a", "Main", ACTIVITY_RESUMED, 0),
            ev("app.a", "Main", ACTIVITY_RESUMED, 5_000),
            ev("app.a", "Main", ACTIVITY_PAUSED, 10_000),
        )
        val a = result.single()
        assertEquals(10_000L, a.foregroundMs)
        assertEquals(1, a.launchCount)
    }

    @Test
    fun `app already open when the window starts is counted from the start`() {
        // The first thing we see is a pause: it was resumed before the window.
        val result = run(ev("app.a", "Main", ACTIVITY_PAUSED, 45_000))
        val a = result.single()
        assertEquals(45_000L, a.foregroundMs)
        assertEquals(0, a.launchCount)
    }

    @Test
    fun `app still open when the window ends is counted until the end`() {
        val result = run(ev("app.a", "Main", ACTIVITY_RESUMED, end - start - 60_000))
        assertEquals(60_000L, result.single().foregroundMs)
    }

    @Test
    fun `foreground service time is tracked separately from screen time`() {
        val result = run(
            ev("music", "Player", ACTIVITY_RESUMED, 0),
            ev("music", "Player", ACTIVITY_PAUSED, 10_000),
            ev("music", "PlaybackService", FOREGROUND_SERVICE_START, 5_000),
            ev("music", "PlaybackService", FOREGROUND_SERVICE_STOP, 305_000),
        )
        val m = result.single()
        assertEquals(10_000L, m.foregroundMs)
        assertEquals(300_000L, m.foregroundServiceMs)
    }

    @Test
    fun `background-only app is included`() {
        val result = run(
            ev("sync", "UploadService", FOREGROUND_SERVICE_START, 0),
            ev("sync", "UploadService", FOREGROUND_SERVICE_STOP, 120_000),
        )
        val s = result.single()
        assertEquals(0L, s.foregroundMs)
        assertEquals(120_000L, s.foregroundServiceMs)
        assertEquals(0, s.launchCount)
    }

    @Test
    fun `apps with only unrelated events are left out`() {
        val result = run(ev("app.a", "Main", 23 /* ACTIVITY_STOPPED */, 1_000))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `results are sorted by total activity, busiest first`() {
        val result = run(
            ev("small", "M", ACTIVITY_RESUMED, 0),
            ev("small", "M", ACTIVITY_PAUSED, 1_000),
            ev("big", "M", ACTIVITY_RESUMED, 2_000),
            ev("big", "M", ACTIVITY_PAUSED, 100_000),
        )
        assertEquals(listOf("big", "small"), result.map { it.packageName })
    }
}
