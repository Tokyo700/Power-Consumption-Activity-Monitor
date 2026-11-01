package com.aditya.powermonitor.di

import android.content.Context
import com.aditya.powermonitor.data.collector.BatteryCollector
import com.aditya.powermonitor.data.collector.SelfCpuSampler
import com.aditya.powermonitor.data.collector.UsageCollector
import com.aditya.powermonitor.data.db.AppDatabase
import com.aditya.powermonitor.data.export.CsvExporter
import com.aditya.powermonitor.data.repository.MonitorRepository

/**
 * Creates the app's long-lived objects once and hands the same instances to whoever needs
 * them ("manual dependency injection").
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val batteryCollector = BatteryCollector(appContext)
    val usageCollector = UsageCollector(appContext)
    val selfCpuSampler = SelfCpuSampler()

    // `by lazy` opens the database the first time something asks for it, not at app launch.
    val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val repository: MonitorRepository by lazy {
        MonitorRepository(database, batteryCollector, usageCollector)
    }
    val csvExporter: CsvExporter by lazy { CsvExporter(appContext, repository) }
}
