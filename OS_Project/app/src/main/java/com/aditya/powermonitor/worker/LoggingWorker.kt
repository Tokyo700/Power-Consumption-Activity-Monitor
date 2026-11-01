package com.aditya.powermonitor.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aditya.powermonitor.PowerMonitorApp

/**
 * Runs one logging pass in the background. WorkManager creates this class itself, so it
 * can't take the repository as a constructor argument; it fetches it from AppContainer.
 */
class LoggingWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as PowerMonitorApp).container.repository
        return try {
            val r = repository.logSnapshot()
            Log.i(TAG, "Logged ${r.appsLogged} apps, battery drop ${r.batteryDropPercent}%")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Logging failed", e)
            // retry() asks WorkManager to run this again later with exponential backoff.
            Result.retry()
        }
    }

    companion object {
        const val TAG = "LoggingWorker"
    }
}
