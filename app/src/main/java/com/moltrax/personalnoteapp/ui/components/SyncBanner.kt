package com.moltrax.personalnoteapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.domain.model.SyncStatus
import com.moltrax.personalnoteapp.ui.SyncViewModel
import com.moltrax.personalnoteapp.ui.theme.AppColors
import kotlinx.coroutines.delay

/**
 * Global sync banner — shown above all screens. Reads state from the Activity-level
 * [SyncViewModel] (shared @Singleton repository).
 *
 * The "Synced" success message is only published on manual triggers or recovery from an error
 * (the repository decides) and is auto-cleared after a few seconds. Errors
 * stay persistent; "Detail" shows the full error text, "Retry" tries again.
 */
@Composable
fun SyncBanner(
    modifier: Modifier = Modifier,
    vm: SyncViewModel = hiltViewModel(),
) {
    val status by vm.syncStatus.collectAsStateWithLifecycle()
    SyncBannerContent(
        status = status,
        modifier = modifier,
        onSync = { vm.sync() },
        onAck = { vm.acknowledge() },
    )
}

@Composable
private fun SyncBannerContent(
    status: SyncStatus,
    modifier: Modifier,
    onSync: () -> Unit,
    onAck: () -> Unit,
) {
    LaunchedEffect(status) {
        if (status is SyncStatus.Synced) {
            delay(2500)
            onAck()
        }
    }

    val (text, color, isError) = when (status) {
        is SyncStatus.Syncing -> Triple(stringResource(R.string.sync_in_progress), MaterialTheme.colorScheme.primary, false)
        is SyncStatus.Synced  -> Triple(stringResource(R.string.sync_done), AppColors.Success, false)
        // Error: show the full raw message (HTTP code + message + exception type), no masking
        is SyncStatus.Error   -> Triple(status.message, AppColors.Error, true)
        else                  -> return
    }

    var showDetail by remember(status) { mutableStateOf(false) }

    Surface(
        color = color.copy(alpha = 0.15f),
        modifier = modifier.fillMaxWidth().then(
            if (isError) Modifier else Modifier.clickable(onClick = onSync)
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (isError) stringResource(R.string.sync_error_prefix, text) else text,
                modifier = Modifier.weight(1f).then(
                    if (isError) Modifier.clickable { showDetail = true } else Modifier
                ),
                style = MaterialTheme.typography.labelSmall, color = color,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (isError) {
                TextButton(onClick = { showDetail = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(stringResource(R.string.sync_detail), style = MaterialTheme.typography.labelSmall, color = color)
                }
                TextButton(onClick = onSync, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(stringResource(R.string.action_retry), style = MaterialTheme.typography.labelSmall, color = color)
                }
            }
        }
    }

    if (showDetail && status is SyncStatus.Error) {
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { showDetail = false },
            title = { Text(stringResource(R.string.sync_error_title)) },
            text = {
                // Full error text — selectable, so it can be copied and inspected
                SelectionContainer {
                    Text(status.message, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetail = false; onSync() }) { Text(stringResource(R.string.action_retry)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(status.message)) }) { Text(stringResource(R.string.action_copy)) }
                    TextButton(onClick = { showDetail = false }) { Text(stringResource(R.string.action_close)) }
                }
            },
        )
    }
}
