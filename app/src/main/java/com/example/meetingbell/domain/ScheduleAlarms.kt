package com.example.meetingbell.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.meetingbell.alarm.AlarmReceiver
import com.example.meetingbell.data.calendar.MeetingEvent
import com.example.meetingbell.data.db.AlarmState
import com.example.meetingbell.data.db.ScheduledAlarm
import com.example.meetingbell.data.db.ScheduledAlarmDao
import java.util.Date

class ScheduleAlarms(
    private val context: Context,
    private val dao: ScheduledAlarmDao,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    suspend fun schedule(events: List<MeetingEvent>, leadMinutes: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.e(TAG, "SCHEDULE_EXACT_ALARM not granted — skipping ${events.size} alarms")
            return
        }
        for (event in events) {
            val alarmFireUtc = event.startUtc - leadMinutes * 60_000L
            val requestCode = dao.getNextRequestCode()

            val alarmIntent = buildAlarmIntent(event.instanceId, event.title, requestCode)
            val showIntent = PendingIntent.getActivity(
                context,
                requestCode + 1_000_000,
                context.packageManager.getLaunchIntentForPackage(context.packageName)
                    ?: Intent(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(alarmFireUtc, showIntent),
                alarmIntent,
            )

            dao.upsert(
                ScheduledAlarm(
                    eventInstanceId = event.instanceId,
                    eventTitle = event.title,
                    meetingStartUtc = event.startUtc,
                    alarmFireUtc = alarmFireUtc,
                    requestCode = requestCode,
                    calendarId = event.calendarId,
                )
            )
            Log.i(TAG, "SCHEDULE '${event.title}' fires at ${Date(alarmFireUtc)} rc=$requestCode")
        }
    }

    suspend fun reschedule(alarm: ScheduledAlarm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.e(TAG, "SCHEDULE_EXACT_ALARM not granted — cannot reschedule '${alarm.eventTitle}'")
            return
        }
        val alarmIntent = buildAlarmIntent(alarm.eventInstanceId, alarm.eventTitle, alarm.requestCode)
        val showIntent = PendingIntent.getActivity(
            context,
            alarm.requestCode + 1_000_000,
            context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(alarm.alarmFireUtc, showIntent), alarmIntent)
        dao.updateState(alarm.id, AlarmState.SCHEDULED)
        Log.i(TAG, "RESCHEDULE '${alarm.eventTitle}' fires at ${Date(alarm.alarmFireUtc)} rc=${alarm.requestCode}")
    }

    suspend fun cancel(alarms: List<ScheduledAlarm>) {
        for (alarm in alarms) {
            val pi = buildAlarmIntent(alarm.eventInstanceId, alarm.eventTitle, alarm.requestCode)
            alarmManager.cancel(pi)
            dao.updateState(alarm.id, AlarmState.CANCELLED)
            Log.i(TAG, "CANCEL '${alarm.eventTitle}' rc=${alarm.requestCode}")
        }
    }

    suspend fun cancelAll() {
        cancel(dao.getAllScheduled())
    }

    private fun buildAlarmIntent(instanceId: Long, title: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_INSTANCE_ID, instanceId)
            putExtra(AlarmReceiver.EXTRA_TITLE, title)
            putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, requestCode)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val TAG = "ScheduleAlarms"
    }
}
