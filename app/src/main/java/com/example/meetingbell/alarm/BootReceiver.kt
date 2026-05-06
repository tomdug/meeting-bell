package com.example.meetingbell.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.example.meetingbell.worker.SyncWorker
import java.util.Date

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.LOCKED_BOOT_COMPLETED"
        ) return

        Log.i(TAG, "Boot completed at ${Date()} — enqueuing sync")
        AlarmLogger.log(context, "BOOT_COMPLETED — enqueuing sync")

        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        )
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
