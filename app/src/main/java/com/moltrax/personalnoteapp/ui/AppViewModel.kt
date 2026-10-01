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

    // Seçili uygulama dili (varsayılan "en"). Kök composition bunu dinler; değişince tüm metinler
    // anında güncellenir. Diller Android XML kaynaklarıdır; yalnızca aktif dil belleğe alınır, dil
    // değişince eski dilin kaynak referansları bırakılır (manuel önbellek/unload yoktur).
    val language = prefs.language.stateIn(viewModelScope, SharingStarted.Eagerly, "en")

    // Dil geçişi sırasında kısa süreli "Yükleniyor" göstergesi için bayrak. Yalnızca kullanıcı dili
    // değiştirince true olur; açılışta (kayıtlı dilin yüklenmesi) tetiklenmez.
    private val _isSwitchingLanguage = MutableStateFlow(false)
    val isSwitchingLanguage: StateFlow<Boolean> = _isSwitchingLanguage.asStateFlow()

    /** Kullanıcı dili değiştirir: göstergeyi aç ve seçimi kalıcılaştır (kök composition yeni dile geçer). */
    fun setLanguage(code: String) {
        if (code == language.value) return
        _isSwitchingLanguage.value = true
        viewModelScope.launch { prefs.setLanguage(code) }
    }

    /** Yeni dil uygulanıp (yeniden) compose edildikten sonra göstergeyi kapatır. */
    fun onLanguageApplied() {
        _isSwitchingLanguage.value = false
    }
}
