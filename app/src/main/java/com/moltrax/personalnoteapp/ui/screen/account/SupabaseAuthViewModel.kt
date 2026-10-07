package com.moltrax.personalnoteapp.ui.screen.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.data.remote.supabase.SessionState
import com.moltrax.personalnoteapp.data.remote.supabase.SupabaseAuthService
import com.moltrax.personalnoteapp.domain.model.isValidUsername
import com.moltrax.personalnoteapp.domain.repository.ProfileRepository
import com.moltrax.personalnoteapp.domain.model.SupabaseProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Auth screen steps: credentials first, username only when the profile row is missing. */
enum class AuthStep { Credentials, Username, Done }

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val signUpMode: Boolean = false,
    val username: String = "",
    val step: AuthStep = AuthStep.Credentials,
    val busy: Boolean = false,
    val error: String? = null,
)

/**
 * Managed-account sign-in (Phase 3). The app stays fully usable offline —
 * this screen is an entry point, not a blocking gate (see AppNavHost).
 */
@HiltViewModel
class SupabaseAuthViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: SupabaseAuthService,
    private val profiles: ProfileRepository,
    private val prefs: AppPreferences,
) : ViewModel() {

    val isConfigured: Boolean = auth.isConfigured

    val sessionState: StateFlow<SessionState> = auth.sessionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState.SignedOut)

    /** Stored account email for display (Profile header); null when signed out. Read-only. */
    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    init {
        viewModelScope.launch {
            auth.sessionState.collect {
                _userEmail.value = if (it is SessionState.SignedIn) {
                    runCatching { auth.currentUserEmail() }.getOrNull()
                } else null
            }
        }
    }

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    /** Field edits clear the last error. */
    fun update(transform: (AuthUiState) -> AuthUiState) {
        _state.update { transform(it).copy(error = null) }
    }

    fun submitCredentials() {
        val s = _state.value
        if (s.email.isBlank() || s.password.length < 6 || s.busy) {
            _state.update { it.copy(error = context.getString(R.string.auth_err_credentials)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val result = if (s.signUpMode) auth.signUp(s.email, s.password) else auth.signIn(s.email, s.password)
            result.onSuccess {
                val uid = auth.currentUserId()
                prefs.setSupabaseUserId(uid)
                _state.update { it.copy(busy = false) }
                if (uid != null) enterUsernameIfNeeded(uid)
            }.onFailure { e ->
                _state.update { it.copy(busy = false, error = e.message ?: context.getString(R.string.auth_err_signin)) }
            }
        }
    }

    private suspend fun enterUsernameIfNeeded(uid: String) {
        val existing = runCatching { profiles.getMyProfile(uid) }.getOrNull()
        if (existing != null) {
            _state.update { it.copy(step = AuthStep.Done) }
        } else {
            _state.update { it.copy(step = AuthStep.Username) }
        }
    }

    fun submitUsername() {
        val name = _state.value.username.trim()
        if (!isValidUsername(name)) {
            _state.update { it.copy(error = context.getString(R.string.auth_err_username)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val uid = auth.currentUserId()
            if (uid == null) {
                _state.update { it.copy(busy = false, error = context.getString(R.string.auth_err_session)) }
                return@launch
            }
            runCatching { profiles.upsertMyProfile(SupabaseProfile(id = uid, username = name)) }
                .onSuccess { _state.update { it.copy(busy = false, step = AuthStep.Done) } }
                .onFailure { e ->
                    _state.update { it.copy(busy = false, error = e.message ?: context.getString(R.string.auth_err_username_save)) }
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            prefs.setSupabaseUserId(null)
        }
    }

    /** Loads any persisted session into memory (once per process). */
    fun restore() {
        viewModelScope.launch { auth.restore() }
    }

    fun consumeDone() {
        _state.update { it.copy(step = AuthStep.Credentials) }
    }
}
