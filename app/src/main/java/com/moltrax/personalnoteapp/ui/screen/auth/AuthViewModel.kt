package com.moltrax.personalnoteapp.ui.screen.auth

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.data.remote.drive.DriveAuthService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authService: DriveAuthService,
    private val prefs: AppPreferences,
) : ViewModel() {

    val isSignedIn = prefs.isSignedIn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val signInIntent: Intent get() = authService.signInIntent

    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch {
            runCatching {
                val account = GoogleSignIn.getSignedInAccountFromIntent(data).result
                if (account != null) prefs.setSignedIn(true)
            }
        }
    }

    /**
     * Sign-in sonrası Drive (drive.appdata) iznini doğrular. İzin eksikse onay ekranı
     * Intent'ini [onConsentRequired] ile döndürür (UI başlatır); jeton alınırken başka bir
     * hata olursa [onError] gerçek mesajla çağrılır.
     */
    fun ensureDriveConsent(onConsentRequired: (Intent) -> Unit, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { authService.getConsentIntentOrNull() }
                .onSuccess { intent -> if (intent != null) onConsentRequired(intent) }
                .onFailure { onError("${it.javaClass.simpleName}: ${it.message ?: ""}") }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authService.signOut()
            prefs.setSignedIn(false)
        }
    }

    fun checkExistingSignIn() {
        viewModelScope.launch {
            prefs.setSignedIn(authService.isSignedIn())
        }
    }
}
