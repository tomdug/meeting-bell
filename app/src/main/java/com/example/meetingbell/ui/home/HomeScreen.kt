package com.example.meetingbell.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.meetingbell.data.db.AlarmState
import com.example.meetingbell.data.db.ScheduledAlarm
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    onOpenOnboarding: () -> Unit,
) {
    val alarms by viewModel.upcomingAlarms.collectAsStateWithLifecycle()
    val reliability by viewModel.reliabilityState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshReliability()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MeetingBell") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ReliabilityBanner(
                reliability = reliability,
                onFix = onOpenOnboarding,
            )

            if (alarms.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No upcoming meetings in the next 48 hours",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val now = System.currentTimeMillis()
                val cutoff = now + TimeUnit.HOURS.toMillis(48)
                val visible = alarms.filter { it.alarmFireUtc < cutoff }
                LazyColumn {
                    items(visible, key = { it.id }) { alarm ->
                        MeetingRow(
                            alarm = alarm,
                            onMuteToggle = { muted ->
                                if (muted) viewModel.muteAlarm(alarm.eventInstanceId)
                                else viewModel.unmuteAlarm(alarm.eventInstanceId)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReliabilityBanner(reliability: ReliabilityState, onFix: () -> Unit) {
    AnimatedVisibility(visible = true) {
        if (reliability.isFullyArmed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B5E20))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "All systems armed",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Setup incomplete",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    val missing = buildList {
                        if (!reliability.hasCalendarPermission) add("Calendar access")
                        if (!reliability.hasNotificationPermission) add("Notifications")
                        if (!reliability.hasExactAlarmPermission) add("Exact alarms")
                        if (!reliability.hasDndAccess) add("DND bypass")
                        if (!reliability.isBatteryOptimizationExempt) add("Battery exemption")
                        if (!reliability.hasFullScreenIntentPermission) add("Full-screen alerts")
                        if (reliability.selectedCalendarCount == 0) add("No calendars selected")
                    }
                    Text(
                        text = missing.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                FilledTonalButton(onClick = onFix) { Text("Fix") }
            }
        }
    }
}

@Composable
private fun MeetingRow(alarm: ScheduledAlarm, onMuteToggle: (Boolean) -> Unit) {
    val muted = alarm.state == AlarmState.CANCELLED
    
    val meetingZonedDateTime = Instant.ofEpochMilli(alarm.meetingStartUtc)
        .atZone(ZoneId.systemDefault())
    val meetingDate = meetingZonedDateTime.toLocalDate()
    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)

    val locale = LocalConfiguration.current.locales[0]
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)
    val dayFormatter = DateTimeFormatter.ofPattern("EEE HH:mm", locale)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1565C0)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alarm.eventTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                val startFormatted = when (meetingDate) {
                    today -> "Today ${timeFormatter.format(meetingZonedDateTime)}"
                    tomorrow -> "Tomorrow ${timeFormatter.format(meetingZonedDateTime)}"
                    else -> dayFormatter.format(meetingZonedDateTime)
                }
                Text(
                    text = startFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val alarmZonedDateTime = Instant.ofEpochMilli(alarm.alarmFireUtc)
                    .atZone(ZoneId.systemDefault())
                Text(
                    text = if (muted) "Alarm muted" else
                        "Alarm at ${timeFormatter.format(alarmZonedDateTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (muted) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }
            Switch(
                checked = !muted,
                onCheckedChange = { on -> onMuteToggle(!on) },
            )
        }
    }
}
