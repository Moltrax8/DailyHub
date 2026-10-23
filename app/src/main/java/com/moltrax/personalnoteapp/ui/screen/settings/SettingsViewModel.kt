package com.moltrax.personalnoteapp.ui.screen.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.data.repository.UpdateRepository
import com.moltrax.personalnoteapp.domain.model.AppRelease
import com.moltrax.personalnoteapp.domain.repository.TaskRepository
import com.moltrax.personalnoteapp.service.NotificationService
import com.moltrax.personalnoteapp.worker.RescheduleNotificationsWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Manual "Check for updates" result (About section; read-only, no behavior change). */
sealed interface UpdateCheck {
    data object Unavailable : UpdateCheck
    data object UpToDate : UpdateCheck
    data class Available(val release: AppRelease) : UpdateCheck
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    @ApplicationContext private val appContext: Context,
    private val taskRepo: TaskRepository,
    private val notifService: NotificationService,
    private val updates: UpdateRepository,
) : ViewModel() {

    val language = prefs.language.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "en")
    val reminderMinutes = prefs.reminderMinutes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 60)
    val systemAlertsEnabled = prefs.systemAlertsEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val autoUpdate = prefs.autoUpdate.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setAutoUpdate(v: Boolean) = viewModelScope.launch { prefs.setAutoUpdate(v) }

    fun setReminderMinutes(m: Int) = viewModelScope.launch {
        prefs.setReminderMinutes(m)
        // Deduplicated reschedule work so the new lead-time applies to existing alarms.
        enqueueReschedule()
    }

    fun setSystemAlertsEnabled(v: Boolean) = viewModelScope.launch {
        prefs.setSystemAlertsEnabled(v)
        if (!v) {
            // When turning off, cancel staged alarms immediately (a DataStore write alone is not enough).
            notifService.cancelAll(taskRepo.getAll().map { it.id })
        } else {
            enqueueReschedule()
        }
    }

    /** Whether the exact-alarm permission is granted (otherwise reminders fall back to inexact). */
    fun canScheduleExactAlarms(): Boolean = notifService.canScheduleExactAlarms()

    /** Whether notifications are enabled (including POST_NOTIFICATIONS). */
    fun areNotificationsEnabled(): Boolean = notifService.areNotificationsEnabled()

    /** Opens the system's exact-alarm permission screen. */
    fun openExactAlarmSettings() {
        runCatching { appContext.startActivity(notifService.exactAlarmSettingsIntent()) }
    }

    /** Opens the app's system notification settings screen. */
    fun openNotificationSettings() {
        runCatching { appContext.startActivity(notifService.appNotificationSettingsIntent()) }
    }

    private fun enqueueReschedule() {
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            RescheduleNotificationsWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RescheduleNotificationsWorker>().build(),
        )
    }

    /** One-shot manual update check for the About section. */
    suspend fun checkForUpdatesNow(): UpdateCheck {
        val release = runCatching { updates.checkNow() }.getOrNull()
            ?: return UpdateCheck.Unavailable
        return if (updates.isNewerThanInstalled(release)) UpdateCheck.Available(release)
        else UpdateCheck.UpToDate
    }
}
