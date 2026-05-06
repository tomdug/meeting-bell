package com.example.meetingbell.ui.home

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.meetingbell.AppContainer
import com.example.meetingbell.data.db.ScheduledAlarm
import com.example.meetingbell.data.db.ScheduledAlarmDao
import com.example.meetingbell.data.prefs.AppPreferences
import com.example.meetingbell.domain.ScheduleAlarms
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReliabilityState(
    val hasCalendarPermission: Boolean,
    val hasNotificationPermission: Boolean,
    val hasExactAlarmPermission: Boolean,
    val hasDndAccess: Boolean,
    val isBatteryOptimizationExempt: Boolean,
    val hasFullScreenIntentPermission: Boolean,
    val selectedCalendarCount: Int,
) {
    val isFullyArmed: Boolean
        get() = hasCalendarPermission &&
            hasNotificationPermission &&
            hasExactAlarmPermission &&
            hasDndAccess &&
            isBatteryOptimizationExempt &&
            hasFullScreenIntentPermission &&
            selectedCalendarCount > 0
}

class HomeViewModel(
    application: Application,
    private val dao: ScheduledAlarmDao,
    private val appPreferences: AppPreferences,
    private val scheduleAlarms: ScheduleAlarms,
) : AndroidViewModel(application) {

    private val now get() = System.currentTimeMillis()

    val upcomingAlarms: StateFlow<List<ScheduledAlarm>> = dao
        .observeUpcoming(System.currentTimeMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _reliabilityRefresh = MutableStateFlow(0)
    val reliabilityState: StateFlow<ReliabilityState> = _reliabilityRefresh
        .combine(appPreferences.settingsFlow) { _, settings ->
            buildReliabilityState(settings.selectedCalendarIds.size)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), buildReliabilityState(0))

    fun refreshReliability() {
        _reliabilityRefresh.value++
    }

    fun muteAlarm(eventInstanceId: Long) {
        viewModelScope.launch {
            scheduleAlarms.cancel(listOfNotNull(dao.getByInstanceId(eventInstanceId)))
        }
    }

    fun unmuteAlarm(eventInstanceId: Long) {
        viewModelScope.launch {
            val alarm = dao.getByInstanceId(eventInstanceId) ?: return@launch
            if (alarm.alarmFireUtc > now) {
                scheduleAlarms.reschedule(alarm)
            }
        }
    }

    private fun buildReliabilityState(selectedCalendarCount: Int): ReliabilityState {
        val ctx = getApplication<Application>()
        val alarmManager = ctx.getSystemService(AlarmManager::class.java)
        val notificationManager = ctx.getSystemService(NotificationManager::class.java)
        val powerManager = ctx.getSystemService(PowerManager::class.java)

        return ReliabilityState(
            hasCalendarPermission = ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED,
            hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true,
            hasExactAlarmPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else true,
            hasDndAccess = notificationManager.isNotificationPolicyAccessGranted,
            isBatteryOptimizationExempt = powerManager.isIgnoringBatteryOptimizations(ctx.packageName),
            hasFullScreenIntentPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                notificationManager.canUseFullScreenIntent()
            } else true,
            selectedCalendarCount = selectedCalendarCount,
        )
    }

    companion object {
        fun factory(container: AppContainer, app: Application): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    HomeViewModel(app, container.dao, container.appPreferences, container.scheduleAlarms)
                }
            }
    }
}
