package com.example.meetingbell.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.meetingbell.MeetingBellApplication
import com.example.meetingbell.data.db.AlarmState
import com.example.meetingbell.ui.alarm.AlarmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date

class AlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaPlayer: MediaPlayer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val instanceId = intent?.getLongExtra(EXTRA_INSTANCE_ID, -1L) ?: -1L
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Meeting"
        val requestCode = intent?.getIntExtra(EXTRA_REQUEST_CODE, -1) ?: -1

        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "STOP action received for instanceId=$instanceId")
            AlarmLogger.log(this, "ALARM_DISMISSED via notification action instanceId=$instanceId")
            stopAlarm(instanceId, AlarmState.DISMISSED)
            return START_NOT_STICKY
        }

        Log.i(TAG, "SERVICE_START '$title' instanceId=$instanceId at ${Date()}")
        AlarmLogger.log(this, "ALARM_SERVICE_START title='$title' instanceId=$instanceId")

        createNotificationChannel()
        val notification = buildNotification(title, instanceId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        scope.launch(Dispatchers.IO) {
            (application as MeetingBellApplication).container.dao
                .updateStateByInstanceId(instanceId, AlarmState.FIRED)
        }

        scope.launch {
            val appSettings = (application as MeetingBellApplication)
                .container.appPreferences.settingsFlow
                .first()
            startAudio(appSettings.alarmSoundUri)
            delay(appSettings.maxDurationSeconds * 1000L)
            Log.i(TAG, "Auto-stop after ${appSettings.maxDurationSeconds}s for instanceId=$instanceId")
            AlarmLogger.log(this@AlarmService, "ALARM_AUTO_STOP instanceId=$instanceId")
            stopAlarm(instanceId, AlarmState.FIRED)
        }

        return START_NOT_STICKY
    }

    private fun stopAlarm(instanceId: Long, finalState: AlarmState) {
        stopAudio()
        if (instanceId != -1L) {
            scope.launch(Dispatchers.IO) {
                (application as MeetingBellApplication).container.dao.updateStateByInstanceId(
                    instanceId, finalState
                )
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startAudio(soundUri: Uri?) {
        stopAudio()
        val uri = soundUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(applicationContext, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio", e)
        }
    }

    private fun stopAudio() {
        mediaPlayer?.runCatching { stop(); release() }
        mediaPlayer = null
    }

    private fun createNotificationChannel() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(CHANNEL_ID, "Meeting Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Audible alarms for upcoming meetings"
            setBypassDnd(true)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), audioAttributes)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, instanceId: Long): Notification {
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra(EXTRA_INSTANCE_ID, instanceId)
            putExtra(EXTRA_TITLE, title)
        }
        val fullScreenPi = PendingIntent.getActivity(
            this, instanceId.toInt(), fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_STOP
            putExtra(EXTRA_INSTANCE_ID, instanceId)
        }
        val stopPi = PendingIntent.getService(
            this, instanceId.toInt() + 500_000, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Meeting starting soon")
            .setContentText(title)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(fullScreenPi, true)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudio()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "meetingbell_alarm"
        const val NOTIF_ID = 1001
        const val EXTRA_INSTANCE_ID = "event_instance_id"
        const val EXTRA_TITLE = "event_title"
        const val EXTRA_REQUEST_CODE = "alarm_request_code"
        const val ACTION_STOP = "com.example.meetingbell.ALARM_STOP"
        private const val TAG = "AlarmService"

        fun start(context: Context, instanceId: Long, title: String, requestCode: Int) {
            val intent = Intent(context, AlarmService::class.java).apply {
                putExtra(EXTRA_INSTANCE_ID, instanceId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_REQUEST_CODE, requestCode)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AlarmService::class.java))
        }
    }
}
