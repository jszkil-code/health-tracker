package dev.healthtracker.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.healthtracker.app.data.log.AppLogger
import javax.inject.Inject

@HiltAndroidApp
class HealthTrackerApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var logger: AppLogger

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        System.loadLibrary("sqlcipher")
        super.onCreate()
        logger.info("app", "App started, version ${BuildConfig.VERSION_NAME}")
    }
}
