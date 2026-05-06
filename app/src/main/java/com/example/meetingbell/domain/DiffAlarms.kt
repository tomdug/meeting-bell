package com.example.meetingbell.domain

import com.example.meetingbell.data.calendar.MeetingEvent
import com.example.meetingbell.data.db.ScheduledAlarm
import com.example.meetingbell.data.db.ScheduledAlarmDao

data class DiffResult(
    val toSchedule: List<MeetingEvent>,
    val toCancel: List<ScheduledAlarm>,
    val unchanged: List<ScheduledAlarm>,
)

class DiffAlarms(private val dao: ScheduledAlarmDao) {

    suspend fun execute(freshEvents: List<MeetingEvent>, leadMinutes: Int): DiffResult {
        val now = System.currentTimeMillis()
        val existing = dao.getAllScheduled()
        val existingByInstanceId = existing.associateBy { it.eventInstanceId }
        val freshIds = freshEvents.map { it.instanceId }.toSet()

        val toCancel = mutableListOf<ScheduledAlarm>()
        val toSchedule = mutableListOf<MeetingEvent>()
        val unchanged = mutableListOf<ScheduledAlarm>()

        // Existing alarms whose event no longer appears in the fresh query
        for (alarm in existing) {
            if (alarm.eventInstanceId !in freshIds) {
                toCancel.add(alarm)
            }
        }

        for (event in freshEvents) {
            val expectedFireUtc = event.startUtc - leadMinutes * 60_000L
            val existing = existingByInstanceId[event.instanceId]
            when {
                existing == null -> {
                    // New event — schedule if alarm is still in the future
                    if (expectedFireUtc > now) toSchedule.add(event)
                }
                existing.alarmFireUtc != expectedFireUtc -> {
                    // Event rescheduled or lead time changed — cancel old, schedule new
                    toCancel.add(existing)
                    if (expectedFireUtc > now) toSchedule.add(event)
                }
                else -> unchanged.add(existing)
            }
        }

        return DiffResult(toSchedule, toCancel, unchanged)
    }
}
