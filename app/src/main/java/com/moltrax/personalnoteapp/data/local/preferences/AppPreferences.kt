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
        val SYSTEM_ALERTS       = booleanPreferencesKey("system_alerts_enabled") // System alerts (enabled by default)
        val IS_SIGNED_IN        = booleanPreferencesKey("is_signed_in")
        val DRIVE_FILE_ID       = stringPreferencesKey("drive_file_id")
        val LAST_SYNC_AT        = stringPreferencesKey("last_sync_at")
        val BIRTH_DATE          = stringPreferencesKey("birth_date")        // ISO "yyyy-MM-dd"
        val BIRTHDAY_SHOWN_ON   = stringPreferencesKey("birthday_shown_on") // ISO "yyyy-MM-dd"

        // User-edited display name (overrides the Google account name). Falls back to the account name when empty.
        val DISPLAY_NAME        = stringPreferencesKey("display_name")

        // Unconfirmed set/weight draft entered on the sport-task completion screen. Stored as a
        // JSON map keyed by task id so the data survives screen/app restarts.
        val WORKOUT_DRAFTS      = stringPreferencesKey("workout_drafts")

        // Last modification time of the list-order vector (SyncMetadata.taskOrder). Decides which
        // concurrent reordering wins (the larger one is published).
        val TASK_ORDER_UPDATED_AT = longPreferencesKey("task_order_updated_at")

        // The user's own ExerciseDB (RapidAPI) key for exercise demo videos.
        // When empty, demo videos stay disabled (the app still works).
        val EXERCISEDB_KEY      = stringPreferencesKey("exercisedb_key")

        // Managed-account identity (Phase 3+): Supabase auth.uid, null when signed out.
        val SUPABASE_USER_ID    = stringPreferencesKey("supabase_user_id")
    }

    val themeMode: Flow<String> = context.dataStore.data.map { it[Keys.THEME_MODE] ?: "system" }

    // App language — default "en" (English). The choice is persisted in DataStore; the last
    // selected language loads on restart. The root composition observes this flow for instant switching.
    val language: Flow<String> = context.dataStore.data.map { it[Keys.LANGUAGE] ?: "en" }
    suspend fun setLanguage(code: String) = context.dataStore.edit { it[Keys.LANGUAGE] = code }

    val reminderMinutes: Flow<Int> = context.dataStore.data.map { it[Keys.REMINDER_MINUTES] ?: 60 }
    suspend fun setReminderMinutes(m: Int) = context.dataStore.edit { it[Keys.REMINDER_MINUTES] = m }

    // Task deadline reminder notifications. Default: enabled.
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

    // Date of birth — stored in ISO "yyyy-MM-dd" format
    val birthDate: Flow<String?> = context.dataStore.data.map { it[Keys.BIRTH_DATE] }
    suspend fun setBirthDate(iso: String?) = context.dataStore.edit {
        if (iso == null) it.remove(Keys.BIRTH_DATE) else it[Keys.BIRTH_DATE] = iso
    }

    // Day the birthday celebration was last shown — to show it once per day
    val birthdayShownOn: Flow<String?> = context.dataStore.data.map { it[Keys.BIRTHDAY_SHOWN_ON] }
    suspend fun setBirthdayShownOn(iso: String) = context.dataStore.edit { it[Keys.BIRTHDAY_SHOWN_ON] = iso }

    // User-edited display name; null when unset/blank (falls back to the Google account name).
    val displayName: Flow<String?> = context.dataStore.data.map { it[Keys.DISPLAY_NAME]?.takeIf { n -> n.isNotBlank() } }
    suspend fun setDisplayName(name: String?) = context.dataStore.edit {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) it.remove(Keys.DISPLAY_NAME) else it[Keys.DISPLAY_NAME] = trimmed
    }

    // Sport-task completion draft (task id → set/weight entries) stored as JSON.
    val workoutDrafts: Flow<String?> = context.dataStore.data.map { it[Keys.WORKOUT_DRAFTS] }
    suspend fun setWorkoutDrafts(json: String?) = context.dataStore.edit {
        if (json.isNullOrBlank()) it.remove(Keys.WORKOUT_DRAFTS) else it[Keys.WORKOUT_DRAFTS] = json
    }

    // LWW clock of the list-order vector (0 = never reordered).
    val taskOrderUpdatedAt: Flow<Long> = context.dataStore.data.map { it[Keys.TASK_ORDER_UPDATED_AT] ?: 0L }
    suspend fun setTaskOrderUpdatedAt(v: Long) = context.dataStore.edit { it[Keys.TASK_ORDER_UPDATED_AT] = v }

    // The user's own ExerciseDB API key (RapidAPI). Empty = demo videos off.
    val exerciseDbKey: Flow<String?> = context.dataStore.data.map { it[Keys.EXERCISEDB_KEY]?.takeIf { it.isNotBlank() } }
    suspend fun setExerciseDbKey(key: String?) = context.dataStore.edit {
        val trimmed = key?.trim().orEmpty()
        if (trimmed.isEmpty()) it.remove(Keys.EXERCISEDB_KEY) else it[Keys.EXERCISEDB_KEY] = trimmed
    }

    // Managed-account identity (Phase 3+ Supabase auth.uid); null when signed out.
    val supabaseUserId: Flow<String?> = context.dataStore.data.map { it[Keys.SUPABASE_USER_ID]?.takeIf { it.isNotBlank() } }
    suspend fun setSupabaseUserId(id: String?) = context.dataStore.edit {
        val trimmed = id?.trim().orEmpty()
        if (trimmed.isEmpty()) it.remove(Keys.SUPABASE_USER_ID) else it[Keys.SUPABASE_USER_ID] = trimmed
    }
}
