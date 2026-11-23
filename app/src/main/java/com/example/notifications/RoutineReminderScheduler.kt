package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.model.RoutineActivity
import com.example.util.DateTimeUtils

object RoutineReminderScheduler {

    fun syncReminder(context: Context, activity: RoutineActivity, notificationsEnabled: Boolean) {
        if (!notificationsEnabled || !activity.reminderEnabled || activity.completed || activity.id.isBlank()) {
            cancelReminder(context, activity.id)
            return
        }

        RoutineReminderReceiver.ensureChannel(context)

        val triggerMinute = (activity.startMinute - activity.reminderMinutesBefore).coerceAtLeast(0L).toInt()
        val triggerAtMillis = DateTimeUtils.triggerTimeMillis(activity.dateKey, triggerMinute)
        if (triggerAtMillis <= System.currentTimeMillis()) {
            cancelReminder(context, activity.id)
            return
        }

        val requestCode = activity.id.hashCode()
        val intent = Intent(context, RoutineReminderReceiver::class.java).apply {
            putExtra(RoutineReminderReceiver.EXTRA_TITLE, activity.title)
            putExtra(
                RoutineReminderReceiver.EXTRA_TIME_RANGE,
                "Scheduled ${DateTimeUtils.formatTimeRange(activity.startMinute, activity.endMinute)}"
            )
            putExtra(RoutineReminderReceiver.EXTRA_NOTIFICATION_ID, requestCode)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    fun cancelReminder(context: Context, activityId: String) {
        if (activityId.isBlank()) return
        val requestCode = activityId.hashCode()
        val intent = Intent(context, RoutineReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
