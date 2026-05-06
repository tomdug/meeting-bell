package com.example.meetingbell.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.meetingbell.MeetingBellApplication
import java.util.Date

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Sync started at ${Date()}")
        return try {
            (applicationContext as MeetingBellApplication).container.syncMeetings.execute()
            Log.i(TAG, "Sync complete at ${Date()}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed (attempt ${runAttemptCount + 1})", e)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
    }
}
