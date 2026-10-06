package com.moltrax.personalnoteapp.data.remote.drive

import com.moltrax.personalnoteapp.BuildConfig
import com.moltrax.personalnoteapp.data.remote.drive.model.SyncMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val DRIVE_BASE = "https://www.googleapis.com"
private const val UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
private const val MIME = "application/json"
private const val FILE_NAME = "tasks_sync.json"

data class DriveFile(val id: String)

@Singleton
class DriveApiService @Inject constructor(private val http: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    private fun auth(token: String) = "Bearer $token"

    // Extracts a meaningful message from the Drive v3 error body (falls back to the HTTP code).
    private fun Response.errorMessage(): String {
        val raw = runCatching { body?.string() }.getOrNull().orEmpty()
        val apiMsg = runCatching {
            JSONObject(raw).getJSONObject("error").optString("message")
        }.getOrNull()
        return "HTTP $code ${apiMsg.orEmpty().ifBlank { message }}".trim()
    }

    // All network calls run on Dispatchers.IO: OkHttp execute() is blocking, so no
    // NetworkOnMainThreadException even when called from the main thread.
    suspend fun findOrNull(token: String): DriveFile? = withContext(Dispatchers.IO) {
        // Drive API v3 only accepts "appDataFolder", "drive" or "photos" as spaces values.
        val primary = if (BuildConfig.DRIVE_SCOPE.contains("appdata")) "appDataFolder" else "drive"
        findIn(token, primary) ?: run {
            // "No file found" is a normal answer on the first sync. Only look in the other
            // space when the granted scope can reach it: the narrow drive.appdata scope can
            // ONLY access appDataFolder, and asking for "drive" then fails with
            // "HTTP 403 The granted scopes do not give access to all of the requested spaces",
            // which used to abort the very first sync. A full-drive token (primary == "drive")
            // can also read appDataFolder, and a failure there just means "not found".
            if (primary == "appDataFolder") null
            else runCatching { findIn(token, "appDataFolder") }.getOrNull()
        }
    }

    private fun findIn(token: String, spaces: String): DriveFile? {
        // NOTE: there is no "etag" field in v3; requesting it returns 400. We only ask for id.
        // trashed=false: do not find an old trashed backup with the same name.
        val url = "$DRIVE_BASE/drive/v3/files?spaces=$spaces&q=name='$FILE_NAME' and trashed=false&fields=files(id)"
        val req = Request.Builder().url(url).header("Authorization", auth(token)).get().build()
        return http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException(resp.errorMessage())
            val body = resp.body?.string() ?: return@use null
            val files = JSONObject(body).getJSONArray("files")
            if (files.length() == 0) return@use null
            DriveFile(files.getJSONObject(0).getString("id"))
        }
    }

    suspend fun download(token: String, fileId: String): SyncMetadata? = withContext(Dispatchers.IO) {
        val url = "$DRIVE_BASE/drive/v3/files/$fileId?alt=media"
        val req = Request.Builder().url(url).header("Authorization", auth(token)).get().build()
        http.newCall(req).execute().use { resp ->
            // 404: file deleted — will be recreated, not an error.
            if (resp.code == 404) return@use null
            if (!resp.isSuccessful) throw IOException(resp.errorMessage())
            // An empty 200 body is NOT "no backup" (it may be a transient network cut): return null
            // is wrong here — throw so the caller does not overwrite remote data with a push.
            val body = resp.body?.string().takeUnless { it.isNullOrEmpty() }
                ?: throw IOException("Boş Drive yedek gövdesi")
            json.decodeFromString<SyncMetadata>(body)
        }
    }

    data class UploadResult(val fileId: String)

    /**
     * Writes the backup file to Drive. Creates a new file when [existingFileId] is null,
     * otherwise updates its content. Throws on error (never swallows silently).
     *
     * No ETag/If-Match is USED for concurrency — Drive v3 has no etag. Multi-device
     * conflicts are resolved via the pull-merge (last-write-wins) step before sync.
     */
    suspend fun upload(
        token: String,
        metadata: SyncMetadata,
        existingFileId: String?,
    ): UploadResult = withContext(Dispatchers.IO) {
        val content = json.encodeToString(metadata)
        val mediaType = MIME.toMediaType()

        if (existingFileId == null) {
            // New file in appDataFolder — multipart (metadata + content)
            val metaJson = """{"name":"$FILE_NAME","parents":["appDataFolder"]}"""
            val boundary = "===boundary==="
            val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metaJson\r\n" +
                "--$boundary\r\nContent-Type: $MIME\r\n\r\n$content\r\n--$boundary--"
            val req = Request.Builder()
                .url("$UPLOAD_BASE/files?uploadType=multipart&fields=id")
                .header("Authorization", auth(token))
                .header("Content-Type", "multipart/related; boundary=$boundary")
                .post(body.toRequestBody())
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException(resp.errorMessage())
                val obj = JSONObject(resp.body?.string() ?: throw IOException("Boş Drive yanıtı"))
                UploadResult(obj.getString("id"))
            }
        } else {
            // Update the existing file
            val req = Request.Builder()
                .url("$UPLOAD_BASE/files/$existingFileId?uploadType=media&fields=id")
                .header("Authorization", auth(token))
                .header("Content-Type", MIME)
                .patch(content.toRequestBody(mediaType))
                .build()
            http.newCall(req).execute().use { resp ->
                // 404: file deleted — retry by creating a new file.
                if (resp.code == 404) return@use upload(token, metadata, null)
                if (!resp.isSuccessful) throw IOException(resp.errorMessage())
                val obj = JSONObject(resp.body?.string() ?: throw IOException("Boş Drive yanıtı"))
                UploadResult(obj.getString("id"))
            }
        }
    }
}
