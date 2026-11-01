package com.aditya.powermonitor.data.repository

import com.aditya.powermonitor.data.collector.AppUsage
import com.aditya.powermonitor.data.repository.DrainEstimator.DEFAULT_INTERVAL_MS
import com.aditya.powermonitor.data.repository.DrainEstimator.MAX_INTERVAL_MS
import org.junit.Assert.assertEquals
import org.junit.Test

class DrainEstimatorTest {

    private val now = 10_000_000_000L

    private fun app(fgMin: Long, serviceMin: Long = 0) = AppUsage(
        packageName = "p$fgMin-$serviceMin",
        label = "",
        foregroundMs = fgMin * 60_000,
        foregroundServiceMs = serviceMin * 60_000,
        launchCount = 1,
        lastUsed = 0,
    )

    // intervalStart

    @Test
    fun `first ever log covers the default window`() {
        assertEquals(now - DEFAULT_INTERVAL_MS, DrainEstimator.intervalStart(null, now))
    }

    @Test
    fun `next log starts exactly where the previous one ended`() {
        val previous = now - 20 * 60_000
        assertEquals(previous, DrainEstimator.intervalStart(previous, now))
    }

    @Test
    fun `a previous log older than the max falls back to the default window`() {
        val previous = now - MAX_INTERVAL_MS - 1
        assertEquals(now - DEFAULT_INTERVAL_MS, DrainEstimator.intervalStart(previous, now))
    }

    @Test
    fun `a previous log in the future (clock changed) falls back to the default window`() {
        assertEquals(now - DEFAULT_INTERVAL_MS, DrainEstimator.intervalStart(now + 1, now))
    }

    // batteryDrop

    @Test
    fun `drop is the level difference while unplugged`() {
        assertEquals(3, DrainEstimator.batteryDrop(80, false, 77, false))
    }

    @Test
    fun `no drop is counted if charging at either end`() {
        assertEquals(0, DrainEstimator.batteryDrop(80, true, 77, false))
        assertEquals(0, DrainEstimator.batteryDrop(80, false, 77, true))
    }

    @Test
    fun `a rising level never counts as negative drain`() {
        assertEquals(0, DrainEstimator.batteryDrop(50, false, 52, false))
    }

    // shares

    @Test
    fun `drop is split in proportion to foreground time`() {
        val shares = DrainEstimator.shares(listOf(app(30), app(10)), dropPercent = 4)
        assertEquals(3f, shares[0], 1e-4f)
        assertEquals(1f, shares[1], 1e-4f)
    }

    @Test
    fun `foreground service time counts at its lower weight`() {
        // 10 min on screen vs 10 min background (weighted 0.3) -> 10 : 3.
        val shares = DrainEstimator.shares(listOf(app(10), app(0, 10)), dropPercent = 13)
        assertEquals(10f, shares[0], 1e-4f)
        assertEquals(3f, shares[1], 1e-4f)
    }

    @Test
    fun `shares always add up to the whole drop`() {
        val shares = DrainEstimator.shares(listOf(app(7), app(3, 11), app(1)), dropPercent = 5)
        assertEquals(5f, shares.sum(), 1e-4f)
    }

    @Test
    fun `no drop means every share is zero`() {
        assertEquals(listOf(0f, 0f), DrainEstimator.shares(listOf(app(5), app(1)), 0))
    }

    @Test
    fun `no activity means nothing to share`() {
        assertEquals(listOf(0f), DrainEstimator.shares(listOf(app(0)), 4))
        assertEquals(emptyList<Float>(), DrainEstimator.shares(emptyList(), 4))
    }
}
