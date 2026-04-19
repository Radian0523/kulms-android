package com.radian0523.kulms_plus_for_android.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.radian0523.kulms_plus_for_android.R
import com.radian0523.kulms_plus_for_android.data.model.Assignment

object NotificationHelper {
    private const val CHANNEL_ID = "kulms_deadline"
    private const val PREFS_NAME = "kulms_settings"
    private const val OFFSETS_KEY = "notificationOffsets"
    private val DEFAULT_OFFSETS = setOf("1440", "60") // 24h, 1h (minutes)

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    // MARK: - Notification Offsets

    fun getNotificationOffsets(context: Context): List<Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(OFFSETS_KEY, null) ?: return DEFAULT_OFFSETS.map { it.toInt() }.sortedDescending()
        val offsets = set.mapNotNull { it.toIntOrNull() }
        return if (offsets.isEmpty()) DEFAULT_OFFSETS.map { it.toInt() }.sortedDescending() else offsets.sortedDescending()
    }

    fun setNotificationOffsets(context: Context, offsets: List<Int>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(OFFSETS_KEY, offsets.map { it.toString() }.toSet()).apply()
    }

    fun formatOffsetLabel(minutes: Int, context: Context? = null): String {
        return when {
            minutes >= 1440 && minutes % 1440 == 0 -> context?.getString(R.string.offset_days_before, minutes / 1440) ?: "${minutes / 1440}日前"
            minutes >= 60 && minutes % 60 == 0 -> context?.getString(R.string.offset_hours_before, minutes / 60) ?: "${minutes / 60}時間前"
            else -> context?.getString(R.string.offset_mins_before, minutes) ?: "${minutes}分前"
        }
    }

    private fun notificationContent(context: Context, assignment: Assignment, offsetMinutes: Int): Pair<String, String> {
        val label = formatOffsetLabel(offsetMinutes, context).let {
            if (it.endsWith("前")) it.dropLast(1) else it
        }
        val title = if (offsetMinutes <= 60) context.getString(R.string.notif_title_soon) else context.getString(R.string.notif_title_approaching)
        val body = context.getString(R.string.notif_body, assignment.title, assignment.courseName, label)
        return title to body
    }

    fun scheduleNotifications(context: Context, assignments: List<Assignment>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val manager = NotificationManagerCompat.from(context)
        manager.cancelAll()

        val now = System.currentTimeMillis()
        val offsets = getNotificationOffsets(context)

        data class Candidate(val id: Int, val title: String, val body: String, val triggerAt: Long)

        val candidates = mutableListOf<Candidate>()

        for (assignment in assignments) {
            val deadline = assignment.deadline ?: continue
            if (assignment.isSubmitted || assignment.isChecked) continue

            for (offset in offsets) {
                val triggerAt = deadline - offset.toLong() * 60 * 1000
                if (triggerAt <= now) continue

                val (title, body) = notificationContent(context, assignment, offset)
                candidates.add(Candidate(
                    id = "kulms-${offset}m-${assignment.compositeKey}".hashCode(),
                    title = title,
                    body = body,
                    triggerAt = triggerAt
                ))
            }
        }

        // Sort by trigger time (nearest first)
        candidates.sortBy { it.triggerAt }

        for (candidate in candidates) {
            scheduleAlarm(context, candidate.id, candidate.title, candidate.body, candidate.triggerAt)
        }
    }

    private fun scheduleAlarm(
        context: Context,
        id: Int,
        title: String,
        body: String,
        triggerAt: Long
    ) {
        // Use AlarmManager for precise scheduling
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(context, NotificationReceiver::class.java).apply {
            putExtra("id", id)
            putExtra("title", title)
            putExtra("body", body)
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context, id, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent
            )
        } catch (_: SecurityException) {
            // Exact alarm permission not granted, fall back to inexact
            alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun showNotification(context: Context, id: Int, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
