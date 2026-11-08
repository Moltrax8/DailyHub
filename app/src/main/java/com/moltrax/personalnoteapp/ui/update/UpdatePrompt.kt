package com.moltrax.personalnoteapp.ui.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.domain.model.ReleaseNoteSection
import com.moltrax.personalnoteapp.domain.model.formatReleaseDate
import com.moltrax.personalnoteapp.domain.model.isSafeChangelogUrl
import com.moltrax.personalnoteapp.domain.model.parseReleaseNotes
import java.util.Locale

/**
 * Update prompt overlay: shows when a newer GitHub release
 * is detected by a public release check. Renders nothing when
 * the feature is off or no update is pending.
 *
 * Presented as a release-notes card: badge + version, headline, dated
 * emoji sections, changelog link and the download/later actions. The
 * download, checksum, signature and unknown-sources flows are unchanged.
 */
@Composable
fun UpdatePrompt(vm: UpdateViewModel = hiltViewModel()) {
    val state by vm.ui.collectAsStateWithLifecycle()

    // Ask about a new version every time the app is opened (throttled in the ViewModel), so the prompt
    // appears by itself and nobody has to dig through Settings.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, vm) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) vm.checkOnAppOpen()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val candidate = state.candidate ?: return

    val config = LocalConfiguration.current
    val context = LocalContext.current
    val notes = remember(candidate.notes) { parseReleaseNotes(candidate.notes) }
    val released = remember(candidate.publishedAt, notes.released, config) {
        formatReleaseDate(candidate.publishedAt, config.locales[0] ?: Locale.getDefault())
            ?: notes.released
    }
    val changelogUrl = notes.changelogUrl?.takeIf(::isSafeChangelogUrl)

    Dialog(onDismissRequest = { if (!state.downloading) vm.dismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.heightIn(max = 560.dp)) {
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 4.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = MaterialTheme.shapes.extraSmall,
                        ) {
                            Text(
                                stringResource(R.string.release_notes_badge),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                        Text(
                            stringResource(R.string.release_notes_version, candidate.versionName),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.release_notes_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    released?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.release_notes_released, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (notes.isEmpty) {
                        candidate.notes?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(12.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        notes.headline?.let {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        notes.intro?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        notes.sections.forEach { section ->
                            ReleaseNotesSection(section)
                        }
                    }

                    if (changelogUrl != null) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { openUrl(context, changelogUrl) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.release_notes_changelog),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    changelogUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                UpdatePromptActions(state = state, vm = vm)
            }
        }
    }
}

@Composable
private fun ReleaseNotesSection(section: ReleaseNoteSection) {
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        section.icon?.let {
            Text(it, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            localizedSectionTitle(section.title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
    section.items.forEach { item ->
        Spacer(Modifier.height(6.dp))
        Text(
            "•  $item",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UpdatePromptActions(
    state: UpdateUiState,
    vm: UpdateViewModel,
) {
    val candidate = state.candidate ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        if (state.downloading) {
            LinearProgressIndicator(
                progress = { (state.progress ?: 0) / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.update_downloading, state.progress ?: 0),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
        state.error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(12.dp))
        }
        if (state.needsUnknownSources) {
            Text(
                stringResource(R.string.update_install_prompt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { vm.openUnknownSourcesSettings() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.update_open_settings))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { vm.retryInstall() }) {
                    Text(stringResource(R.string.action_retry))
                }
                TextButton(onClick = { vm.dismiss() }) {
                    Text(stringResource(R.string.update_later))
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { vm.dismiss() },
                    enabled = !state.downloading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.update_later))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { vm.downloadAndInstall(candidate) },
                    enabled = !state.downloading,
                    modifier = Modifier.weight(1.5f),
                ) {
                    Text(stringResource(R.string.release_notes_download))
                }
            }
        }
    }
}

@Composable
private fun localizedSectionTitle(raw: String): String = when (raw.lowercase(Locale.ROOT)) {
    "new features", "features", "yenilikler", "yeni özellikler" ->
        stringResource(R.string.release_notes_section_features)
    "improvements", "changes", "iyileştirmeler" ->
        stringResource(R.string.release_notes_section_improvements)
    "bug fixes", "fixes", "hata düzeltmeleri" ->
        stringResource(R.string.release_notes_section_fixes)
    "security", "güvenlik" ->
        stringResource(R.string.release_notes_section_security)
    "important notes", "notes", "önemli notlar" ->
        stringResource(R.string.release_notes_section_notes)
    else -> raw
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
