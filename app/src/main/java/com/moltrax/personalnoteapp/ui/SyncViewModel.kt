package com.moltrax.personalnoteapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moltrax.personalnoteapp.domain.model.SyncStatus
import com.moltrax.personalnoteapp.domain.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Uygulama (Activity) seviyesinde tutulan senkronizasyon durumu. SyncRepository @Singleton
 * olduğundan tüm ekranlarla aynı durum akışını paylaşır; böylece sync banner'ı tüm sekmelerde
 * global olarak gösterilebilir.
 */
@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncRepo: SyncRepository,
) : ViewModel() {

    val syncStatus: StateFlow<SyncStatus> = syncRepo.syncStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatus.Idle)

    /** Kullanıcı tetikledi (aşağı çekerek yenileme / manuel buton) → başarı arayüzde gösterilsin. */
    fun sync() { viewModelScope.launch { syncRepo.sync(manual = true) } }

    /**
     * Yaşam döngüsü tetiklemesi (uygulama ön plana geldiğinde) → sessiz arka plan
     * senkronizasyonu. Başarı mesajı gösterilmez; yalnızca önceki durum HATA ise
     * (hatadan kurtarma) repository başarıyı yayınlar.
     */
    fun syncSilent() { viewModelScope.launch { syncRepo.sync(manual = false) } }

    /** Gösterilen "Senkronize edildi" mesajını temizle. */
    fun acknowledge() = syncRepo.acknowledgeStatus()
}
