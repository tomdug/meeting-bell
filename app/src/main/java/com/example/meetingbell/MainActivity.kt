package com.example.meetingbell

import android.Manifest
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.meetingbell.ui.home.HomeScreen
import com.example.meetingbell.ui.home.HomeViewModel
import com.example.meetingbell.ui.onboarding.OnboardingScreen
import com.example.meetingbell.ui.settings.SettingsScreen
import com.example.meetingbell.ui.settings.SettingsViewModel
import com.example.meetingbell.ui.theme.MeetingBellTheme
import com.example.meetingbell.worker.SyncWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var calendarObserver: ContentObserver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MeetingBellApplication
        val container = app.container

        setContent {
            MeetingBellTheme {
                val scope = rememberCoroutineScope()
                var startRoute by remember { mutableStateOf<String?>(null) }

                if (startRoute == null) {
                    scope.launch {
                        val prefs = container.appPreferences.settingsFlow.first()
                        startRoute = if (prefs.onboardingComplete) "home" else "onboarding"
                    }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    return@MeetingBellTheme
                }

                val navController = rememberNavController()
                val homeVm: HomeViewModel = viewModel(
                    factory = HomeViewModel.factory(container, app)
                )
                val settingsVm: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.factory(container, app)
                )

                NavHost(navController = navController, startDestination = startRoute!!) {
                    composable("onboarding") {
                        OnboardingScreen(
                            container = container,
                            onComplete = {
                                navController.navigate("home") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                                enqueueSync()
                            }
                        )
                    }
                    composable("home") {
                        HomeScreen(
                            viewModel = homeVm,
                            onOpenSettings = { navController.navigate("settings") },
                            onOpenOnboarding = { navController.navigate("onboarding") },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            viewModel = settingsVm,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) return

        calendarObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enqueueSync()
            }
        }
        contentResolver.registerContentObserver(
            CalendarContract.Instances.CONTENT_URI,
            true,
            calendarObserver!!,
        )
        enqueueSync()
    }

    override fun onPause() {
        super.onPause()
        calendarObserver?.let { contentResolver.unregisterContentObserver(it) }
        calendarObserver = null
    }

    private fun enqueueSync() {
        WorkManager.getInstance(this).enqueue(
            OneTimeWorkRequestBuilder<SyncWorker>().build()
        )
    }
}
