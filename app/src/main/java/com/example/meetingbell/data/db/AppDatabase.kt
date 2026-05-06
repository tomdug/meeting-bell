package com.example.meetingbell.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ScheduledAlarm::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scheduledAlarmDao(): ScheduledAlarmDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "meetingbell.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
