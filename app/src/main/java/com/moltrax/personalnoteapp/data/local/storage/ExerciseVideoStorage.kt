package com.moltrax.personalnoteapp.data.local.storage

import android.content.Context
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Egzersiz demo medyasını (ExerciseDB GIF'i veya gerçek .mp4 videosu) uygulamanın iç
 * depolamasına indirir; böylece hareket çevrimdışıyken de oynatılabilir.
 *
 * Dosyalar [filesDir]/exercise_media altında, kaynak egzersiz id'si ile adlandırılır
 * (örn. `0001.gif`). Aynı dosya tekrar indirilmez. İlgili egzersiz/program silindiğinde
 * [delete] ile fiziksel dosya da temizlenir (Repository katmanı çağırır).
 */
@Singleton
class ExerciseVideoStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val prefs: AppPreferences,
) {
    private val dir: File by lazy {
        File(context.filesDir, "exercise_media").apply { if (!exists()) mkdirs() }
    }

    /**
     * [url] adresindeki medyayı indirip mutlak yolunu döner. Dosya zaten varsa yeniden
     * indirmeden mevcut yolu döner. Hata olursa null (UI yine de uzak URL'den oynatabilir).
     */
    suspend fun download(exerciseId: String, url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            // ExerciseDB resim ucu (".../image?...") her zaman GIF döndürür; uzantı buradan çıkarılamaz.
            // Diğer (eski/doğrudan) URL'lerde uzantı adresten alınır.
            val ext = if (url.contains("/image", ignoreCase = true)) "gif"
                else url.substringBefore('?').substringAfterLast('.', "mp4")
                    .takeIf { it.length in 1..5 } ?: "mp4"
            val file = File(dir, "$exerciseId.$ext")
            if (file.exists() && file.length() > 0) return@withContext file.absolutePath
            // Aynı egzersizin farklı uzantılı eski artıkları (gif↔mp4 geçişi) temizlenir.
            dir.listFiles { f -> f.name.startsWith("$exerciseId.") && f.name != file.name }
                ?.forEach { runCatching { it.delete() } }

            // ExerciseDB demo'su yalnızca X-RapidAPI-Key header'ı ile indirilebilir (aksi halde 401).
            val key = prefs.exerciseDbKey.first() ?: ""
            val request = Request.Builder().url(url).apply {
                if (url.contains("rapidapi.com", ignoreCase = true) && key.isNotBlank()) {
                    header("X-RapidAPI-Key", key)
                }
            }.build()
            // Yarım indirme önbelleğe girmesin: önce tmp dosyaya yaz, bitince atomik taşı.
            // Crash anında kalan tmp bir sonraki indirmede ezilir; bozuk dosya asla dönülmez.
            val tmp = File(dir, "$exerciseId.$ext.tmp")
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body ?: return@withContext null
                body.byteStream().use { input ->
                    FileOutputStream(tmp).use { output -> input.copyTo(output) }
                }
            }
            if (tmp.length() > 0 && tmp.renameTo(file)) file.absolutePath
            else { runCatching { tmp.delete() }; null }
        }.getOrNull()
    }

    /** Egzersiz/program silindiğinde lokal medya dosyasını temizler. */
    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    /** Egzersize ait TÜM önbellek varyantlarını siler (uzantı değişmiş artıklar dahil). */
    fun deleteVariants(exerciseId: String) {
        runCatching {
            dir.listFiles { f -> f.name.startsWith("$exerciseId.") }?.forEach { it.delete() }
        }
    }
}
