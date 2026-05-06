# Meeting Alarm Android App — Scope & Implementation Plan

## Goal
Build an Android app that automatically rings audible alarms before calendar meetings, removing the need to manually set timers each morning. The alarm must reliably make noise even when the phone is on silent / DND / Doze / battery-optimised, and even when the app has been killed.

## Why this is non-trivial
The user has tried existing automation tools (MacroDroid and others). They installed correctly but failed to actually produce sound at the scheduled time. The reliability of the alarm trigger — not the UI — is the core engineering problem. Treat this as reliability-first; design every other decision around it.

---

## Core requirements (MVP)

### Functional
- Read events from user-selected device calendars (Google Calendar, Outlook, etc. — anything that syncs through the system Calendar Provider).
- Show next ~24–48h of upcoming meetings on the home screen.
- Schedule an audible alarm at `meeting_start − lead_time` for each meeting.
- Default lead time: **1 minute**, globally configurable.
- The alarm MUST:
    - Make noise with ringer on silent.
    - Make noise in DND mode.
    - Fire while app is backgrounded or killed.
    - Fire after device reboot (if it was already scheduled).
    - Survive Doze and battery optimisation.
- React to calendar changes (created/moved/cancelled events) within a sync cycle.
- Provide a clear stop/dismiss action when the alarm fires.

### Settings
- Calendar selection (multi-select).
- Lead time in minutes.
- Skip declined events (toggle, default ON).
- Skip all-day events (toggle, default ON).
- Alarm sound (default = system alarm tone).
- Max alarm duration before auto-stop (default 60s).
- "Test alarm" button.

---

## Tech stack
- Kotlin
- Jetpack Compose
- Min SDK 29 (Android 10), Target SDK 35
- Persistence: DataStore (settings); Room single-table (scheduled-alarm tracking for diffing & cancellation)
- Background: WorkManager (periodic sync), AlarmManager (firing)
- Architecture: simple MVVM, single-activity, Compose Navigation

---

## Critical reliability decisions — READ CAREFULLY

### 1. Use `AlarmManager.setAlarmClock()` — NOT `setExactAndAllowWhileIdle()`
Per Android docs, `setAlarmClock()` is the only scheduling API that:
- Bypasses Doze unconditionally
- Is exempt from battery optimisation
- Is treated as user-visible (shows the lock-screen alarm icon)

`setExactAndAllowWhileIdle()` is throttled in Doze and is almost certainly why competing apps fail. Do not use it.

### 2. Audio routing — play on `STREAM_ALARM`
Use `AudioAttributes` with `USAGE_ALARM` + `CONTENT_TYPE_SONIFICATION`. The alarm stream plays at alarm volume regardless of ringer mode. Verify on real device with phone set to silent.

### 3. Notification channel
Create one channel with:
- `IMPORTANCE_HIGH`
- Category `CATEGORY_ALARM`
- `setBypassDnd(true)` (requires `ACCESS_NOTIFICATION_POLICY` user grant)
- `setSound(uri, AudioAttributes(USAGE_ALARM))`
- Full-screen intent enabled

### 4. Trigger flow when an alarm fires
1. Manifest-registered `BroadcastReceiver.onReceive()` (exported=false)
2. Acquire short partial wake lock
3. Start a foreground service (`FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`, or `SPECIAL_USE` on 14+)
4. Service plays alarm via `MediaPlayer` on `STREAM_ALARM` AND posts a full-screen-intent notification
5. Full-screen intent launches `AlarmActivity` (big "Stop" button, meeting title, time-until)
6. Auto-stop after `max_duration` to avoid runaway audio

### 5. Survive boot
`RECEIVE_BOOT_COMPLETED` receiver → enqueue one-time WorkManager sync → reschedules all upcoming alarms from calendar.

---

## Permissions
- `READ_CALENDAR`
- `POST_NOTIFICATIONS` (13+)
- `SCHEDULE_EXACT_ALARM` (12+) — request via settings intent
- `RECEIVE_BOOT_COMPLETED`
- `WAKE_LOCK`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- `ACCESS_NOTIFICATION_POLICY` (DND bypass)

Onboarding wizard walks through:
1. Calendar permission
2. Notification permission
3. Exact alarm permission
4. DND access
5. Battery optimisation exemption (`Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
6. Calendar selection

A persistent **Reliability Check** panel on Home shows which of these are granted. The app refuses to "arm" if any critical ones are missing, and shows an actionable banner.

---

## Data model

### Calendar reader
Query `CalendarContract.Instances` (NOT `Events` directly — Instances correctly expand recurrences) for `[now, now + 7d]`. Filter by selected `calendar_id`s. Projection: `_id`, `event_id`, `title`, `begin`, `end`, `all_day`, `selfAttendeeStatus`, `eventLocation`.

### Room table `scheduled_alarm`
| Column | Type | Notes |
|---|---|---|
| `id` | Long PK auto | |
| `event_instance_id` | Long, unique | from `CalendarContract.Instances._ID` |
| `event_title` | String | |
| `meeting_start_utc` | Long ms | |
| `alarm_fire_utc` | Long ms | |
| `request_code` | Int unique | for `PendingIntent` cancel |
| `state` | Enum | SCHEDULED / FIRED / DISMISSED / CANCELLED |

Used for diff-on-resync: cancel removed, schedule new, leave unchanged untouched.

---

## Sync strategy
- On app open
- WorkManager periodic job every 15 min (cheap — just a calendar query)
- `ContentObserver` on `CalendarContract.Instances` while app is foreground
- On boot
- After every sync, diff against `scheduled_alarm`, cancel obsolete `PendingIntent`s, schedule new ones, update DB

---

## UI

1. **Home** — list of upcoming meetings (next 24h), each row: title, start time, calendar colour, computed alarm time, "mute this one" toggle. Top banner showing reliability state (all-clear / missing permissions / warnings).
2. **Settings** — lead time, calendars, filters, sound, max duration, Test Alarm button.
3. **Onboarding** — first-run permissions wizard.
4. **AlarmActivity** — full-screen-intent activity. Big Stop button, meeting title, "starts in 1 min".

---

## Acceptance criteria (must all pass on real device)
1. Phone silent + DND on + screen off → alarm is audible at scheduled time.
2. App force-stopped from recents → next scheduled alarm still fires.
3. Phone rebooted 5 min before meeting → alarm still fires.
4. Phone idle for hours (Doze) → next alarm fires within 1–2 s of scheduled time.
5. Meeting moved 15 min later in Google Calendar → alarm reschedules within next sync.
6. Meeting deleted → its scheduled alarm is cancelled.
7. Two back-to-back meetings → both alarms fire.
8. User dismisses alarm → no further sound for that event; later events unaffected.
9. Test Alarm produces a real alarm matching production behaviour.

---

## Out of scope (v1)
- Per-meeting custom lead times
- Snooze
- Different lead times per calendar
- Cross-device sync
- Wear OS companion
- iOS

---

## Suggested project structure
```
app/src/main/
  java/<pkg>/
    MainActivity.kt
    ui/
      home/
      settings/
      onboarding/
      alarm/                  # full-screen alarm activity
    data/
      calendar/               # CalendarContract reader
      db/                     # Room: ScheduledAlarmDao, AppDatabase
      prefs/                  # DataStore
    domain/
      SyncMeetings.kt
      ScheduleAlarms.kt
      DiffAlarms.kt
    alarm/
      AlarmReceiver.kt        # triggered by AlarmManager
      AlarmService.kt         # foreground service, plays sound
      BootReceiver.kt
    worker/
      SyncWorker.kt
  AndroidManifest.xml
  res/
```

---

## Recommended build order
1. Scaffold project, manifest, permissions, single Compose activity.
2. Calendar provider read + Home list (no alarms yet).
3. Settings: calendar picker + lead time.
4. Schedule alarms via `setAlarmClock`; receiver that just logs.
5. Foreground service + notification channel + alarm-stream playback.
6. AlarmActivity full-screen with Stop.
7. Sync worker + diffing + reschedule on changes.
8. Boot receiver.
9. Onboarding wizard + reliability check panel.
10. Test Alarm button.
11. Run full acceptance matrix on real hardware (ideally one Pixel + one Samsung — Samsung's battery management is notoriously aggressive).

---

## Notes for the coding agent
- Verify alarm reliability on a Samsung device specifically. One Plus and Samsung have additional non-standard battery optimisation layers; the app may need to direct the user to OEM-specific settings (e.g. `com.samsung.android.lool` for "Never sleeping apps").
- Do not write your own scheduler abstraction over `AlarmManager`. Call it directly. The Android docs on this are the source of truth.
- The full-screen-intent path on Android 14+ requires the `USE_FULL_SCREEN_INTENT` permission; on Android 14 some uses now require user opt-in via Settings. Design the onboarding to surface this.
- Log every alarm scheduling/firing/cancellation with timestamps to local file or Logcat — when reliability bugs surface (and they will), you need a trace.