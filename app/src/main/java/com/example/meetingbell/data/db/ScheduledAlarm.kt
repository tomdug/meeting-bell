package com.example.meetingbell.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scheduled_alarm",
    indices = [
        Index(value = ["event_instance_id"], unique = true),
        Index(value = ["request_code"], unique = true),
    ]
)
data class ScheduledAlarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "event_instance_id") val eventInstanceId: Long,
    @ColumnInfo(name = "event_title") val eventTitle: String,
    @ColumnInfo(name = "meeting_start_utc") val meetingStartUtc: Long,
    @ColumnInfo(name = "alarm_fire_utc") val alarmFireUtc: Long,
    @ColumnInfo(name = "request_code") val requestCode: Int,
    @ColumnInfo(name = "state") val state: AlarmState = AlarmState.SCHEDULED,
    @ColumnInfo(name = "calendar_id") val calendarId: Long,
)

enum class AlarmState { SCHEDULED, FIRED, DISMISSED, CANCELLED }
