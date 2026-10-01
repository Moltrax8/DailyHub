package com.moltrax.personalnoteapp.domain.repository

import com.moltrax.personalnoteapp.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Senkronizasyon işleminin makinece okunabilir sonucu (insanca okunabilir durum
 * [SyncStatus] akışında kalır). Dönüş tipi olduğu için çağıranlar yok sayabilir;
 * [androidx.work.CoroutineWorker] bunu success/retry/failure kararında kullanır.
 */
sealed interface SyncResult {
    data object Ok : SyncResult

    /**
     * [retryable] true = geçici hata (ağ) → worker yeniden denemeli;
     * false = kalıcı hata (auth/parse) → worker başarısız saymalı.
     */
    data class Failed(val error: Throwable, val retryable: Boolean) : SyncResult
}

interface SyncRepository {
    val syncStatus: Flow<SyncStatus>

    /**
     * Arka plan (otomatik) gönderim — veri değiştikçe çağrılır, başarısı sessiz geçer.
     * Kayıp güncellemeyi önlemek için içeride çek-birleştir-gönder yapar (salt push,
     * eşzamanlı düzenlenen veriyi ezebilirdi); imza uyumluluğu için korunur.
     */
    suspend fun pushToDrive(): SyncResult

    suspend fun pullFromDrive(manual: Boolean = false): SyncResult

    /**
     * Güvenli tam senkronizasyon: önce uzaktaki veriyi çekip yerelle birleştirir,
     * sonra birleşmiş sonucu geri gönderir. Yeni/boş bir cihazda uzaktaki yedeğin
     * boş veriyle ezilmesini önler. Açılışta ve manuel "Senkronize et" için kullanılır.
     *
     * [manual] true ise (kullanıcı tetiklediyse) başarı durumu arayüzde gösterilir.
     * Otomatik (arka plan) senkronizasyonda başarı yalnızca önceki durum HATA ise gösterilir;
     * aksi halde sessizce tamamlanır.
     */
    suspend fun sync(manual: Boolean = false): SyncResult

    /** Gösterilen "Senkronize edildi" başarı durumunu temizler (Idle'a çeker). Hatayı temizlemez. */
    fun acknowledgeStatus()
}
