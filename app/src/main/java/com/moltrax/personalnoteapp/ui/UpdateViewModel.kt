package com.moltrax.personalnoteapp.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moltrax.personalnoteapp.BuildConfig
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.data.repository.UpdateRepository
import com.moltrax.personalnoteapp.domain.model.AppRelease
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

data class UpdateUiState(
    val candidate: AppRelease? = null,
    val downloading: Boolean = false,
    val progress: Int? = null,
    val error: String? = null,
    val needsUnknownSources: Boolean = false,
)

/**
 * Automatic update prompter (Phase 9, opt-in). While enabled: a single
 * Supabase check at startup + a Realtime INSERT subscription while open.
 * Download streams the GitHub release APK to cache; install goes through
 * the system installer (unknown-sources consent when required).
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updates: UpdateRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _ui = MutableStateFlow(UpdateUiState())
    val ui: StateFlow<UpdateUiState> = _ui.asStateFlow()

    private var subscription: com.moltrax.personalnoteapp.data.remote.supabase.RealtimeClient.Subscription? = null
    private var downloadJob: Job? = null
    private var snoozedCode: Int? = null
    /** Last downloaded APK retained so install can be retried after unknown-sources consent. */
    private var lastApk: File? = null

    init {
        viewModelScope.launch {
            updates.observeEnabled().collect { enabled ->
                subscription?.close()
                subscription = null
                if (!enabled) {
                    _ui.update { UpdateUiState() }
                    snoozedCode = null
                    return@collect
                }
                // Single startup check (covers "app was closed at release time").
                runCatching { updates.checkNow() }.getOrNull()?.let { offer(it) }
                // Live notices while open (covers "app is open at release time").
                subscription = updates.subscribeReleases { offer(it) }
            }
        }
    }

    private fun offer(release: AppRelease) {
        if (!updates.isNewerThanInstalled(release)) return
        if (snoozedCode == release.versionCode) return
        if (_ui.value.downloading) return
        _ui.update { it.copy(candidate = release, error = null, needsUnknownSources = false) }
    }

    fun dismiss() {
        snoozedCode = _ui.value.candidate?.versionCode
        _ui.update { it.copy(candidate = null, error = null, needsUnknownSources = false) }
    }

    fun openUnknownSourcesSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startActivity(
                Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun downloadAndInstall(release: AppRelease) {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            _ui.update { it.copy(downloading = true, progress = 0, error = null) }
            // Fixed filename: never interpolate the server-controlled versionName (path traversal).
            val dest = File(File(context.cacheDir, "updates"), "update.apk")
            runCatching {
                updates.downloadApk(release.apkUrl, dest) { done, total ->
                    val pct = total?.let { (done * 100 / it).toInt().coerceIn(0, 100) }
                    _ui.update { s -> if (s.downloading) s.copy(progress = pct) else s }
                }
            }.onSuccess {
                _ui.update { it.copy(downloading = false, progress = 100) }
                install(dest)
            }.onFailure { e ->
                lastApk = null
                val msg = if ((e.message ?: "").contains("too large", ignoreCase = true)) {
                    context.getString(R.string.update_file_too_large)
                } else {
                    e.message
                }
                _ui.update { it.copy(downloading = false, error = msg) }
            }
        }
    }

    /** Re-runs the installer for the already-downloaded file (no re-download). */
    fun retryInstall() {
        val f = lastApk
        if (f == null || !f.exists()) return
        install(f)
    }

    private fun install(apk: File) {
        lastApk = apk
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            _ui.update { it.copy(needsUnknownSources = true) }
            return
        }
        if (!isTrustedApk(apk)) {
            runCatching { if (apk.exists()) apk.delete() }
            lastApk = null
            _ui.update { it.copy(error = context.getString(R.string.update_signature_mismatch)) }
            return
        }
        _ui.update { it.copy(needsUnknownSources = false) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure { e ->
            _ui.update { it.copy(error = e.message) }
        }
    }

    /**
     * Verifies the downloaded APK really is this app: package name must match
     * and one signing-certificate SHA-256 digest must match the installed app.
     */
    private fun isTrustedApk(apk: File): Boolean {
        return runCatching {
            val pm = context.packageManager
            val archive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)
            } ?: return false
            if (archive.packageName != context.packageName) return false
            val archiveSigs = archiveSignatures(archive)
            if (archiveSigs.isEmpty()) return false
            val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
            val installedSigs = archiveSignatures(installed)
            if (installedSigs.isEmpty()) return false
            val archiveDigests = archiveSigs.map { sha256(it.toByteArray()) }.toSet()
            val installedDigests = installedSigs.map { sha256(it.toByteArray()) }.toSet()
            archiveDigests.intersect(installedDigests).isNotEmpty()
        }.getOrDefault(false)
    }

    private fun archiveSignatures(info: android.content.pm.PackageInfo): List<android.content.pm.Signature> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val si = info.signingInfo
            if (si == null) {
                @Suppress("DEPRECATION")
                info.signatures?.toList().orEmpty()
            } else {
                val current = si.apkContentsSigners?.toList().orEmpty()
                if (current.isNotEmpty()) current else si.signingCertificateHistory?.toList().orEmpty()
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.toList().orEmpty()
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(bytes)
        return d.joinToString("") { "%02x".format(it) }
    }

    fun appVersionName(): String = BuildConfig.VERSION_NAME

    override fun onCleared() {
        subscription?.close()
        super.onCleared()
    }
}
