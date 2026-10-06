package com.moltrax.personalnoteapp.ui.screen.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.data.remote.drive.DriveAuthService
import com.moltrax.personalnoteapp.data.repository.UpdateRepository
import com.moltrax.personalnoteapp.domain.model.AppRelease
import com.moltrax.personalnoteapp.domain.repository.SyncRepository
import com.moltrax.personalnoteapp.domain.repository.TaskRepository
import com.moltrax.personalnoteapp.domain.util.BirthdayUtils
import com.moltrax.personalnoteapp.service.NotificationService
import com.moltrax.personalnoteapp.worker.RescheduleNotificationsWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
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
    private val authService: DriveAuthService,
    private val syncRepo: SyncRepository,
    @ApplicationContext private val appContext: Context,
    private val taskRepo: TaskRepository,
    private val notifService: NotificationService,
    private val updates: UpdateRepository,
) : ViewModel() {

    val language       = prefs.language.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "en")
    val reminderMinutes = prefs.reminderMinutes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 60)
    val systemAlertsEnabled = prefs.systemAlertsEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val lastSyncAt     = prefs.lastSyncAt.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    // Automatic update checks (Phase 9, opt-in, default OFF).
    val autoUpdate     = prefs.autoUpdate.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setAutoUpdate(v: Boolean) = viewModelScope.launch { prefs.setAutoUpdate(v) }

    /** Saved birth date (LocalDate), or null when not picked yet. */
    val birthDate = prefs.birthDate
        .map { BirthdayUtils.parse(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Current age computed from the birth date; null when there is no date. */
    val age = birthDate
        .map { date -> date?.let { BirthdayUtils.calculateAge(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setBirthDate(date: LocalDate) = viewModelScope.launch {
        prefs.setBirthDate(BirthdayUtils.format(date))
    }

    // Signed-in Google account info for the profile screen (static — display only)
    private val account get() = authService.getLastSignedInAccount()
    val accountName: String? get() = account?.displayName
    val accountEmail: String? get() = account?.email
    val accountPhotoUrl: String? get() = account?.photoUrl?.toString()

    /** Updates the user's display name; when left blank it falls back to the Google account name. */
    fun setDisplayName(name: String) = viewModelScope.launch { prefs.setDisplayName(name) }

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
    /** The user's own ExerciseDB (RapidAPI) key; null/blank means demo videos are off. */
    val exerciseDbKey = prefs.exerciseDbKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Saves the ExerciseDB key; blank/null clears it (videos turn off). */
    fun setExerciseDbKey(key: String?) = viewModelScope.launch { prefs.setExerciseDbKey(key) }

    fun syncNow() = viewModelScope.launch { syncRepo.sync(manual = true) }
    fun pullNow() = viewModelScope.launch { syncRepo.pullFromDrive(manual = true) }

    /** One-shot manual update check for the About section. Null fetch = Unavailable. */
    suspend fun checkForUpdatesNow(): UpdateCheck {
        val release = runCatching { updates.checkNow() }.getOrNull() ?: return UpdateCheck.Unavailable
        return if (updates.isNewerThanInstalled(release)) UpdateCheck.Available(release)
        else UpdateCheck.UpToDate
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            authService.signOut()
            prefs.setSignedIn(false)
            onDone()
        }
    }
}
