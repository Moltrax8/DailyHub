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
    /** Görünen ad — kullanıcının elle düzenlediği ad; yoksa Google hesabı adı; o da yoksa varsayılan. */
    val displayName: String = "",
    val photoUrl: String? = null,
)

/**
 * Profil başlığının salt-okunur verisini sağlar: görünen ad ve profil fotoğrafı. Görünen ad,
 * kullanıcının ayarladığı özel adı (DataStore) önceler; ayarlı değilse Google hesabı adına düşülür.
 * Düzenleme/çıkış işlevleri [com.moltrax.personalnoteapp.ui.screen.settings.SettingsViewModel]'de.
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
