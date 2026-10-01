package com.moltrax.personalnoteapp.data.repository

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import android.content.Context
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.data.remote.drive.DriveApiService
import com.moltrax.personalnoteapp.data.remote.drive.DriveAuthService
import com.moltrax.personalnoteapp.data.remote.drive.model.SyncMetadata
import com.moltrax.personalnoteapp.data.remote.drive.model.toDomain
import com.moltrax.personalnoteapp.data.remote.drive.model.toJson
import com.moltrax.personalnoteapp.domain.model.Category
import com.moltrax.personalnoteapp.domain.model.SyncStatus
import com.moltrax.personalnoteapp.domain.model.Task
import com.moltrax.personalnoteapp.domain.repository.CategoryRepository
import com.moltrax.personalnoteapp.domain.repository.SyncRepository
import com.moltrax.personalnoteapp.domain.repository.SyncResult
import com.moltrax.personalnoteapp.domain.repository.TaskRepository
import com.moltrax.personalnoteapp.domain.repository.WorkoutRepository
import com.moltrax.personalnoteapp.worker.RescheduleNotificationsWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val taskRepo: TaskRepository,
    private val categoryRepo: CategoryRepository,
    private val workoutRepo: WorkoutRepository,
    private val driveApi: DriveApiService,
    private val driveAuth: DriveAuthService,
    private val prefs: AppPreferences,
    @ApplicationContext private val appContext: Context,
) : SyncRepository {

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val syncStatus: Flow<SyncStatus> = _status

    override suspend fun sync(manual: Boolean): SyncResult = runSync(manual) { token ->
        // Önce çek + birleştir, sonra geri gönder: boş cihazın uzaktaki yedeği ezmesini önler
        pullInternal(token)
        pushInternal(token)
        prefs.setLastSyncAt(Instant.now().toString())
    }

    // Arka plan gönderimi: her zaman sessiz (manual = false). Kayıp güncellemeyi önlemek için
    // salt push yerine çek-birleştir-gönder yapar; imza uyumluluğu için korunur.
    override suspend fun pushToDrive(): SyncResult = sync(manual = false)

    override suspend fun pullFromDrive(manual: Boolean): SyncResult = runSync(manual) { token ->
        pullInternal(token)
    }

    override fun acknowledgeStatus() {
        // Yalnızca başarı durumunu temizle; hata kullanıcı çözene kadar görünür kalsın.
        if (_status.value is SyncStatus.Synced) _status.value = SyncStatus.Idle
    }

    /**
     * Ortak senkronizasyon iskeleti. Başarı durumu yalnızca [manual] tetiklemede ya da önceki
     * durum HATA iken (hatadan kurtarma) gösterilir; normal arka plan senkronizasyonu sessizce
     * Idle'a döner. Hatalar her durumda gösterilir.
     *
     * Sonucu [SyncResult] olarak da döner ki çağıran (öz. SyncWorker) success/retry/failure
     * kararı verebilsin. Coroutine iptali ([CancellationException]) asla yutulmaz.
     */
    private suspend fun runSync(manual: Boolean, block: suspend (token: String) -> Unit): SyncResult {
        // Giriş yapılmamışsa sessizce geç (başlangıçtaki otomatik sync için).
        if (driveAuth.getLastSignedInAccount() == null) return SyncResult.Ok
        val announce = manual || _status.value is SyncStatus.Error
        if (announce) _status.value = SyncStatus.Syncing
        return try {
            val token = driveAuth.getFreshToken()
                ?: throw IllegalStateException("Erişim jetonu alınamadı (oturum geçersiz olabilir)")
            block(token)
            _status.value = if (announce) SyncStatus.Synced else SyncStatus.Idle
            SyncResult.Ok
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _status.value = SyncStatus.Error(e.detail())
            SyncResult.Failed(e, e.isRetryable())
        }
    }

    /** Geçici (ağ) hatalar yeniden denenebilir; auth/parse/programlama hataları kalıcıdır. */
    private fun Throwable.isRetryable(): Boolean =
        this is IOException || cause is IOException

    // Hatanın tam detayını üretir: istisna türü + mesaj + (varsa) kök neden.
    private fun Throwable.detail(): String = buildString {
        append(this@detail::class.java.simpleName)
        message?.let { append(": "); append(it) }
        cause?.let { c ->
            append(" | neden: ").append(c::class.java.simpleName)
            c.message?.let { append(": "); append(it) }
        }
    }

    private suspend fun pushInternal(token: String) {
        val meta = buildMetadata()
        // Yeni cihazda ilk gönderimde uzaktaki mevcut dosyayı keşfet
        val fileId = prefs.driveFileId.first() ?: driveApi.findOrNull(token)?.id

        val result = driveApi.upload(token, meta, fileId)
        prefs.setDriveFileId(result.fileId)
    }

    private suspend fun pullInternal(token: String) {
        var fileId = prefs.driveFileId.first() ?: driveApi.findOrNull(token)?.id ?: return
        var remote = driveApi.download(token, fileId)
        if (remote == null) {
            // Önbellekteki fileId bayat olabilir (dosya silinmiş / hesap değişmiş): önbelleği
            // temizleyip yeniden keşfet; hâlâ yoksa çekecek yedek yoktur (sessiz dön).
            prefs.setDriveFileId(null)
            fileId = driveApi.findOrNull(token)?.id ?: return
            remote = driveApi.download(token, fileId) ?: return
        }

        // Tasks — LWW by updatedAt (mezar taşları ham listede gelir; newer updatedAt kazanır,
        // böylece silme de yayılır ve uzaktan diriltilmez). Eşitlikte kazanan deterministiktir.
        val localTasks = taskRepo.getAllForSync()
        val mergedTasks = mergeById(localTasks, remote.tasks.map { it.toDomain() }, { it.id }) { l, r ->
            if (r.updatedAt > l.updatedAt) r else pickOnTie(l, r)
        }
        // Liste sırası vektörü: karşı tarafın vektörü daha yeniyse benimse (satır saatlerine
        // dokunmadan — sıra bilgisi vektörde taşınır, satır LWW'sini kirletmez); yoksa yerel
        // sıra yayımlanır. Eşzamanlı yeniden sıralamalar artık kimerik karışım üretmez.
        val localOrderTs = taskRepo.getTaskOrderTimestamp()
        val orderedTasks = if (remote.taskOrderUpdatedAt > localOrderTs && remote.taskOrder.isNotEmpty()) {
            taskRepo.setTaskOrderTimestamp(remote.taskOrderUpdatedAt)
            applyRemoteOrder(mergedTasks, remote.taskOrder)
        } else mergedTasks
        taskRepo.replaceAll(orderedTasks)
        // Çekilen birleşim alarm-relevant bir şeyi değiştirdiyse (tamamlanma/silme/vade),
        // sahnelenmiş alarmları tazele. Widget ayrıca DB gözlemcisiyle kendini günceller.
        rescheduleAlarmsIfNeeded(localTasks, orderedTasks)

        // Kategoriler — ada göre birleşim; mezar taşı bulaşıcıdır (bir tarafta silinmişse
        // silinmiş kalır), kalıcılık iki taraftan biri kalıcıysa korunur.
        val mergedCategories = mergeById(
            categoryRepo.getAllForSync(), remote.categories.map { it.toDomain() }, { it.name },
        ) { l, r ->
            if (l.isDeleted || r.isDeleted) {
                Category(name = l.name, isPermanent = l.isPermanent || r.isPermanent, isDeleted = true)
            } else if (l.isPermanent || r.isPermanent) l.copy(isPermanent = true) else l
        }
        categoryRepo.replaceAll(mergedCategories)
        // Birleşim sonrası bağlı görevi kalmayan geçici kategorileri temizle
        categoryRepo.cleanupTemporary()

        // Workout grupları — LWW: grup updatedAt'ine göre. Mezar taşları (isDeleted) dahil edilir,
        // böylece bir tarafta silinen grup/antrenman karşı taraftan geri DİRİLTİLMEZ.
        val mergedGroups = mergeById(workoutRepo.getGroupsForSync(), remote.workoutGroups, { it.id }) { l, r ->
            if (r.updatedAt > l.updatedAt) r else pickOnTie(l, r)
        }
        workoutRepo.replaceGroups(mergedGroups)

        // Workout seansları — değişmez kayıtlar id'ye göre birleşir; mezar taşı bulaşıcıdır
        // (bir tarafta silinmişse silinmiş kalır), çakışmada yereldeki alanlar korunur.
        val mergedSessions = mergeById(workoutRepo.getSessionsForSync(), remote.workoutSessions, { it.id }) { l, r ->
            if (l.isDeleted || r.isDeleted) l.copy(isDeleted = true) else l
        }
        workoutRepo.replaceSessions(mergedSessions)
    }

    private suspend fun buildMetadata(): SyncMetadata {
        // Mezar taşları dahil — diğer cihazlar silmeleri öğrensin.
        val tasks = taskRepo.getAllForSync()
        return SyncMetadata(
            lastModifiedUtc = Instant.now().toString(),
            tasks           = tasks.map { it.toJson() },
            categories      = categoryRepo.getAllForSync().map { it.toJson() },
            workoutGroups   = workoutRepo.getGroupsForSync(),
            workoutSessions = workoutRepo.getSessionsForSync(),
            taskOrder = tasks.sortedBy { it.sortOrder }.map { it.id },
            taskOrderUpdatedAt = taskRepo.getTaskOrderTimestamp(),
        )
    }

    /**
     * Uzak sıra vektörünü yerel birleşmiş listeye uygular. Bilinmeyen (karşıda olmayan) id'ler
     * listenin sonunda mevcut göreli sıralarıyla kalır. sortOrder yeniden yazılır ama updatedAt'e
     * DOKUNULMAZ — sıra bilgisi vektörde taşındığından satır LWW saatleri kirletilmez.
     */
    private fun applyRemoteOrder(tasks: List<Task>, order: List<String>): List<Task> {
        val rank = order.withIndex().associate { it.value to it.index }
        return tasks
            .sortedWith(compareBy({ rank[it.id] ?: Int.MAX_VALUE }, { it.sortOrder }))
            .mapIndexed { index, t ->
                if (t.sortOrder != index.toLong()) t.copy(sortOrder = index.toLong()) else t
            }
    }

    /** Alarm imzası: id → (tamamlandı mı, vade, silindi mi). Sıra değişiklikleri imzada yoktur. */
    private fun alarmSignature(tasks: List<Task>): Map<String, Triple<Boolean, Long?, Boolean>> =
        tasks.associate { it.id to Triple(it.isDone, it.dueDate, it.isDeleted) }

    /**
     * Birleşim alarm-relevant bir şeyi değiştirdiyse sahnelenmiş alarmları tekilleştirilmiş
     * yeniden kurma işiyle tazele (hayalet/eksik bildirim kalmasın). Değişiklik yoksa alarm
     * sistemini uyandırma (pil/gürültü).
     */
    private fun rescheduleAlarmsIfNeeded(before: List<Task>, after: List<Task>) {
        if (alarmSignature(before) == alarmSignature(after)) return
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            RescheduleNotificationsWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RescheduleNotificationsWorker>().build(),
        )
    }

    /**
     * LWW eşitliğinde (aynı id + aynı updatedAt) kazananı deterministik seçer: iki cihaz da
     * aynı girdilerle aynı sonucu hesaplar, böylece birleşim yakınsar. Sözlüksel sıralama
     * keyfidir ama kararlıdır (zaman damgasının yerine geçmez, yalnızca eşitliği bozar).
     */
    private fun <T : Any> pickOnTie(local: T, remote: T): T =
        if (remote.toString() >= local.toString()) remote else local

    /** Generic id-keyed merge; [resolve] picks the winner when an id exists on both sides. */
    private fun <T : Any> mergeById(
        local: List<T>,
        remote: List<T>,
        id: (T) -> String,
        resolve: (local: T, remote: T) -> T,
    ): List<T> {
        val map = LinkedHashMap<String, T>()
        local.forEach { map[id(it)] = it }
        remote.forEach { r ->
            val key = id(r)
            val existing = map[key]
            map[key] = if (existing == null) r else resolve(existing, r)
        }
        return map.values.toList()
    }
}
