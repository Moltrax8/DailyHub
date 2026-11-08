package com.moltrax.personalnoteapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.moltrax.personalnoteapp.R

internal const val REMINDER_CHANNEL_ID = "task_reminders"
private const val REMINDER_CHANNEL_NAME = "Task Reminders"

/** Callers retain their own locale, existing-channel policy, and legacy-channel cleanup. */
internal fun ensureReminderChannel(context: Context, manager: NotificationManager, refreshExisting: Boolean) {
    if (manager.getNotificationChannel(REMINDER_CHANNEL_ID) == null) {
        manager.createNotificationChannel(
            NotificationChannel(REMINDER_CHANNEL_ID, REMINDER_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.notif_channel_description)
                setShowBadge(true)
            },
        )
    } else if (refreshExisting) {
        manager.getNotificationChannel(REMINDER_CHANNEL_ID)?.let {
            it.description = context.getString(R.string.notif_channel_description)
            it.setShowBadge(true)
            manager.createNotificationChannel(it)
        }
    }
}
