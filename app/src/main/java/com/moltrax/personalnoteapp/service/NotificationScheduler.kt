package com.moltrax.personalnoteapp.service

import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.domain.model.Task
import com.moltrax.personalnoteapp.domain.util.planReminder
import com.moltrax.personalnoteapp.domain.util.shouldStageReminder
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single choke point for per-task reminder alarms (v2 Phase 1.1).
 *
 * Every task mutation funnels through [TaskRepositoryImpl], which calls here —
 * so no writer must remember the cancel/schedule dance (and its `isDone` /
 * `isDeleted` / alerts-off edge cases) ever again. Bulk paths (cold start,
 * reboot, settings toggle, Drive pull) keep using [RescheduleNotificationsWorker].
 */
@Singleton
class NotificationScheduler @Inject constructor(
    private val notifService: NotificationService,
    private val prefs: AppPreferences,
) {
    /**
     * Stages the task's alarm when it deserves one, cancels any staged alarm
     * otherwise (done / deleted / dateless / past-due / alerts off).
     * Task due 18:00 → notify 17:00 with a clear "due in 1 hour" message.
     */
    suspend fun refresh(task: Task) {
        val alerts = prefs.systemAlertsEnabled.first()
        val minutes = prefs.reminderMinutes.first()
        val plan = if (shouldStageReminder(task.isDeleted, task.isDone, task.dueDate, alerts)) {
            planReminder(task.dueDate, System.currentTimeMillis(), minutes)
        } else {
            null
        }
        if (plan == null) {
            notifService.cancelReminder(task.id)
        } else {
            notifService.scheduleReminder(task, minutes)
        }
    }

    /** Cancels any staged alarm for [taskId] (e.g. after a tombstone delete). */
    suspend fun cancel(taskId: String) = notifService.cancelReminder(taskId)
}
