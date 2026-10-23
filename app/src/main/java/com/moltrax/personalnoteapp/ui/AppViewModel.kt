package com.moltrax.personalnoteapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(private val prefs: AppPreferences) : ViewModel() {
    val themeMode = prefs.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, "system")

    fun setThemeMode(mode: String) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    // Selected app language (default "en"). The root composition observes this; when it changes all texts
    // update instantly. Languages are Android XML resources; only the active language is kept in memory, and
    // the old language's resource references are released on change (no manual cache/unload).
    val language = prefs.language.stateIn(viewModelScope, SharingStarted.Eagerly, "en")

    // Flag for a brief "Loading" indicator during a language switch. Becomes true only when the user changes
    // the language; not triggered at startup (loading the saved language).
    private val _isSwitchingLanguage = MutableStateFlow(false)
    val isSwitchingLanguage: StateFlow<Boolean> = _isSwitchingLanguage.asStateFlow()

    /** User changes the language: show the indicator and persist the selection (root composition switches to the new language). */
    fun setLanguage(code: String) {
        if (code == language.value) return
        _isSwitchingLanguage.value = true
        viewModelScope.launch { prefs.setLanguage(code) }
    }

    /** Hide the indicator after the new language is applied and (re)composed. */
    fun onLanguageApplied() {
        _isSwitchingLanguage.value = false
    }
}
