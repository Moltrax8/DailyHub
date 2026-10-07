package com.moltrax.personalnoteapp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.ui.UpdateViewModel

/**
 * Update prompt overlay (Phase 9, opt-in): shows when a newer GitHub release
 * is detected via Supabase (startup check or Realtime). Renders nothing when
 * the feature is off or no update is pending.
 */
@Composable
fun UpdatePrompt(vm: UpdateViewModel = hiltViewModel()) {
    val state by vm.ui.collectAsStateWithLifecycle()

    // Ask about a new version every time the app is opened (throttled in the ViewModel), so the prompt
    // appears by itself and nobody has to dig through Settings.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, vm) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) vm.checkOnAppOpen()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val candidate = state.candidate ?: return

    AlertDialog(
        onDismissRequest = { if (!state.downloading) vm.dismiss() },
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Column {
                Text(
                    stringResource(
                        R.string.update_available_body,
                        candidate.versionName,
                        vm.appVersionName(),
                    )
                )
                candidate.notes?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                if (state.downloading) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { (state.progress ?: 0) / 100f })
                    Text(stringResource(R.string.update_downloading, state.progress ?: 0))
                }
                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                if (state.needsUnknownSources) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.update_install_prompt))
                }
            }
        },
        confirmButton = {
            if (state.needsUnknownSources) {
                Row {
                    TextButton(onClick = { vm.openUnknownSourcesSettings() }) {
                        Text(stringResource(R.string.update_open_settings))
                    }
                    TextButton(onClick = { vm.retryInstall() }) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            } else {
                TextButton(
                    onClick = { vm.downloadAndInstall(candidate) },
                    enabled = !state.downloading,
                ) { Text(stringResource(R.string.update_download)) }
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.dismiss() }, enabled = !state.downloading) {
                Text(stringResource(R.string.update_later))
            }
        },
    )
}
