package com.aditya.powermonitor.data.collector

import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import java.io.File

/** CPU usage of our own process, plus a check of what the OS still lets us read. */
data class CpuSample(
    /** CPU time this process has used since it started (user + kernel). */
    val processCpuMs: Long,
    /** Wall-clock time since this process started. */
    val processUptimeMs: Long,
    /** processCpuMs / (uptime × cores): average share of the whole CPU we've used. */
    val averageCpuPercent: Float,
    val cores: Int,
    /** utime and stime from /proc/self/stat, converted from clock ticks to ms. */
    val userMs: Long?,
    val kernelMs: Long?,
    /** Whether system-wide /proc/stat is readable. Android 8+ blocks it for normal apps. */
    val canReadSystemProcStat: Boolean,
    /** Whether another process's stat (init, pid 1) is readable. Also blocked. */
    val canReadOtherProcess: Boolean,
)

class SelfCpuSampler {

    fun sample(): CpuSample {
        val cpuMs = Process.getElapsedCpuTime()
        val uptimeMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        val cores = Runtime.getRuntime().availableProcessors()
        val (userMs, kernelMs) = readSelfStat()
        return CpuSample(
            processCpuMs = cpuMs,
            processUptimeMs = uptimeMs,
            averageCpuPercent =
                if (uptimeMs > 0) cpuMs * 100f / (uptimeMs * cores) else 0f,
            cores = cores,
            userMs = userMs,
            kernelMs = kernelMs,
            canReadSystemProcStat = canRead("/proc/stat"),
            canReadOtherProcess = canRead("/proc/1/stat"),
        )
    }

    /**
     * /proc/self/stat is one line of space-separated fields. Field 2 is the process name in
     * parentheses and may itself contain spaces, so we split after the last ')'. Fields 14 and
     * 15 (utime, stime) are counted in clock ticks, usually 100 per second.
     */
    private fun readSelfStat(): Pair<Long?, Long?> = try {
        val line = File("/proc/self/stat").readText()
        val fields = line.substringAfterLast(')').trim().split(' ')
        // fields[0] is field 3 (state), so field n is fields[n - 3].
        val ticksPerSecond = Os.sysconf(OsConstants._SC_CLK_TCK).takeIf { it > 0 } ?: 100L
        val utime = fields[14 - 3].toLong() * 1000 / ticksPerSecond
        val stime = fields[15 - 3].toLong() * 1000 / ticksPerSecond
        utime to stime
    } catch (e: Exception) {
        null to null
    }

    private fun canRead(path: String): Boolean = try {
        File(path).readText().isNotEmpty()
    } catch (e: Exception) {
        false
    }
}
