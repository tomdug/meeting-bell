# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew assembleDebug          # build debug APK
./gradlew assembleRelease        # build release APK
./gradlew installDebug           # build and install on connected device
./gradlew test                   # run unit tests
./gradlew connectedAndroidTest   # run instrumented tests on device
./gradlew lint                   # run lint checks
```

Testing boot receiver manually:
```bash
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.example.meetingbell
```

## Architecture

Single-module Android app (minSdk 29, targetSdk 36), Kotlin + Jetpack Compose, no DI framework — dependencies are wired manually via `AppContainer`.

**Dependency graph:** `MeetingBellApplication` owns a singleton `AppContainer` which lazily initialises all dependencies (Room `AppDatabase`, `ScheduledAlarmDao`, `AppPreferences`, `CalendarReader`, `DiffAlarms`, `ScheduleAlarms`, `SyncMeetings`). ViewModels receive what they need from `AppContainer` via manual `ViewModelProvider.Factory`.

**Alarm trigger chain (reliability-critical):**
```
AlarmManager.setAlarmClock()  ← only API that bypasses Doze unconditionally
  → AlarmReceiver.onReceive(): acquire PARTIAL_WAKE_LOCK (10s), startForegroundService
    → AlarmService.onStartCommand(): startForeground, MediaPlayer(USAGE_ALARM), full-screen notification
      → AlarmActivity (over lock screen) or heads-up notification
        → user taps Stop → ACTION_STOP intent → AlarmService updates DB, stopSelf()
```

**Never use `setExactAndAllowWhileIdle`** — it is throttled in Doze. Always use `setAlarmClock`.

**Calendar sync cycle:**
- `SyncWorker` (WorkManager, periodic 15 min) calls `SyncMeetings.execute()`
- `MainActivity.onResume` registers a `ContentObserver` on `CalendarContract.Instances.CONTENT_URI` and enqueues a one-time sync — only if `READ_CALENDAR` is granted
- `BootReceiver` enqueues an expedited one-time `SyncWorker` on boot (AlarmManager state is wiped on reboot)
- `SyncMeetings` → `CalendarReader.queryInstances()` (uses `Instances`, not `Events`, to correctly expand recurrences) → `DiffAlarms.execute()` → `ScheduleAlarms.schedule/cancel()`

**`DiffAlarms`** computes a three-way diff (toSchedule / toCancel / unchanged) between the current DB state and a fresh calendar query. Handles new events, deleted events, and rescheduled events (fire time changed). Filters out alarms whose fire time is already in the past.

**Data persistence:**
- Room (`scheduled_alarm` table) — tracks every alarm with its state (SCHEDULED / FIRED / DISMISSED / CANCELLED) and the `requestCode` used to cancel its `PendingIntent`
- DataStore (`settings`) — selected calendar IDs, lead time, filters, alarm sound URI, max duration, onboarding complete flag

**Navigation:** Single `MainActivity` with a Compose `NavHost` (routes: `onboarding`, `home`, `settings`). `AlarmActivity` is a separate activity (not in the nav graph) that appears over the lock screen. The app reads `onboardingComplete` from DataStore before rendering the NavHost; a `CircularProgressIndicator` is shown while that read is in flight.

**Onboarding:** Step-by-step permission wizard ending with a calendar selection step. `OnboardingScreen` takes the full `AppContainer` (needs both `AppPreferences` and `CalendarReader`). On completion it calls `enqueueSync()` immediately so the home screen populates without needing to background/foreground the app.

**Reliability banner:** `HomeViewModel.buildReliabilityState()` checks all 7 conditions (calendar permission, notification permission, exact alarm, DND access, battery exemption, full-screen intent, ≥1 calendar selected). `HomeScreen` refreshes this on every `ON_RESUME` lifecycle event via `DisposableEffect`.

**`AlarmLogger`** appends timestamped lines to `filesDir/alarm_log.txt` (rotates at 500 lines). Every schedule/cancel/fire/dismiss/sync event is logged here — useful for debugging reliability issues on real devices.

## Key Constraints

- `CalendarReader` guards both `queryCalendars()` and `queryInstances()` with a permission check and returns empty lists rather than throwing — callers at the ViewModel/domain layer must not assume the list is populated.
- `SettingsScreen` calls `viewModel.refreshCalendars()` on `ON_RESUME` (not just at ViewModel init) because the ViewModel may be alive before calendar permission is granted.
- Audio must use `AudioAttributes.USAGE_ALARM` to play through silent/DND mode.
- The notification channel must have `setBypassDnd(true)` and `IMPORTANCE_HIGH`.
- On API 34+ (`UPSIDE_DOWN_CAKE`), `startForeground` requires the `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` overload.
- `PendingIntent` flags must include `FLAG_IMMUTABLE` (required API 31+).
