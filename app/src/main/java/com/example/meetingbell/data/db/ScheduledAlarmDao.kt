package com.example.meetingbell.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledAlarmDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alarm: ScheduledAlarm)

    @Query("SELECT * FROM scheduled_alarm WHERE state = 'SCHEDULED'")
    suspend fun getAllScheduled(): List<ScheduledAlarm>

    @Query("SELECT * FROM scheduled_alarm WHERE event_instance_id = :eventInstanceId LIMIT 1")
    suspend fun getByInstanceId(eventInstanceId: Long): ScheduledAlarm?

    @Query("UPDATE scheduled_alarm SET state = :state WHERE id = :id")
    suspend fun updateState(id: Long, state: AlarmState)

    @Query("UPDATE scheduled_alarm SET state = :state WHERE event_instance_id = :eventInstanceId")
    suspend fun updateStateByInstanceId(eventInstanceId: Long, state: AlarmState)

    @Query("DELETE FROM scheduled_alarm WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM scheduled_alarm WHERE event_instance_id = :eventInstanceId")
    suspend fun deleteByInstanceId(eventInstanceId: Long)

    @Query(
        "SELECT * FROM scheduled_alarm WHERE state IN ('SCHEDULED', 'CANCELLED') AND meeting_start_utc > :now " +
            "ORDER BY alarm_fire_utc ASC"
    )
    fun observeUpcoming(now: Long): Flow<List<ScheduledAlarm>>

    @Query("SELECT COALESCE(MAX(request_code), 1000) + 1 FROM scheduled_alarm")
    suspend fun getNextRequestCode(): Int
}
