package com.example.meetingbell.alarm

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AlarmLogger {
    private const val LOG_FILE = "alarm_log.txt"
    private const val MAX_LINES = 500
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(context: Context, message: String) {
        try {
            val file = File(context.filesDir, LOG_FILE)
            val timestamp = dateFormat.format(Date())
            val line = "$timestamp $message\n"
            file.appendText(line)
            trimIfNeeded(file)
        } catch (_: Exception) {
            // Never crash on logging failure
        }
    }

    fun readLog(context: Context): String {
        return try {
            File(context.filesDir, LOG_FILE).readText()
        } catch (_: Exception) {
            ""
        }
    }

    private fun trimIfNeeded(file: File) {
        val lines = file.readLines()
        if (lines.size > MAX_LINES) {
            file.writeText(lines.takeLast(MAX_LINES).joinToString("\n") + "\n")
        }
    }
}
