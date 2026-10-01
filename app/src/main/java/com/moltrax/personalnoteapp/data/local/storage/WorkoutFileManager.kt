package com.moltrax.personalnoteapp.data.local.storage

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage Access Framework file operations for portable workout JSON documents.
 *
 * No broad storage permissions: access is granted per-document by the system picker
 * ([ActivityResultContracts.OpenDocument] / [CreateDocument]). All I/O runs on
 * [Dispatchers.IO] with `use`-closed streams.
 */
@Singleton
class WorkoutFileManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        /** Refuse absurd inputs before they can jank the UI thread on decode. */
        const val MAX_JSON_BYTES = 2L * 1024 * 1024
    }

    /** Reads the full UTF-8 text of a user-picked document. Null streams/bad IO → failure. */
    suspend fun readText(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Belge açılamadı")
            input.use {
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0L
                while (true) {
                    val n = it.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_JSON_BYTES) throw IOException("Dosya çok büyük")
                    out.write(buf, 0, n)
                }
                out.toString(Charsets.UTF_8.name())
            }
        }
    }

    /** Overwrites a user-chosen document with UTF-8 text. */
    suspend fun writeText(uri: Uri, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val output = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("Belge yazılamadı")
            output.use {
                it.write(text.toByteArray(Charsets.UTF_8))
                it.flush()
            }
        }
    }
}
