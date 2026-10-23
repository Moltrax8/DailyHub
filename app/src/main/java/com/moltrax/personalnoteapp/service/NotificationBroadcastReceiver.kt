package com.moltrax.personalnoteapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.moltrax.personalnoteapp.MainActivity
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.ui.i18n.localizedFor
import com.moltrax.personalnoteapp.widget.TaskWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NotificationBroadcastReceiver : BroadcastReceiver() {
    companion object {
        // Shared scope for the process lifetime: DataStore reads never block the main thread (no ANR).
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId    = intent.getStringExtra("task_id") ?: return
        val title     = intent.getStringExtra("task_title") ?: context.getString(R.string.notif_untitled_task)
        val minutes   = intent.getIntExtra("reminder_minutes", 60)
        val pending = goAsync()
        scope.launch {
            try {
                showNotification(context, taskId, title, minutes)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun showNotification(context: Context, taskId: String, title: String, minutes: Int) {
        // Localize to the selected language (one-shot short DataStore read — on a background thread).
        val lang = runCatching {
            TaskWidget.entryPoint(context).appPreferences().language.first()
        }.getOrDefault("en")
        val ctx = context.localizedFor(lang)

        ensureChannel(ctx)

        val tapIntent = Intent(ctx, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("personalnoteapp://reminder_tap/${Uri.encode(taskId)}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, NotificationIds.stableId(ctx, taskId), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Standard reminder text based on the time left until the deadline.
        val timeLeft = when {
            minutes <= 0   -> ctx.getString(R.string.notif_time_very_short)
            minutes < 60   -> ctx.getString(R.string.notif_time_minutes, minutes)
            minutes == 60  -> ctx.getString(R.string.notif_time_one_hour)
            else           -> ctx.getString(R.string.notif_time_hours, minutes / 60)
        }
        val body = ctx.getString(R.string.notif_body, title, timeLeft)

        val notif = NotificationCompat.Builder(ctx, "task_reminders")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.notif_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // Tag-based notification: task id is the collision-free key, no int truncation.
        // Also clear the legacy hashCode-based notification (migration).
        val nm = ctx.getSystemService(NotificationManager::class.java)
        runCatching { nm.cancel(taskId.hashCode()) }
        nm.notify(taskId, 0, notif)
    }

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel("task_reminders") == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    "task_reminders",
                    "Task Reminders",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = ctx.getString(R.string.notif_channel_description)
                    setShowBadge(true)
                },
            )
        }
    }
}
