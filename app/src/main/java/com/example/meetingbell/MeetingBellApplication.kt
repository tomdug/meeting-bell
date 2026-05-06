package com.example.meetingbell

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.meetingbell.worker.SyncWorker
import java.util.concurrent.TimeUnit

class MeetingBellApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        schedulePeriodSync()
    }

    private fun schedulePeriodSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "meeting_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
