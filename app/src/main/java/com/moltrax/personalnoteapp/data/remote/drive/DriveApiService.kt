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

    // Drive v3 hata gövdesinden anlamlı mesaj çıkarır (yoksa HTTP kodunu kullanır).
    private fun Response.errorMessage(): String {
        val raw = runCatching { body?.string() }.getOrNull().orEmpty()
        val apiMsg = runCatching {
            JSONObject(raw).getJSONObject("error").optString("message")
        }.getOrNull()
        return "HTTP $code ${apiMsg.orEmpty().ifBlank { message }}".trim()
    }

    // Tüm ağ çağrıları Dispatchers.IO üzerinde çalışır: OkHttp execute() bloklayıcıdır,
    // Main thread'den çağrılsa bile NetworkOnMainThreadException oluşmaz.
    suspend fun findOrNull(token: String): DriveFile? = withContext(Dispatchers.IO) {
        // Drive API v3 yalnızca "appDataFolder", "drive" veya "photos" spaces değerlerini kabul eder.
        val primary = if (BuildConfig.DRIVE_SCOPE.contains("appdata")) "appDataFolder" else "drive"
        findIn(token, primary) ?: run {
            // Kapsam (scope) değişmiş olabilir: dosya diğer alanda duruyordur.
            val fallback = if (primary == "appDataFolder") "drive" else "appDataFolder"
            findIn(token, fallback)
        }
    }

    private fun findIn(token: String, spaces: String): DriveFile? {
        // NOT: v3'te "etag" diye bir alan yoktur; istemek 400 döndürür. Yalnızca id istiyoruz.
        // trashed=false: çöpteki aynı adlı eski yedek bulunmasın.
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
            // 404: dosya silinmiş — yeniden oluşturulacak, hata değil.
            if (resp.code == 404) return@use null
            if (!resp.isSuccessful) throw IOException(resp.errorMessage())
            // Boş 200 gövdesi "yedek yok" DEĞİLDİR (geçici ağ kesilmesi olabilir): null dönüp
            // uzaktaki veriyi ezmek yerine hata fırlat ki çağıran push yapmasın.
            val body = resp.body?.string().takeUnless { it.isNullOrEmpty() }
                ?: throw IOException("Boş Drive yedek gövdesi")
            json.decodeFromString<SyncMetadata>(body)
        }
    }

    data class UploadResult(val fileId: String)

    /**
     * Yedek dosyasını Drive'a yazar. [existingFileId] null ise yeni dosya oluşturur,
     * doluysa içeriğini günceller. Hata durumunda istisna fırlatır (sessizce yutmaz).
     *
     * Eşzamanlılık için ETag/If-Match KULLANILMAZ — Drive v3'te etag yoktur. Çoklu cihaz
     * çakışması, senkronizasyondan önce yapılan çek-birleştir (last-write-wins) adımıyla çözülür.
     */
    suspend fun upload(
        token: String,
        metadata: SyncMetadata,
        existingFileId: String?,
    ): UploadResult = withContext(Dispatchers.IO) {
        val content = json.encodeToString(metadata)
        val mediaType = MIME.toMediaType()

        if (existingFileId == null) {
            // appDataFolder'a yeni dosya — multipart (metadata + içerik)
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
            // Var olan dosyayı güncelle
            val req = Request.Builder()
                .url("$UPLOAD_BASE/files/$existingFileId?uploadType=media&fields=id")
                .header("Authorization", auth(token))
                .header("Content-Type", MIME)
                .patch(content.toRequestBody(mediaType))
                .build()
            http.newCall(req).execute().use { resp ->
                // 404: dosya silinmiş — yeni dosya oluşturmak için tekrar dener.
                if (resp.code == 404) return@use upload(token, metadata, null)
                if (!resp.isSuccessful) throw IOException(resp.errorMessage())
                val obj = JSONObject(resp.body?.string() ?: throw IOException("Boş Drive yanıtı"))
                UploadResult(obj.getString("id"))
            }
        }
    }
}
