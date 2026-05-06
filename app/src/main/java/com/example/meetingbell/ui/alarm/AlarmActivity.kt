package com.example.meetingbell.ui.alarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meetingbell.alarm.AlarmService
import com.example.meetingbell.ui.theme.MeetingBellTheme
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val title = intent.getStringExtra(AlarmService.EXTRA_TITLE) ?: "Meeting"
        val instanceId = intent.getLongExtra(AlarmService.EXTRA_INSTANCE_ID, -1L)

        setContent {
            MeetingBellTheme {
                AlarmScreen(
                    title = title,
                    onStop = {
                        // Send STOP action so AlarmService updates DB state to DISMISSED
                        val stopIntent = android.content.Intent(this@AlarmActivity, AlarmService::class.java).apply {
                            action = AlarmService.ACTION_STOP
                            putExtra(AlarmService.EXTRA_INSTANCE_ID, instanceId)
                        }
                        startService(stopIntent)
                        finish()
                    }
                )
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Prevent accidental dismiss via back gesture
    }
}

@Composable
private fun AlarmScreen(title: String, onStop: () -> Unit) {
    var tickMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tickMs = System.currentTimeMillis()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Meeting starting soon",
            color = Color(0xFFE0E0E0),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onStop,
            modifier = Modifier.size(160.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
        ) {
            Text(
                text = "Stop\nAlarm",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}
