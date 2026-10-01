package com.moltrax.personalnoteapp.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.domain.repository.TaskRepository
import com.moltrax.personalnoteapp.service.NotificationService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class RescheduleNotificationsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val taskRepo: TaskRepository,
    private val notifService: NotificationService,
    private val prefs: AppPreferences,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val all = taskRepo.getAll()
        // If system alerts are off, schedule no reminders — clear any previously scheduled ones.
        if (!prefs.systemAlertsEnabled.first()) {
            notifService.cancelAll(all.map { it.id })
            return Result.success()
        }
        val now = System.currentTimeMillis()
        val minutes = prefs.reminderMinutes.first()
        val actives = all.filter { !it.isDone && it.dueDate != null && it.dueDate > now }
        val activeIds = actives.map { it.id }.toSet()
        // Leave no orphaned alarms: cancel PendingIntents of done/past/hidden tasks.
        notifService.cancelAll(all.map { it.id }.filter { it !in activeIds })
        actives.forEach { notifService.scheduleReminder(it, minutes) }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "reschedule_notifications"
    }
}
