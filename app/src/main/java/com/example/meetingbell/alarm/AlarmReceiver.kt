package com.example.meetingbell.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import java.util.Date

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val instanceId = intent.getLongExtra(EXTRA_INSTANCE_ID, -1L)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Meeting"
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, -1)

        Log.i(TAG, "FIRED: '$title' instanceId=$instanceId rc=$requestCode at ${Date()}")
        AlarmLogger.log(context, "ALARM_FIRED title='$title' instanceId=$instanceId rc=$requestCode")

        val pm = context.getSystemService(PowerManager::class.java)
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MeetingBell:AlarmReceiver")
        wl.acquire(10_000L)

        AlarmService.start(context, instanceId, title, requestCode)

        wl.release()
    }

    companion object {
        const val EXTRA_INSTANCE_ID = "event_instance_id"
        const val EXTRA_TITLE = "event_title"
        const val EXTRA_REQUEST_CODE = "alarm_request_code"
        private const val TAG = "AlarmReceiver"
    }
}
