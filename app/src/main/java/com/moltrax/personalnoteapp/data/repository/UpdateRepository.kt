package com.moltrax.personalnoteapp.data.repository

import com.moltrax.personalnoteapp.BuildConfig
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.domain.model.AppRelease
import com.moltrax.personalnoteapp.domain.model.isAllowedApkHost
import com.moltrax.personalnoteapp.domain.model.isDailyHubReleaseApk
import com.moltrax.personalnoteapp.domain.model.isUpdateAvailable
import com.moltrax.personalnoteapp.domain.model.parseGithubLatestRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Automatic update checks against the public GitHub Releases API.
 * One request per app open (throttled by the ViewModel); no polling.
 */
@Singleton
class UpdateRepository @Inject constructor(
    private val http: OkHttpClient,
    private val prefs: AppPreferences,
) {
    // A network interceptor runs before EVERY redirect hop, not just the final response.
    private val downloadHttp = http.newBuilder().addNetworkInterceptor { chain ->
        if (!isAllowedApkHost(chain.request().url.toString())) throw IOException("Untrusted update redirect.")
        chain.proceed(chain.request())
    }.build()
    private val _latest = MutableStateFlow<AppRelease?>(null)
    val latest: Flow<AppRelease?> = _latest.asStateFlow()

    fun observeEnabled(): Flow<Boolean> = prefs.autoUpdate.distinctUntilChanged()

    suspend fun setEnabled(v: Boolean) = prefs.setAutoUpdate(v)

    /** One GitHub release check. Null when unavailable or the response is unusable. */
    suspend fun checkNow(): AppRelease? {
        val release = fetchLatestFromGitHub()
        if (release != null) _latest.update { release }
        return release
    }

    private suspend fun fetchLatestFromGitHub(): AppRelease? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/Moltrax8/DailyHub/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        try {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                parseGithubLatestRelease(resp.body?.string().orEmpty())
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }

    fun isNewerThanInstalled(release: AppRelease, installedCode: Int = BuildConfig.VERSION_CODE): Boolean =
        isUpdateAvailable(release, installedCode)

    companion object {
        /** Hard cap for APK downloads: abort and delete the partial file past this. */
        const val MAX_APK_BYTES = 200L * 1024 * 1024
    }

    /** Streams the APK to [dest] (cache). Throws on HTTP/network errors. */
    suspend fun downloadApk(apkUrl: String, dest: File, expectedSha256: String? = null, onProgress: (Long, Long?) -> Unit = { _, _ -> }) {
        withContext(Dispatchers.IO) {
            require(isDailyHubReleaseApk(apkUrl)) { "Refusing untrusted release URL." }
            require(expectedSha256 == null || Regex("[0-9a-f]{64}").matches(expectedSha256)) { "Invalid update checksum." }
            val req = Request.Builder().url(apkUrl).get().build()
            try {
                downloadHttp.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("Download failed (HTTP ${resp.code}).")
                    val body = resp.body ?: throw IOException("Empty download.")
                    val total = body.contentLength().takeIf { it > 0 }
                    if (total != null && total > MAX_APK_BYTES) throw IOException("Update file too large.")
                    dest.parentFile?.mkdirs()
                    val digest = MessageDigest.getInstance("SHA-256")
                    var done = 0L
                    dest.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        while (body.byteStream().read(buf).also { read = it } != -1) {
                            currentCoroutineContext().ensureActive()
                            done += read
                            if (done > MAX_APK_BYTES) throw IOException("Update file too large.")
                            out.write(buf, 0, read)
                            digest.update(buf, 0, read)
                            onProgress(done, total)
                        }
                    }
                    if (done == 0L || (total != null && done != total)) throw IOException("Incomplete update download.")
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (expectedSha256 != null && actual != expectedSha256) throw IOException("Update checksum mismatch.")
                }
            } catch (e: Exception) {
                runCatching { if (dest.exists()) dest.delete() }
                throw e
            }
        }
    }
}
