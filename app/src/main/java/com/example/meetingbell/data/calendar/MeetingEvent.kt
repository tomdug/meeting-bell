package com.example.meetingbell.data.calendar

data class MeetingEvent(
    val instanceId: Long,
    val eventId: Long,
    val title: String,
    val startUtc: Long,
    val endUtc: Long,
    val calendarId: Long,
    val calendarDisplayName: String,
    val calendarColor: Int,
    val isAllDay: Boolean,
    val selfAttendeeStatus: Int,
    val location: String?,
)
