package com.aditya.powermonitor.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aditya.powermonitor.PowerMonitorApp

/** Deletes old rows so the database doesn't grow forever. */
class CleanupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as PowerMonitorApp).container.repository
        return try {
            val deleted = repository.deleteOlderThan(KEEP_DAYS)
            Log.i(TAG, "Deleted $deleted rows older than $KEEP_DAYS days")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "CleanupWorker"
        const val KEEP_DAYS = 30
    }
}
