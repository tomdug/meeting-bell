package com.example.meetingbell

import android.content.Context
import com.example.meetingbell.data.calendar.CalendarReader
import com.example.meetingbell.data.db.AppDatabase
import com.example.meetingbell.data.db.ScheduledAlarmDao
import com.example.meetingbell.data.prefs.AppPreferences
import com.example.meetingbell.domain.DiffAlarms
import com.example.meetingbell.domain.ScheduleAlarms
import com.example.meetingbell.domain.SyncMeetings

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val db: AppDatabase by lazy { AppDatabase.getInstance(appContext) }
    val dao: ScheduledAlarmDao by lazy { db.scheduledAlarmDao() }
    val appPreferences: AppPreferences by lazy { AppPreferences(appContext) }
    val calendarReader: CalendarReader by lazy { CalendarReader(appContext) }

    val diffAlarms: DiffAlarms by lazy { DiffAlarms(dao) }
    val scheduleAlarms: ScheduleAlarms by lazy { ScheduleAlarms(appContext, dao) }
    val syncMeetings: SyncMeetings by lazy {
        SyncMeetings(appContext, calendarReader, appPreferences, diffAlarms, scheduleAlarms)
    }
}
