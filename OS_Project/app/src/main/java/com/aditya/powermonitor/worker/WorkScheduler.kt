package com.aditya.powermonitor.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

object WorkScheduler {
    const val PERIODIC_WORK_NAME = "periodic-logging"
    const val CLEANUP_WORK_NAME = "daily-cleanup"
    private const val ONE_OFF_WORK_NAME = "one-off-logging"
    private const val ONE_OFF_CLEANUP_NAME = "one-off-cleanup"

    /**
     * Registers the 15-minute job. "Unique" work with KEEP means calling this on every app
     * launch is safe: if the job already exists it's left alone instead of being duplicated
     * or having its timer reset. WorkManager saves the job in its own database and reschedules
     * it after a reboot. No constraints are set, because we want to log even on low battery.
     */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<LoggingWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Runs the cleanup about once a day. The charging constraint tells the OS to wait until
     * the phone is plugged in, so housekeeping never costs the user battery.
     */
    fun scheduleCleanup(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .build()
        val request = PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CLEANUP_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Runs the cleanup once, now, with no charging constraint, for testing. */
    fun runCleanupOnce(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_OFF_CLEANUP_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CleanupWorker>().build(),
        )
    }

    /** Runs the worker once, now, through WorkManager, for testing the background path. */
    fun runOnce(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_OFF_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<LoggingWorker>().build(),
        )
    }

    /** Live status of the periodic job, or null if it isn't scheduled. */
    fun observePeriodic(context: Context): Flow<WorkInfo?> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(PERIODIC_WORK_NAME)
            .map { it.firstOrNull() }
}
