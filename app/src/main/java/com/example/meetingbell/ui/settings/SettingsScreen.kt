package com.example.meetingbell.ui.settings

import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val calendars by viewModel.availableCalendars.collectAsStateWithLifecycle()
    val snackbarState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshCalendars()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        viewModel.setAlarmSoundUri(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader("Calendars")
            if (calendars.isEmpty()) {
                Text(
                    text = "No calendars found. Grant calendar permission first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            calendars.forEach { cal ->
                val selected = cal.id in settings.selectedCalendarIds
                ListItem(
                    headlineContent = { Text(cal.displayName) },
                    supportingContent = { Text(cal.accountName) },
                    trailingContent = {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { viewModel.toggleCalendar(cal.id, it) }
                        )
                    }
                )
            }

            HorizontalDivider()
            SectionHeader("Lead Time")
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "${settings.leadTimeMinutes} minute${if (settings.leadTimeMinutes == 1) "" else "s"} before meeting",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = settings.leadTimeMinutes.toFloat(),
                    onValueChange = { viewModel.setLeadTime(it.roundToInt()) },
                    valueRange = 0f..15f,
                    steps = 14,
                )
            }

            HorizontalDivider()
            SectionHeader("Filters")
            ListItem(
                headlineContent = { Text("Skip declined events") },
                trailingContent = {
                    Switch(
                        checked = settings.skipDeclined,
                        onCheckedChange = { viewModel.setSkipDeclined(it) }
                    )
                }
            )
            ListItem(
                headlineContent = { Text("Skip all-day events") },
                trailingContent = {
                    Switch(
                        checked = settings.skipAllDay,
                        onCheckedChange = { viewModel.setSkipAllDay(it) }
                    )
                }
            )

            HorizontalDivider()
            SectionHeader("Alarm Sound")
            ListItem(
                headlineContent = { Text("Alarm tone") },
                supportingContent = {
                    Text(settings.alarmSoundUri?.lastPathSegment ?: "Default alarm")
                },
                modifier = Modifier.padding(0.dp),
                trailingContent = {
                    Button(onClick = {
                        val intent = android.content.Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, settings.alarmSoundUri)
                        }
                        ringtoneLauncher.launch(intent)
                    }) { Text("Choose") }
                }
            )

            HorizontalDivider()
            SectionHeader("Auto-stop Duration")
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "${settings.maxDurationSeconds}s before auto-stop",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = settings.maxDurationSeconds.toFloat(),
                    onValueChange = { viewModel.setMaxDuration(it.roundToInt()) },
                    valueRange = 10f..300f,
                )
            }

            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        viewModel.triggerTestAlarm()
                        scope.launch { snackbarState.showSnackbar("Test alarm fires in ~5 seconds") }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Test Alarm")
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
