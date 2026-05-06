# MeetingBell 🔔

An Android alarm app that fires an audible alert before your calendar meetings, even when the phone is on silent, DND, or Doze mode. The name was chosen by an AI with no further comment.

## Origin

This app directly implements the solution to the problem described in this post by Jeff Kaufman:

---

### [Alarming Scheduling](https://www.jefftk.com/p/alarming-scheduling)

*May 4th, 2026 · [tech](https://www.jefftk.com/news/tech)*

Each morning I look over my work calendar and make a series of verbal requests:

> Set a timer for 9:59
> Set a timer for 10:59
> Set a timer for 11:29
> Set a timer for 1:29
> Set a timer for 2:29

Why?

- I do not want to miss any meetings.
- I will miss occasional meetings if I'm not notified.
- I want to keep my phone on silent.
- I don't reliably notice my phone vibrating.
- While I do notice a smartwatch vibrating, I [can't wear one](https://www.jefftk.com/p/moto-360-review).

This means I want my phone to make noise before each meeting, while otherwise remaining silent. I put in a bunch of time trying to figure out a better way, learning about the automation options for Android and trying several, and didn't find anything that worked. Even the ones that seemed like they should have worked (MacroDroid seemed pretty promising) just failed to make noise at the right time. So I just set my timers.

On the other hand, it's not a total waste: looking over my schedule and noticing how my meetings fit together and where I have free time is still a good thing to do. But I still wish I could automate this.

---

## How it was built

[SPEC.md](SPEC.md) contains the prompt given to Claude Code. The app was generated in a single shot from that spec, then iteratively fixed using Android Studio's Gemini Agent and Claude Code until the code compiled and the app stopped crashing. No manual code was written — including this README.


## Features

- Reads Google Calendar (and any other Android calendar) via `CalendarContract`
- Fires alarms using `AlarmManager.setAlarmClock()` — the only API that unconditionally bypasses Doze
- Plays audio through silent/DND mode via `AudioAttributes.USAGE_ALARM`
- Displays a full-screen alert over the lock screen
- Survives force-stop and device reboots
- Configurable lead time, calendar selection, alarm sound, and auto-stop duration

## Requirements

- Android 10+ (minSdk 29)
- Calendar permission, exact alarm permission, DND access, and battery optimisation exemption (the onboarding wizard walks through each one)
