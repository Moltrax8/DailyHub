package com.moltrax.personalnoteapp.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Discard retired feature settings/API keys while retaining all local-core preferences. */
internal object CorePreferencesMigration : DataMigration<Preferences> {
    private val retained = setOf("theme_mode", "language", "reminder_minutes", "system_alerts_enabled", "auto_update")
    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData.asMap().keys.any { it.name !in retained }
    override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().apply {
        asMap().keys.filter { it.name !in retained }.forEach { remove(it) }
    }
    override suspend fun cleanUp() = Unit
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_prefs", produceMigrations = { listOf(CorePreferencesMigration) },
)

@Singleton
class AppPreferences @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val REMINDER_MINUTES = intPreferencesKey("reminder_minutes")
        val SYSTEM_ALERTS = booleanPreferencesKey("system_alerts_enabled")
        val AUTO_UPDATE = booleanPreferencesKey("auto_update")
    }

    val themeMode: Flow<String> = context.dataStore.data.map { it[Keys.THEME_MODE] ?: "system" }
    suspend fun setThemeMode(mode: String) = context.dataStore.edit {
        val normalized = mode.lowercase().takeIf { it == "light" || it == "dark" } ?: "system"
        it[Keys.THEME_MODE] = normalized
    }

    // App language — default "en". The root composition observes this flow for instant switching.
    val language: Flow<String> = context.dataStore.data.map { it[Keys.LANGUAGE] ?: "en" }
    suspend fun setLanguage(code: String) = context.dataStore.edit { it[Keys.LANGUAGE] = code }

    val reminderMinutes: Flow<Int> = context.dataStore.data.map { it[Keys.REMINDER_MINUTES] ?: 60 }
    suspend fun setReminderMinutes(m: Int) = context.dataStore.edit { it[Keys.REMINDER_MINUTES] = m }

    // Task deadline reminder notifications. Default: enabled.
    val systemAlertsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.SYSTEM_ALERTS] ?: true }
    suspend fun setSystemAlertsEnabled(v: Boolean) = context.dataStore.edit { it[Keys.SYSTEM_ALERTS] = v }

    // Automatic GitHub release checks. Default: enabled.
    val autoUpdate: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_UPDATE] ?: true }
    suspend fun setAutoUpdate(v: Boolean) = context.dataStore.edit { it[Keys.AUTO_UPDATE] = v }
}
