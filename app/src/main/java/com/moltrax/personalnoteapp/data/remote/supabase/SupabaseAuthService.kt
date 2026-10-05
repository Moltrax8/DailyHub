package com.moltrax.personalnoteapp.data.remote.supabase

import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.data.remote.supabase.model.EmailCredentials
import com.moltrax.personalnoteapp.data.remote.supabase.model.GoTrueSession
import com.moltrax.personalnoteapp.data.remote.supabase.model.GoTrueUser
import com.moltrax.personalnoteapp.data.remote.supabase.model.RefreshRequest
import com.moltrax.personalnoteapp.data.remote.supabase.model.parseGoTrueError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Local session state (no server round-trip to observe). */
sealed interface SessionState {
    data object SignedOut : SessionState
    data class SignedIn(val userId: String) : SessionState
}

/**
 * Managed-account auth (Phase 3) over GoTrue REST — no new SDK, same
 * Retrofit/OkHttp stack as the rest of the app. Fully separated from Drive
 * sign-in. Every method fails gracefully when unconfigured.
 */
@Singleton
class SupabaseAuthService @Inject constructor(
    private val api: SupabaseAuthApi?,
    private val tokens: SupabaseTokenStore,
    private val prefs: AppPreferences,
) {
    val isConfigured: Boolean get() = api != null

    private val _session = MutableStateFlow<SessionState>(SessionState.SignedOut)
    val sessionState: StateFlow<SessionState> = _session.asStateFlow()

    private val refreshMutex = Mutex()

    /** Restores a persisted session (if any) into memory. Call once at startup. */
    suspend fun restore() {
        val stored = tokens.load()
        val uid = stored?.session?.user?.id
        // A stored row without a usable access token (e.g. legacy confirm-pending
        // payload) must not count as signed in.
        if (uid != null && !stored.session.accessToken.isNullOrBlank()) {
            _session.value = SessionState.SignedIn(uid)
            prefs.setSupabaseUserId(uid)
        } else {
            _session.value = SessionState.SignedOut
            prefs.setSupabaseUserId(null)
        }
    }

    suspend fun signUp(email: String, password: String): Result<Unit> {
        val call = api ?: return Result.failure(IllegalStateException(NOT_CONFIGURED))
        return runCatching {
            val res = call.signUp(EmailCredentials(email.trim(), password))
            if (!res.isSuccessful) throw IllegalStateException(parseGoTrueError(res.code(), res.errorBody()?.string()))
            val session = res.body() ?: throw IllegalStateException(NO_SESSION)
            // Confirm-email ON projects return a bare user object (no tokens).
            if (session.needsConfirmation()) throw IllegalStateException(CONFIRM_EMAIL)
            onNewSession(
                session.accessToken ?: throw IllegalStateException(NO_SESSION),
                session.refreshToken.orEmpty(), session.expiresIn, session.user?.id,
            )
            Unit
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> {
        val call = api ?: return Result.failure(IllegalStateException(NOT_CONFIGURED))
        return runCatching {
            val res = call.signIn(EmailCredentials(email.trim(), password))
            if (!res.isSuccessful) throw IllegalStateException(parseGoTrueError(res.code(), res.errorBody()?.string()))
            val session = res.body() ?: throw IllegalStateException(NO_SESSION)
            onNewSession(
                session.accessToken ?: throw IllegalStateException(NO_SESSION),
                session.refreshToken.orEmpty(), session.expiresIn, session.user?.id,
            )
            Unit
        }
    }

    suspend fun signOut() {
        val token = tokens.load()?.session?.accessToken?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
        runCatching { if (token != null) api?.logout(token) }
        tokens.clear()
        prefs.setSupabaseUserId(null)
        _session.value = SessionState.SignedOut
    }

    suspend fun currentUserId(): String? = when (val s = _session.value) {
        is SessionState.SignedIn -> s.userId
        SessionState.SignedOut -> tokens.load()?.session?.user?.id?.also {
            _session.value = SessionState.SignedIn(it)
        }
    }

    /**
     * Usable access token, refreshing transparently when expired. Null when
     * signed out or the refresh fails (caller treats as signed-out upstream).
     */
    suspend fun freshToken(): String? = refreshMutex.withLock {
        val stored = tokens.load() ?: return null
        val current = stored.session.accessToken?.takeIf { it.isNotBlank() } ?: return null
        if (!tokens.isExpired()) return current
        val call = api ?: return null
        val refreshToken = stored.session.refreshToken?.ifBlank { null } ?: return null
        val res = runCatching { call.refresh(RefreshRequest(refreshToken)) }.getOrNull()
            ?: return null
        if (!res.isSuccessful) {
            // Refresh rejected (revoked/rotated): drop the session, user signs in again.
            signOut()
            return null
        }
        val session = res.body() ?: return null
        val access = session.accessToken ?: return null
        onNewSession(access, session.refreshToken.orEmpty(), session.expiresIn, session.user?.id)
        access
    }

    private suspend fun onNewSession(access: String, refresh: String, expiresIn: Long, uid: String?) {
        tokens.save(
            GoTrueSession(
                accessToken = access, refreshToken = refresh, expiresIn = expiresIn,
                user = uid?.let(::GoTrueUser),
            )
        )
        if (uid != null) {
            prefs.setSupabaseUserId(uid)
            _session.value = SessionState.SignedIn(uid)
        }
    }

    private companion object {
        const val NOT_CONFIGURED = "Supabase is not configured (missing SUPABASE_URL/ANON_KEY)."
        const val NO_SESSION = "No session returned."
        const val CONFIRM_EMAIL =
            "Account created — check your email, tap the confirm link, then come back and Sign in."
    }
}
