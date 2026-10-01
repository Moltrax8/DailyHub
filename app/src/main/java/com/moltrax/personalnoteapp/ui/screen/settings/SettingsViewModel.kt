package com.moltrax.personalnoteapp.ui.screen.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.data.remote.drive.DriveAuthService
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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val authService: DriveAuthService,
    private val syncRepo: SyncRepository,
    @ApplicationContext private val appContext: Context,
    private val taskRepo: TaskRepository,
    private val notifService: NotificationService,
) : ViewModel() {

    val language       = prefs.language.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "en")
    val reminderMinutes = prefs.reminderMinutes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 60)
    val systemAlertsEnabled = prefs.systemAlertsEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val lastSyncAt     = prefs.lastSyncAt.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Kayıtlı doğum tarihi (LocalDate) veya henüz seçilmemişse null. */
    val birthDate = prefs.birthDate
        .map { BirthdayUtils.parse(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Doğum tarihine göre hesaplanan güncel yaş; tarih yoksa null. */
    val age = birthDate
        .map { date -> date?.let { BirthdayUtils.calculateAge(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setBirthDate(date: LocalDate) = viewModelScope.launch {
        prefs.setBirthDate(BirthdayUtils.format(date))
    }

    // Profil ekranı için oturum açmış Google hesabı bilgileri (statik — gösterim amaçlı)
    private val account get() = authService.getLastSignedInAccount()
    val accountName: String? get() = account?.displayName
    val accountEmail: String? get() = account?.email
    val accountPhotoUrl: String? get() = account?.photoUrl?.toString()

    /** Kullanıcının görünen adını günceller; boş bırakılırsa Google hesabı adına geri döner. */
    fun setDisplayName(name: String) = viewModelScope.launch { prefs.setDisplayName(name) }

    fun setReminderMinutes(m: Int) = viewModelScope.launch {
        prefs.setReminderMinutes(m)
        // Yeni ön-bildirim süresi mevcut alarmlara yansısın diye tekilleştirilmiş yeniden kurma işi.
        enqueueReschedule()
    }

    fun setSystemAlertsEnabled(v: Boolean) = viewModelScope.launch {
        prefs.setSystemAlertsEnabled(v)
        if (!v) {
            // Kapatınca sahnelenmiş alarmları hemen iptal et (DataStore yazımı tek başına yetmez).
            notifService.cancelAll(taskRepo.getAll().map { it.id })
        } else {
            enqueueReschedule()
        }
    }

    /** Kesin alarm izni verildi mi (verilmediyse hatırlatmalar inexact yedeğe düşer). */
    fun canScheduleExactAlarms(): Boolean = notifService.canScheduleExactAlarms()

    /** Bildirim izni açık mı (POST_NOTIFICATIONS dahil). */
    fun areNotificationsEnabled(): Boolean = notifService.areNotificationsEnabled()

    /** Sistemin kesin-alarm izin ekranını açar. */
    fun openExactAlarmSettings() {
        runCatching { appContext.startActivity(notifService.exactAlarmSettingsIntent()) }
    }

    /** Uygulamanın sistem bildirim ayarları ekranını açar. */
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
    /** Kullanıcının kendi ExerciseDB (RapidAPI) anahtarı; null/boşsa demo videoları kapalıdır. */
    val exerciseDbKey = prefs.exerciseDbKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** ExerciseDB anahtarını kaydeder; boş/null ise temizler (videolar kapanır). */
    fun setExerciseDbKey(key: String?) = viewModelScope.launch { prefs.setExerciseDbKey(key) }

    fun syncNow() = viewModelScope.launch { syncRepo.sync(manual = true) }
    fun pullNow() = viewModelScope.launch { syncRepo.pullFromDrive(manual = true) }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            authService.signOut()
            prefs.setSignedIn(false)
            onDone()
        }
    }
}
