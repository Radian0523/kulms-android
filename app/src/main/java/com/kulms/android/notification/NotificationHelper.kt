package com.kulms.android.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kulms.android.R
import com.kulms.android.data.model.Assignment

object NotificationHelper {
    private const val CHANNEL_ID = "kulms_deadline"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "締切リマインド",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "課題の締切通知"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
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

        for (assignment in assignments) {
            val deadline = assignment.deadline ?: continue
            if (assignment.isSubmitted || assignment.isChecked) continue

            // 24h before
            val time24h = deadline - 24 * 3600 * 1000
            if (time24h > now) {
                scheduleAlarm(
                    context,
                    id = "kulms-24h-${assignment.compositeKey}".hashCode(),
                    title = "課題の締切が近づいています",
                    body = "「${assignment.title}」（${assignment.courseName}）の締切まで24時間",
                    triggerAt = time24h
                )
            }

            // 1h before
            val time1h = deadline - 3600 * 1000
            if (time1h > now) {
                scheduleAlarm(
                    context,
                    id = "kulms-1h-${assignment.compositeKey}".hashCode(),
                    title = "課題の締切まもなく",
                    body = "「${assignment.title}」（${assignment.courseName}）の締切まで1時間",
                    triggerAt = time1h
                )
            }
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
