package com.example.meetingbell.data.prefs

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.core.net.toUri

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val selectedCalendarIds: Set<Long>,
    val leadTimeMinutes: Int,
    val skipDeclined: Boolean,
    val skipAllDay: Boolean,
    val alarmSoundUri: Uri?,
    val maxDurationSeconds: Int,
    val onboardingComplete: Boolean,
)

class AppPreferences(private val context: Context) {

    private object Keys {
        val SELECTED_CALENDAR_IDS = stringSetPreferencesKey("selected_calendar_ids")
        val LEAD_TIME_MINUTES = intPreferencesKey("lead_time_minutes")
        val SKIP_DECLINED = booleanPreferencesKey("skip_declined")
        val SKIP_ALL_DAY = booleanPreferencesKey("skip_all_day")
        val ALARM_SOUND_URI = stringPreferencesKey("alarm_sound_uri")
        val MAX_DURATION_SECONDS = intPreferencesKey("max_duration_seconds")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            selectedCalendarIds = prefs[Keys.SELECTED_CALENDAR_IDS]
                ?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet(),
            leadTimeMinutes = prefs[Keys.LEAD_TIME_MINUTES] ?: 1,
            skipDeclined = prefs[Keys.SKIP_DECLINED] ?: true,
            skipAllDay = prefs[Keys.SKIP_ALL_DAY] ?: true,
            alarmSoundUri = prefs[Keys.ALARM_SOUND_URI]
                        ?.takeIf { it.isNotEmpty() }?.toUri(),
            maxDurationSeconds = prefs[Keys.MAX_DURATION_SECONDS] ?: 60,
            onboardingComplete = prefs[Keys.ONBOARDING_COMPLETE] ?: false,
        )
    }

    suspend fun setSelectedCalendarIds(ids: Set<Long>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_CALENDAR_IDS] = ids.map { it.toString() }.toSet()
        }
    }

    suspend fun setLeadTimeMinutes(minutes: Int) {
        context.dataStore.edit { prefs -> prefs[Keys.LEAD_TIME_MINUTES] = minutes }
    }

    suspend fun setSkipDeclined(skip: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.SKIP_DECLINED] = skip }
    }

    suspend fun setSkipAllDay(skip: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.SKIP_ALL_DAY] = skip }
    }

    suspend fun setAlarmSoundUri(uri: Uri?) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ALARM_SOUND_URI] = uri?.toString() ?: ""
        }
    }

    suspend fun setMaxDurationSeconds(seconds: Int) {
        context.dataStore.edit { prefs -> prefs[Keys.MAX_DURATION_SECONDS] = seconds }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.ONBOARDING_COMPLETE] = complete }
    }
}
