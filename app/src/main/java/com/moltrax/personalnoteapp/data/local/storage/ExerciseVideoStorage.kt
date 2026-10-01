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
 * Downloads exercise demo media (ExerciseDB GIF or real .mp4 video) into the app's internal
 * storage so a movement can still play offline.
 *
 * Files live under [filesDir]/exercise_media, named by the source exercise id
 * (e.g. `0001.gif`). The same file is never downloaded twice. When the related
 * exercise/program is deleted, the physical file is also cleaned up via [delete]
 * (called by the Repository layer).
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
     * Downloads the media at [url] and returns its absolute path. Returns the existing path
     * without re-downloading if the file already exists. Returns null on error (the UI can
     * still play from the remote URL).
     */
    suspend fun download(exerciseId: String, url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            // The ExerciseDB image endpoint (".../image?...") always returns GIF; the extension cannot be inferred from it.
            // For other (legacy/direct) URLs, the extension is taken from the address.
            val ext = if (url.contains("/image", ignoreCase = true)) "gif"
                else url.substringBefore('?').substringAfterLast('.', "mp4")
                    .takeIf { it.length in 1..5 } ?: "mp4"
            val file = File(dir, "$exerciseId.$ext")
            if (file.exists() && file.length() > 0) return@withContext file.absolutePath
            // Clean up stale leftovers of the same exercise with a different extension (gif↔mp4 transitions).
            dir.listFiles { f -> f.name.startsWith("$exerciseId.") && f.name != file.name }
                ?.forEach { runCatching { it.delete() } }

            // The ExerciseDB demo can only be downloaded with the X-RapidAPI-Key header (401 otherwise).
            val key = prefs.exerciseDbKey.first() ?: ""
            val request = Request.Builder().url(url).apply {
                if (url.contains("rapidapi.com", ignoreCase = true) && key.isNotBlank()) {
                    header("X-RapidAPI-Key", key)
                }
            }.build()
            // Never let a partial download enter the cache: write to a tmp file first, then move atomically when done.
            // A tmp left behind by a crash is overwritten on the next download; a corrupt file is never returned.
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

    /** Cleans up the local media file when its exercise/program is deleted. */
    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    /** Deletes ALL cached variants of an exercise (including leftovers with a changed extension). */
    fun deleteVariants(exerciseId: String) {
        runCatching {
            dir.listFiles { f -> f.name.startsWith("$exerciseId.") }?.forEach { it.delete() }
        }
    }
}
