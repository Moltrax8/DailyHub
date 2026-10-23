package com.moltrax.personalnoteapp.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.domain.model.Task
import com.moltrax.personalnoteapp.domain.util.planReminder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val CHANNEL_ID      = "task_reminders"
private const val CHANNEL_NAME    = "Task Reminders"

// Last-staged reminder diagnostics (Settings debug row + E2E assertions).
private const val DIAG_PREFS            = "notification_diag"
private const val KEY_DIAG_TASK         = "last_task_id"
private const val KEY_DIAG_FIRE_AT      = "last_fire_at"
private const val KEY_DIAG_EXACT        = "last_exact"
private const val KEY_DIAG_RECORDED_AT  = "last_recorded_at"

const val ACTION_TASK_REMINDER = "com.moltrax.personalnoteapp.ACTION_TASK_REMINDER"

/**
 * Task id (UUID string) -> stable int request/notification code mapping.
 * `String.hashCode()` can collide; instead we assign an incremental id on first use and
 * persist it in SharedPreferences — so alarms and notifications never get mixed up.
 */
object NotificationIds {
    private const val PREFS = "notification_ids"
    private const val KEY_NEXT = "next_id"

    @Synchronized
    fun stableId(context: Context, taskId: String): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getInt(keyFor(taskId), -1).takeIf { it > 0 }?.let { return it }
        var next = prefs.getInt(KEY_NEXT, 1).coerceAtLeast(1)
        // Look for a free slot against possible manual collisions (a single pass suffices in practice).
        while (prefs.all.values.any { it == next }) next++
        prefs.edit().putInt(keyFor(taskId), next).putInt(KEY_NEXT, next + 1).apply()
        return next
    }

    private fun keyFor(taskId: String) = "nid_" + Uri.encode(taskId)
}

@Singleton
class NotificationService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    init { createChannels() }

    private fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notif_channel_description)
                setShowBadge(true)
            }
            nm.createNotificationChannel(channel)
        } else {
            // Refresh description/badge on the existing channel (fields updatable without deleting the channel).
            nm.getNotificationChannel(CHANNEL_ID)?.let {
                it.description = context.getString(R.string.notif_channel_description)
                it.setShowBadge(true)
                nm.createNotificationChannel(it)
            }
        }
        // Gamification removed: clean up the legacy "System Messages" (Penalty Zone) channel.
        nm.deleteNotificationChannel("system_messages")
    }

    /** Whether exact-alarm permission is granted on Android 12+ (always true on older versions). */
    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    /** Intent directing the user to the system exact-alarm permission screen. */
    fun exactAlarmSettingsIntent(): Intent =
        Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Whether notification permission is on (including POST_NOTIFICATIONS on Android 13+). */
    fun areNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Intent to the app's system notification settings screen. */
    fun appNotificationSettingsIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun alarmIntent(taskId: String): Intent =
        Intent(context, NotificationBroadcastReceiver::class.java).apply {
            action = ACTION_TASK_REMINDER
            data = Uri.parse("personalnoteapp://reminder/${Uri.encode(taskId)}")
        }

    /**
     * Schedules the reminder alarm. Timing follows [planReminder] (single source of
     * truth, JVM-tested): fire `reminderMinutes` before the deadline, or at the
     * deadline itself when the lead window already passed.
     * Uses [setExactAndAllowWhileIdle] when exact-alarm permission is granted,
     * otherwise inexact [setAndAllowWhileIdle] (may be delayed — the UI should direct
     * the user to the permission screen via [canScheduleExactAlarms]).
     * @return true = exact alarm scheduled, false = fell back to inexact
     *   (or nothing staged when there is no usable deadline).
     */
    fun scheduleReminder(task: Task, reminderMinutes: Int = 60): Boolean {
        val plan = planReminder(task.dueDate, System.currentTimeMillis(), reminderMinutes)
            ?: return false

        // The notification text must show the ACTUAL remaining time: if the reminder window already passed
        // (fireAt = due) this is 0 ("very short" text), otherwise reminderMinutes. This way a notification
        // firing at the deadline never wrongly says "1 hour left".
        val minutesLeftAtFire = plan.minutesLeftAtFire
        val fireAt = plan.fireAt

        val notifIntent = alarmIntent(task.id).apply {
            putExtra("task_id", task.id)
            putExtra("task_title", task.title)
            putExtra("reminder_minutes", minutesLeftAtFire)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            NotificationIds.stableId(context, task.id),
            notifIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val am = context.getSystemService(AlarmManager::class.java)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
        }
        recordLastReminder(task.id, fireAt, exact)
        return exact
    }

    /**
     * Whether an alarm is currently staged for [taskId] (a matching PendingIntent
     * exists — covers both exact and inexact alarms, plus legacy hashCode ones).
     * Test hook for the reminder E2E test; also backs the Settings diagnostics row.
     */
    fun isReminderScheduled(taskId: String): Boolean {
        val pi = PendingIntent.getBroadcast(
            context,
            NotificationIds.stableId(context, taskId),
            alarmIntent(taskId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pi != null) return true
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            Intent(context, NotificationBroadcastReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) != null
    }

    /** Last alarm staged by [scheduleReminder] (for the Settings diagnostics row). */
    data class ReminderDiag(
        val taskId: String,
        val fireAt: Long,
        val exact: Boolean,
        val recordedAt: Long,
    )

    fun lastReminder(): ReminderDiag? {
        val prefs = context.getSharedPreferences(DIAG_PREFS, Context.MODE_PRIVATE)
        val taskId = prefs.getString(KEY_DIAG_TASK, null) ?: return null
        return ReminderDiag(
            taskId = taskId,
            fireAt = prefs.getLong(KEY_DIAG_FIRE_AT, 0L),
            exact = prefs.getBoolean(KEY_DIAG_EXACT, false),
            recordedAt = prefs.getLong(KEY_DIAG_RECORDED_AT, 0L),
        )
    }

    private fun recordLastReminder(taskId: String, fireAt: Long, exact: Boolean) {
        context.getSharedPreferences(DIAG_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_DIAG_TASK, taskId)
            .putLong(KEY_DIAG_FIRE_AT, fireAt)
            .putBoolean(KEY_DIAG_EXACT, exact)
            .putLong(KEY_DIAG_RECORDED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun clearLastReminder(taskId: String) {
        val prefs = context.getSharedPreferences(DIAG_PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_DIAG_TASK, null) == taskId) {
            prefs.edit().clear().apply()
        }
    }

    fun cancelReminder(taskId: String) {
        clearLastReminder(taskId)
        val pi = PendingIntent.getBroadcast(
            context,
            NotificationIds.stableId(context, taskId),
            alarmIntent(taskId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: run {
            // Also try clearing the legacy hashCode-based PendingIntent (migration).
            cancelLegacyReminder(taskId)
            return
        }
        context.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }

    /** Bulk cancel: leaves no orphaned alarms when alerts are turned off/re-scheduled. */
    fun cancelAll(taskIds: Collection<String>) {
        taskIds.forEach { cancelReminder(it) }
    }

    /** Clears alarms left over from the hashCode() era (one-time migration helper). */
    private fun cancelLegacyReminder(taskId: String) {
        runCatching {
            val legacy = PendingIntent.getBroadcast(
                context,
                taskId.hashCode(),
                Intent(context, NotificationBroadcastReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: return
            context.getSystemService(AlarmManager::class.java).cancel(legacy)
            legacy.cancel()
        }
    }
}
