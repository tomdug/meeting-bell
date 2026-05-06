package com.example.meetingbell.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CalendarReader(private val context: Context) {

    private fun hasPermission() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED

    suspend fun queryCalendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
        )
        val calendars = mutableListOf<CalendarInfo>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.VISIBLE} = 1",
            null,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val nameCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accountCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            val colorCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)
            while (cursor.moveToNext()) {
                calendars.add(
                    CalendarInfo(
                        id = cursor.getLong(idCol),
                        displayName = cursor.getString(nameCol) ?: "",
                        accountName = cursor.getString(accountCol) ?: "",
                        color = cursor.getInt(colorCol),
                    )
                )
            }
        }
        calendars
    }

    suspend fun queryInstances(
        calendarIds: Set<Long>,
        windowStart: Long,
        windowEnd: Long,
    ): List<MeetingEvent> = withContext(Dispatchers.IO) {
        if (!hasPermission() || calendarIds.isEmpty()) return@withContext emptyList()

        val projection = arrayOf(
            CalendarContract.Instances._ID,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.CALENDAR_COLOR,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        )

        val placeholders = calendarIds.joinToString(",") { "?" }
        val selection = "${CalendarContract.Instances.CALENDAR_ID} IN ($placeholders)"
        val selectionArgs = calendarIds.map { it.toString() }.toTypedArray()

        val builder: Uri.Builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, windowStart)
        ContentUris.appendId(builder, windowEnd)

        val events = mutableListOf<MeetingEvent>()
        context.contentResolver.query(
            builder.build(),
            projection,
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances._ID)
            val eventIdCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDayCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            val statusCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.SELF_ATTENDEE_STATUS)
            val locationCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
            val calIdCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
            val calColorCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_COLOR)
            val calNameCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                events.add(
                    MeetingEvent(
                        instanceId = cursor.getLong(idCol),
                        eventId = cursor.getLong(eventIdCol),
                        title = cursor.getString(titleCol) ?: "(No title)",
                        startUtc = cursor.getLong(beginCol),
                        endUtc = cursor.getLong(endCol),
                        calendarId = cursor.getLong(calIdCol),
                        calendarDisplayName = cursor.getString(calNameCol) ?: "",
                        calendarColor = cursor.getInt(calColorCol),
                        isAllDay = cursor.getInt(allDayCol) != 0,
                        selfAttendeeStatus = cursor.getInt(statusCol),
                        location = cursor.getString(locationCol)?.takeIf { it.isNotBlank() },
                    )
                )
            }
        }
        events
    }
}
