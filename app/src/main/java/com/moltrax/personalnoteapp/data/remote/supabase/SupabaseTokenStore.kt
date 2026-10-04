package com.moltrax.personalnoteapp.data.remote.supabase

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.moltrax.personalnoteapp.data.remote.supabase.model.GoTrueSession
import com.moltrax.personalnoteapp.data.remote.supabase.model.StoredSession
import com.moltrax.personalnoteapp.data.remote.supabase.model.isSessionExpired
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GoTrue session in EncryptedSharedPreferences (never plain DataStore):
 * the app stays signed in across restarts without re-login.
 */
@Singleton
class SupabaseTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            "supabase_session",
            MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun save(session: GoTrueSession) {
        val stored = StoredSession(session, System.currentTimeMillis())
        prefs.edit().putString(KEY_SESSION, json.encodeToString(stored)).apply()
    }

    fun load(): StoredSession? =
        prefs.getString(KEY_SESSION, null)?.let { runCatching { json.decodeFromString<StoredSession>(it) }.getOrNull() }

    fun clear() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean {
        val stored = load() ?: return true
        return isSessionExpired(stored.session.expiresIn, stored.savedAt, nowMs)
    }

    private companion object {
        const val KEY_SESSION = "user_session"
    }
}
