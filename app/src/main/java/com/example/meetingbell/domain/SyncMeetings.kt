package com.example.meetingbell.domain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.meetingbell.data.calendar.CalendarReader
import com.example.meetingbell.data.calendar.MeetingEvent
import com.example.meetingbell.data.prefs.AppPreferences
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class SyncMeetings(
    private val context: Context,
    private val calendarReader: CalendarReader,
    private val appPreferences: AppPreferences,
    private val diffAlarms: DiffAlarms,
    private val scheduleAlarms: ScheduleAlarms,
) {
    suspend fun execute() {
        if (!hasCalendarPermission()) {
            Log.w(TAG, "READ_CALENDAR not granted — skipping sync")
            return
        }

        val settings = appPreferences.settingsFlow.first()
        if (settings.selectedCalendarIds.isEmpty()) {
            Log.w(TAG, "No calendars selected — skipping sync")
            return
        }

        val now = System.currentTimeMillis()
        val windowEnd = now + TimeUnit.DAYS.toMillis(7)
        val freshEvents = calendarReader.queryInstances(settings.selectedCalendarIds, now, windowEnd)

        val filtered = freshEvents.filter { event ->
            (!settings.skipAllDay || !event.isAllDay) &&
                (!settings.skipDeclined || event.selfAttendeeStatus != android.provider.CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED)
        }

        val diff = diffAlarms.execute(filtered, settings.leadTimeMinutes)

        scheduleAlarms.cancel(diff.toCancel)
        scheduleAlarms.schedule(diff.toSchedule, settings.leadTimeMinutes)

        Log.i(
            TAG,
            "Sync complete: schedule=${diff.toSchedule.size} cancel=${diff.toCancel.size} " +
                "unchanged=${diff.unchanged.size}"
        )
    }

    private fun hasCalendarPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "SyncMeetings"
    }
}
