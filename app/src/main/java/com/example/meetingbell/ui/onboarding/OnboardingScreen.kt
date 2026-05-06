package com.example.meetingbell.ui.onboarding

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meetingbell.AppContainer
import com.example.meetingbell.data.calendar.CalendarInfo
import kotlinx.coroutines.launch

private data class OnboardingStep(
    val title: String,
    val description: String,
    val isCritical: Boolean,
    val isGranted: (context: android.content.Context) -> Boolean,
)

private const val STEP_SELECT_CALENDARS = "Select Calendars"

@Composable
fun OnboardingScreen(
    container: AppContainer,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentStep by remember { mutableIntStateOf(0) }
    var refreshTick by remember { mutableIntStateOf(0) }

    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val notifManager = context.getSystemService(NotificationManager::class.java)
    val powerManager = context.getSystemService(PowerManager::class.java)

    val steps = buildList {
        add(OnboardingStep(
            title = "Calendar Access",
            description = "MeetingBell needs to read your calendar to schedule alarms before your meetings.",
            isCritical = true,
            isGranted = { ctx ->
                ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        ))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(OnboardingStep(
                title = "Notifications",
                description = "Required to show the alarm notification when a meeting is about to start.",
                isCritical = true,
                isGranted = { ctx ->
                    ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                }
            ))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(OnboardingStep(
                title = "Exact Alarm Permission",
                description = "Needed to fire alarms at precisely the right time. Without this, the alarm may be late or not fire at all.",
                isCritical = true,
                isGranted = { _ -> alarmManager.canScheduleExactAlarms() }
            ))
        }
        add(OnboardingStep(
            title = "Do Not Disturb Access",
            description = "Allows the alarm to sound even when DND is on. This is critical — without it, the alarm will be silenced.",
            isCritical = true,
            isGranted = { _ -> notifManager.isNotificationPolicyAccessGranted }
        ))
        add(OnboardingStep(
            title = "Battery Optimization Exemption",
            description = "Prevents Android from delaying background tasks. Required for the alarm to fire when the app is not open.",
            isCritical = true,
            isGranted = { ctx -> powerManager.isIgnoringBatteryOptimizations(ctx.packageName) }
        ))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(OnboardingStep(
                title = "Full-Screen Alerts",
                description = "Allows the alarm to appear over the lock screen. Recommended for the best experience.",
                isCritical = false,
                isGranted = { _ -> notifManager.canUseFullScreenIntent() }
            ))
        }
        add(OnboardingStep(
            title = STEP_SELECT_CALENDARS,
            description = "Choose which calendars MeetingBell should watch for meetings.",
            isCritical = true,
            isGranted = { _ -> false }, // resolved via selectedCalendarIds state below
        ))
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshTick++ }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshTick++ }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshTick++ }

    // Track selected calendars reactively for the calendar selection step
    var selectedCalendarIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(Unit) {
        container.appPreferences.settingsFlow.collect { settings ->
            selectedCalendarIds = settings.selectedCalendarIds
        }
    }

    // Load available calendars when we reach the calendar selection step
    var availableCalendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }

    if (currentStep >= steps.size) {
        scope.launch { container.appPreferences.setOnboardingComplete(true) }
        onComplete()
        return
    }

    val step = steps[currentStep]
    val isCalendarSelectionStep = step.title == STEP_SELECT_CALENDARS

    LaunchedEffect(isCalendarSelectionStep) {
        if (isCalendarSelectionStep) {
            availableCalendars = container.calendarReader.queryCalendars()
        }
    }

    val granted = if (isCalendarSelectionStep) {
        selectedCalendarIds.isNotEmpty()
    } else {
        remember(refreshTick, currentStep) { step.isGranted(context) }
    }

    val scrollState = rememberScrollState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (isCalendarSelectionStep) Arrangement.Top else Arrangement.Center,
        ) {
            if (isCalendarSelectionStep) Spacer(Modifier.height(32.dp))

            Text(
                text = "${currentStep + 1} / ${steps.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = step.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = step.description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))

            if (isCalendarSelectionStep) {
                if (availableCalendars.isEmpty()) {
                    Text(
                        text = "No calendars found. Make sure calendar access was granted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    availableCalendars.forEach { cal ->
                        val selected = cal.id in selectedCalendarIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        val current = selectedCalendarIds.toMutableSet()
                                        if (checked) current.add(cal.id) else current.remove(cal.id)
                                        container.appPreferences.setSelectedCalendarIds(current)
                                    }
                                }
                            )
                            Column {
                                Text(cal.displayName, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = cal.accountName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            } else if (granted) {
                Text(
                    text = "Granted",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Button(
                    onClick = {
                        when (currentStep) {
                            0 -> calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                            else -> {
                                val intent = when {
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        step.title == "Notifications" ->
                                        null.also {
                                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }

                                    step.title == "Exact Alarm Permission" ->
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                                .setData(Uri.parse("package:${context.packageName}"))
                                        } else null

                                    step.title == "Do Not Disturb Access" ->
                                        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

                                    step.title == "Battery Optimization Exemption" ->
                                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                            .setData(Uri.parse("package:${context.packageName}"))

                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                                        step.title == "Full-Screen Alerts" ->
                                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                                            .setData(Uri.parse("package:${context.packageName}"))

                                    else -> null
                                }
                                intent?.let { settingsLauncher.launch(it) }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Grant")
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (!step.isCritical) {
                    OutlinedButton(onClick = { currentStep++ }) { Text("Skip") }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Button(
                    onClick = { currentStep++ },
                    enabled = granted || !step.isCritical,
                ) {
                    Text(if (currentStep == steps.size - 1) "Done" else "Next")
                }
            }
        }
    }
}
