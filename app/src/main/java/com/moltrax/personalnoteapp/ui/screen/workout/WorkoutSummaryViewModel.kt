package com.moltrax.personalnoteapp.ui.screen.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.moltrax.personalnoteapp.data.json.WorkoutJsonCodec
import com.moltrax.personalnoteapp.data.local.storage.WorkoutFileManager
import com.moltrax.personalnoteapp.domain.model.WorkoutSession
import com.moltrax.personalnoteapp.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tamamlanan bir antrenman seansının özet/sonuç sayfasının veri katmanı. Seans id'sinden tek bir
 * [WorkoutSession]'ı (o gün yapılan hareketler + girilen set/tekrar/ağırlık) yükler.
 */
@HiltViewModel
class WorkoutSummaryViewModel @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val fileManager: WorkoutFileManager,
) : ViewModel() {

    private val _session = MutableStateFlow<WorkoutSession?>(null)
    val session: StateFlow<WorkoutSession?> = _session.asStateFlow()

    // İlk yükleme tamamlandı mı (yoksa "yükleniyor" göstergesi; bittiğinde "bulunamadı" olabilir).
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    fun load(sessionId: String) {
        if (_session.value?.id == sessionId) return
        viewModelScope.launch {
            _session.value = workoutRepo.getSessionById(sessionId)
            _loaded.value = true
        }
    }

    /**
     * Görüntülenen seansın BİREBİR aynını JSON olarak kullanıcının seçtiği belgeye yazar.
     * Salt okunur: yeni seans oluşturmaz, mevcut seansı değiştirmez. Sonucu [onDone] ile bildirir.
     */
    fun exportResultToUri(session: WorkoutSession, uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = fileManager
                .writeText(uri, WorkoutJsonCodec.encodeResult(session))
                .isSuccess
            onDone(ok)
        }
    }
}
