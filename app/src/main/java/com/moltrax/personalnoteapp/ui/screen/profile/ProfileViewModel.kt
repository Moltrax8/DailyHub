package com.moltrax.personalnoteapp.ui.screen.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.data.remote.drive.DriveAuthService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ProfileUiState(
    /** Display name — the user's manually edited name; otherwise the Google account name; otherwise the default. */
    val displayName: String = "",
    val photoUrl: String? = null,
)

/**
 * Provides the read-only data of the profile header: display name and profile photo. The display
 * name prefers the user's custom name (DataStore); when unset it falls back to the Google account
 * name. Edit/sign-out behavior lives in [com.moltrax.personalnoteapp.ui.screen.settings.SettingsViewModel].
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    prefs: AppPreferences,
    private val authService: DriveAuthService,
) : ViewModel() {

    private val defaultName: String
        get() = context.getString(R.string.profile_default_name)

    val uiState: StateFlow<ProfileUiState> =
        prefs.displayName.map { custom ->
            val account = authService.getLastSignedInAccount()
            ProfileUiState(
                displayName = custom
                    ?: account?.displayName?.takeIf { it.isNotBlank() }
                    ?: defaultName,
                photoUrl = account?.photoUrl?.toString(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState(displayName = context.getString(R.string.profile_default_name)))
}
