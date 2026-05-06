package com.example.meetingbell.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.meetingbell.AppContainer
import com.example.meetingbell.data.calendar.CalendarInfo
import com.example.meetingbell.data.calendar.CalendarReader
import com.example.meetingbell.data.prefs.AppPreferences
import com.example.meetingbell.data.prefs.AppSettings
import com.example.meetingbell.domain.ScheduleAlarms
import com.example.meetingbell.domain.SyncMeetings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class SettingsViewModel(
    application: Application,
    private val appPreferences: AppPreferences,
    private val calendarReader: CalendarReader,
    private val syncMeetings: SyncMeetings,
    private val scheduleAlarms: ScheduleAlarms,
) : AndroidViewModel(application) {

    val settings: StateFlow<AppSettings> = appPreferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings(
            selectedCalendarIds = emptySet(),
            leadTimeMinutes = 1,
            skipDeclined = true,
            skipAllDay = true,
            alarmSoundUri = null,
            maxDurationSeconds = 60,
            onboardingComplete = false,
        ))

    private val _calendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    val availableCalendars: StateFlow<List<CalendarInfo>> = _calendars

    init {
        refreshCalendars()
    }

    fun refreshCalendars() {
        viewModelScope.launch { _calendars.value = calendarReader.queryCalendars() }
    }

    fun setLeadTime(minutes: Int) {
        viewModelScope.launch { appPreferences.setLeadTimeMinutes(minutes) }
    }

    fun toggleCalendar(calendarId: Long, selected: Boolean) {
        viewModelScope.launch {
            val current = settings.value.selectedCalendarIds.toMutableSet()
            if (selected) current.add(calendarId) else current.remove(calendarId)
            appPreferences.setSelectedCalendarIds(current)
        }
    }

    fun setSkipDeclined(skip: Boolean) {
        viewModelScope.launch { appPreferences.setSkipDeclined(skip) }
    }

    fun setSkipAllDay(skip: Boolean) {
        viewModelScope.launch { appPreferences.setSkipAllDay(skip) }
    }

    fun setAlarmSoundUri(uri: Uri?) {
        viewModelScope.launch { appPreferences.setAlarmSoundUri(uri) }
    }

    fun setMaxDuration(seconds: Int) {
        viewModelScope.launch { appPreferences.setMaxDurationSeconds(seconds) }
    }

    fun triggerTestAlarm() {
        viewModelScope.launch {
            // Synthetic meeting 6 seconds from now, alarm fires in 5 seconds (lead = 1s)
            val now = System.currentTimeMillis()
            val fakeStartUtc = now + TimeUnit.SECONDS.toMillis(6)
            scheduleAlarms.schedule(
                listOf(
                    com.example.meetingbell.data.calendar.MeetingEvent(
                        instanceId = Long.MAX_VALUE, // sentinel for test alarm
                        eventId = Long.MAX_VALUE,
                        title = "Test Alarm",
                        startUtc = fakeStartUtc,
                        endUtc = fakeStartUtc + TimeUnit.MINUTES.toMillis(30),
                        calendarId = -1L,
                        calendarDisplayName = "Test",
                        calendarColor = 0xFF1565C0.toInt(),
                        isAllDay = false,
                        selfAttendeeStatus = 0,
                        location = null,
                    )
                ),
                leadMinutes = 0,
            )
        }
    }

    fun runSync() {
        viewModelScope.launch { syncMeetings.execute() }
    }

    companion object {
        fun factory(container: AppContainer, app: Application): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    SettingsViewModel(
                        app,
                        container.appPreferences,
                        container.calendarReader,
                        container.syncMeetings,
                        container.scheduleAlarms,
                    )
                }
            }
    }
}
