package com.moltrax.personalnoteapp.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moltrax.personalnoteapp.BuildConfig
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
            val dest = File(File(context.cacheDir, "updates"), "DailyHub-${release.versionName}.apk")
            runCatching {
                updates.downloadApk(release.apkUrl, dest) { done, total ->
                    val pct = total?.let { (done * 100 / it).toInt().coerceIn(0, 100) }
                    _ui.update { s -> if (s.downloading) s.copy(progress = pct) else s }
                }
            }.onSuccess {
                _ui.update { it.copy(downloading = false, progress = 100) }
                install(dest)
            }.onFailure { e ->
                _ui.update { it.copy(downloading = false, error = e.message) }
            }
        }
    }

    private fun install(apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            _ui.update { it.copy(needsUnknownSources = true) }
            return
        }
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

    fun appVersionName(): String = BuildConfig.VERSION_NAME

    override fun onCleared() {
        subscription?.close()
        super.onCleared()
    }
}
