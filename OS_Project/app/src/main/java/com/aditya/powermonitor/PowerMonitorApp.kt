package com.aditya.powermonitor

import android.app.Application
import com.aditya.powermonitor.di.AppContainer
import com.aditya.powermonitor.worker.WorkScheduler

/** Process-wide singleton. Builds the AppContainer and makes sure background logging is on. */
class PowerMonitorApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        WorkScheduler.schedulePeriodic(this)
    }
}
