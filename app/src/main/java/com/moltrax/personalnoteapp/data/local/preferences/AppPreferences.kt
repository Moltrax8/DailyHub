package com.moltrax.personalnoteapp.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

@Singleton
class AppPreferences @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val THEME_MODE          = stringPreferencesKey("theme_mode")
        val LANGUAGE            = stringPreferencesKey("language")           // "tr" | "en"
        val REMINDER_MINUTES    = intPreferencesKey("reminder_minutes")
        val SYSTEM_ALERTS       = booleanPreferencesKey("system_alerts_enabled") // Sistem uyarıları (varsayılan açık)
        val IS_SIGNED_IN        = booleanPreferencesKey("is_signed_in")
        val DRIVE_FILE_ID       = stringPreferencesKey("drive_file_id")
        val LAST_SYNC_AT        = stringPreferencesKey("last_sync_at")
        val BIRTH_DATE          = stringPreferencesKey("birth_date")        // ISO "yyyy-MM-dd"
        val BIRTHDAY_SHOWN_ON   = stringPreferencesKey("birthday_shown_on") // ISO "yyyy-MM-dd"

        // Kullanıcının elle düzenlediği görünen ad (Google hesabı adını geçersiz kılar). Boşsa hesap adı kullanılır.
        val DISPLAY_NAME        = stringPreferencesKey("display_name")

        // Spor görevi tamamlama ekranında girilen (henüz onaylanmamış) set/ağırlık taslağı. Görev
        // id'sine göre JSON haritası; ekran kapansa/uygulama kapansa bile veri kaybolmasın diye saklanır.
        val WORKOUT_DRAFTS      = stringPreferencesKey("workout_drafts")

        // Liste sırası vektörünün (SyncMetadata.taskOrder) son değişiklik zamanı. Eşzamanlı
        // yeniden sıralamaların hangisinin kazanacağını belirler (büyük olan yayımlanır).
        val TASK_ORDER_UPDATED_AT = longPreferencesKey("task_order_updated_at")

        // Egzersiz demo videoları için kullanıcının kendi ExerciseDB (RapidAPI) anahtarı.
        // Boşsa demo videoları devre dışı kalır (uygulama yine çalışır).
        val EXERCISEDB_KEY      = stringPreferencesKey("exercisedb_key")
    }

    val themeMode: Flow<String> = context.dataStore.data.map { it[Keys.THEME_MODE] ?: "system" }

    // Uygulama dili — varsayılan "en" (İngilizce). Seçim DataStore'da kalıcı tutulur; uygulama
    // yeniden açıldığında en son seçilen dil yüklenir. Anlık geçiş için kök composition bu akışı dinler.
    val language: Flow<String> = context.dataStore.data.map { it[Keys.LANGUAGE] ?: "en" }
    suspend fun setLanguage(code: String) = context.dataStore.edit { it[Keys.LANGUAGE] = code }

    val reminderMinutes: Flow<Int> = context.dataStore.data.map { it[Keys.REMINDER_MINUTES] ?: 60 }
    suspend fun setReminderMinutes(m: Int) = context.dataStore.edit { it[Keys.REMINDER_MINUTES] = m }

    // Görev bitiş (deadline) hatırlatma bildirimleri. Varsayılan: açık.
    val systemAlertsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.SYSTEM_ALERTS] ?: true }
    suspend fun setSystemAlertsEnabled(v: Boolean) = context.dataStore.edit { it[Keys.SYSTEM_ALERTS] = v }

    val isSignedIn: Flow<Boolean> = context.dataStore.data.map { it[Keys.IS_SIGNED_IN] ?: false }
    suspend fun setSignedIn(v: Boolean) = context.dataStore.edit { it[Keys.IS_SIGNED_IN] = v }

    val driveFileId: Flow<String?> = context.dataStore.data.map { it[Keys.DRIVE_FILE_ID] }
    suspend fun setDriveFileId(id: String?) = context.dataStore.edit {
        if (id == null) it.remove(Keys.DRIVE_FILE_ID) else it[Keys.DRIVE_FILE_ID] = id
    }

    val lastSyncAt: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_SYNC_AT] }
    suspend fun setLastSyncAt(iso: String?) = context.dataStore.edit {
        if (iso == null) it.remove(Keys.LAST_SYNC_AT) else it[Keys.LAST_SYNC_AT] = iso
    }

    // Doğum tarihi — ISO "yyyy-MM-dd" biçiminde saklanır
    val birthDate: Flow<String?> = context.dataStore.data.map { it[Keys.BIRTH_DATE] }
    suspend fun setBirthDate(iso: String?) = context.dataStore.edit {
        if (iso == null) it.remove(Keys.BIRTH_DATE) else it[Keys.BIRTH_DATE] = iso
    }

    // Doğum günü kutlamasının en son gösterildiği gün — günde bir kez gösterimi sağlamak için
    val birthdayShownOn: Flow<String?> = context.dataStore.data.map { it[Keys.BIRTHDAY_SHOWN_ON] }
    suspend fun setBirthdayShownOn(iso: String) = context.dataStore.edit { it[Keys.BIRTHDAY_SHOWN_ON] = iso }

    // Kullanıcının elle düzenlediği görünen ad; ayarlanmamış/boşsa null (Google hesabı adına düşülür).
    val displayName: Flow<String?> = context.dataStore.data.map { it[Keys.DISPLAY_NAME]?.takeIf { n -> n.isNotBlank() } }
    suspend fun setDisplayName(name: String?) = context.dataStore.edit {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) it.remove(Keys.DISPLAY_NAME) else it[Keys.DISPLAY_NAME] = trimmed
    }

    // Spor görevi tamamlama taslağı (görev id → set/ağırlık girişleri) JSON olarak saklanır.
    val workoutDrafts: Flow<String?> = context.dataStore.data.map { it[Keys.WORKOUT_DRAFTS] }
    suspend fun setWorkoutDrafts(json: String?) = context.dataStore.edit {
        if (json.isNullOrBlank()) it.remove(Keys.WORKOUT_DRAFTS) else it[Keys.WORKOUT_DRAFTS] = json
    }

    // Liste sırası vektörünün LWW saati (0 = hiç yeniden sıralanmadı).
    val taskOrderUpdatedAt: Flow<Long> = context.dataStore.data.map { it[Keys.TASK_ORDER_UPDATED_AT] ?: 0L }
    suspend fun setTaskOrderUpdatedAt(v: Long) = context.dataStore.edit { it[Keys.TASK_ORDER_UPDATED_AT] = v }

    // Kullanıcının kendi ExerciseDB API anahtarı (RapidAPI). Boş = demo videoları kapalı.
    val exerciseDbKey: Flow<String?> = context.dataStore.data.map { it[Keys.EXERCISEDB_KEY]?.takeIf { it.isNotBlank() } }
    suspend fun setExerciseDbKey(key: String?) = context.dataStore.edit {
        val trimmed = key?.trim().orEmpty()
        if (trimmed.isEmpty()) it.remove(Keys.EXERCISEDB_KEY) else it[Keys.EXERCISEDB_KEY] = trimmed
    }
}
