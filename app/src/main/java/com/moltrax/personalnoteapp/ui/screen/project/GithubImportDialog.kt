package com.moltrax.personalnoteapp.ui.screen.project

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.domain.model.GithubAppRepo
import com.moltrax.personalnoteapp.domain.model.GithubImportTarget
import com.moltrax.personalnoteapp.domain.model.Space
import com.moltrax.personalnoteapp.ui.components.DhEmptyState
import com.moltrax.personalnoteapp.ui.components.DhErrorState
import com.moltrax.personalnoteapp.ui.components.DhLoadingRow
import com.moltrax.personalnoteapp.ui.components.DhSearchField
import com.moltrax.personalnoteapp.ui.components.DhSegmentedControl
import com.moltrax.personalnoteapp.ui.components.DhStatusChip
import com.moltrax.personalnoteapp.ui.github.GitHubCallbackBus
import com.moltrax.personalnoteapp.ui.theme.DhTokens

/**
 * Projects-list "Import from GitHub" dialog. Covers signed-out (sign-in
 * hint), GitHub-not-connected (connect flow with deep-link refresh) and the
 * connected picker (search, hide-forks chip, private/fork badges,
 * already-added locking, new-each vs. existing-project target, progress and
 * per-repo failures).
 */
@Composable
fun GithubImportDialog(
    vm: ProjectViewModel,
    projects: List<Space>,
    onDismiss: () -> Unit,
    onSignIn: () -> Unit,
    onResult: (imported: Int, failed: List<String>) -> Unit,
) {
    val st by vm.importState.collectAsStateWithLifecycle()
    val gh by vm.githubState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val openFailed = stringResource(R.string.github_open_failed)
    val untrusted = stringResource(R.string.github_untrusted_link)
    var urlError by remember { mutableStateOf<String?>(null) }
    val openUrl: (String) -> Unit = { url ->
        if (!com.moltrax.personalnoteapp.domain.model.isTrustedGitHubOpenUrl(url)) {
            urlError = untrusted
        } else {
            runCatching {
                context.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(url),
                    ),
                )
            }.onFailure { urlError = openFailed }
        }
    }

    LaunchedEffect(Unit) { vm.prepareImport() }

    // Deep-link finish: dailyhub://github-callback?code=...&state=... — the
    // same bus the per-project GitHub tab uses; here it reloads the import.
    val pendingCallback by GitHubCallbackBus.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingCallback) {
        val cb = pendingCallback ?: return@LaunchedEffect
        GitHubCallbackBus.consume()
        vm.finishImportLink(cb.code, cb.state)
    }

    // Clean finish closes the dialog; partial failures stay visible so the
    // user sees which repos failed without losing the successful ones.
    LaunchedEffect(st.done) {
        if (st.done) {
            onResult(st.importedCount, st.failedNames)
            vm.consumeImportResult()
            if (st.failedNames.isEmpty()) onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!st.busy) onDismiss() },
        title = {
            Text(
                stringResource(R.string.projects_import_github),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    st.loading -> {
                        DhLoadingRow(message = stringResource(R.string.loading))
                    }
                    !st.signedIn -> {
                        DhEmptyState(
                            icon = Icons.Default.Person,
                            title = stringResource(R.string.projects_import_sign_in_msg),
                            description = stringResource(R.string.profile_account_offline_desc),
                            actionLabel = stringResource(R.string.supabase_auth_sign_in),
                            onAction = onSignIn,
                        )
                    }
                    st.notConnected -> {
                        DhEmptyState(
                            icon = Icons.Default.Hub,
                            title = stringResource(R.string.github_not_connected_title),
                            description = stringResource(R.string.github_not_connected_desc),
                            actionLabel = stringResource(R.string.github_connect),
                            onAction = { vm.startGithubConnect(openUrl) },
                        )
                        if (gh.connectBusy) {
                            DhLoadingRow(message = stringResource(R.string.loading))
                        }
                        urlError?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    else -> {
                        Text(
                            stringResource(R.string.projects_import_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (projects.isNotEmpty()) {
                            DhSegmentedControl(
                                options = listOf(
                                    stringResource(R.string.projects_import_new_each),
                                    stringResource(R.string.projects_import_add_existing),
                                ),
                                selectedIndex = if (st.target == GithubImportTarget.NEW_PROJECT_EACH) 0 else 1,
                                onSelect = {
                                    vm.setImportTarget(
                                        if (it == 0) GithubImportTarget.NEW_PROJECT_EACH
                                        else GithubImportTarget.EXISTING_PROJECT,
                                    )
                                },
                            )
                            if (st.target == GithubImportTarget.EXISTING_PROJECT) {
                                ImportProjectDropdown(
                                    label = stringResource(R.string.projects_import_target),
                                    projects = projects,
                                    selectedId = st.targetProjectId,
                                    onSelect = { vm.setImportTargetProject(it) },
                                )
                            }
                        }
                        GithubRepoPickerList(
                            query = st.query,
                            onQueryChange = { vm.setImportQuery(it) },
                            queryLabel = stringResource(R.string.github_search_repos),
                            hideForks = st.hideForks,
                            onToggleHideForks = { vm.toggleImportHideForks() },
                            hideForksLabel = stringResource(R.string.github_hide_forks),
                            repos = st.visibleRepos,
                            selectedIds = st.selectedIds,
                            onToggle = { vm.toggleImportRepo(it) },
                            emptyLabel = stringResource(R.string.github_my_repos_empty),
                            lockedIds = st.alreadyAddedIds,
                            lockedLabel = stringResource(R.string.projects_already_added),
                        )
                        st.installUrl?.takeIf { it.isNotBlank() }?.let { installUrl ->
                            TextButton(
                                onClick = { openUrl(installUrl) },
                                modifier = Modifier.heightIn(min = DhTokens.MinTouchTarget),
                            ) { Text(stringResource(R.string.github_add_more)) }
                        }
                        urlError?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (st.busy) {
                            val progress = if (st.progressTotal > 0) {
                                st.progressDone.toFloat() / st.progressTotal
                            } else null
                            if (progress != null) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            Text(
                                stringResource(
                                    R.string.projects_import_progress,
                                    st.progressDone,
                                    st.progressTotal,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (st.failedNames.isNotEmpty()) {
                            Text(
                                stringResource(
                                    R.string.projects_import_failed,
                                    st.failedNames.joinToString(", "),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        st.error?.let { msg ->
                            DhErrorState(
                                title = stringResource(R.string.sync_error_title),
                                description = msg,
                                retryLabel = stringResource(R.string.action_retry),
                                onRetry = { vm.loadImport() },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (st.signedIn && !st.notConnected && !st.loading) {
                TextButton(
                    onClick = {
                        vm.runImport(projects.map { it.name.orEmpty() })
                    },
                    enabled = !st.busy &&
                        st.selectableCount > 0 &&
                        (st.target == GithubImportTarget.NEW_PROJECT_EACH || st.targetProjectId != null),
                    modifier = Modifier.heightIn(min = DhTokens.MinTouchTarget),
                ) {
                    if (st.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.projects_import_confirm, st.selectableCount))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !st.busy,
                modifier = Modifier.heightIn(min = DhTokens.MinTouchTarget),
            ) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Reusable account-repo picker block: search, hide-forks toggle and a
 * multi-select list with private/fork badges. Repos in [lockedIds] stay
 * visible with an "Already added" badge but are not selectable. Shared by
 * the per-project "My repos" dialog and the Projects-list import dialog.
 */
@Composable
fun GithubRepoPickerList(
    query: String,
    onQueryChange: (String) -> Unit,
    queryLabel: String,
    hideForks: Boolean,
    onToggleHideForks: () -> Unit,
    hideForksLabel: String,
    repos: List<GithubAppRepo>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    emptyLabel: String,
    modifier: Modifier = Modifier,
    lockedIds: Set<Long> = emptySet(),
    lockedLabel: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DhSearchField(
            value = query,
            onValueChange = onQueryChange,
            label = queryLabel,
            onClear = { onQueryChange("") },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.heightIn(min = DhTokens.MinTouchTarget),
        ) {
            Checkbox(
                checked = hideForks,
                onCheckedChange = { onToggleHideForks() },
            )
            Text(
                hideForksLabel,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (repos.isEmpty()) {
            Text(
                emptyLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(repos, key = { it.id }) { repo ->
                val locked = repo.id in lockedIds
                val checked = repo.id in selectedIds
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = DhTokens.MinTouchTarget)
                        .clickable(enabled = !locked) { onToggle(repo.id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!locked) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { onToggle(repo.id) },
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            repo.fullName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (locked) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        repo.description?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (repo.private) {
                                DhStatusChip(
                                    label = stringResource(R.string.github_private_badge),
                                )
                            }
                            if (repo.fork) {
                                DhStatusChip(
                                    label = stringResource(R.string.github_fork_badge),
                                )
                            }
                        }
                    }
                    if (locked && lockedLabel != null) {
                        DhStatusChip(label = lockedLabel)
                    }
                }
            }
        }
    }
}

/** Target-project dropdown for the "add to existing" import mode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportProjectDropdown(
    label: String,
    projects: List<Space>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val untitled = stringResource(R.string.projects_untitled)
    val selectedName = projects.firstOrNull { it.id == selectedId }
        ?.name?.takeIf { it.isNotBlank() } ?: untitled
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = if (selectedId == null) "" else selectedName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = MaterialTheme.shapes.small,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            projects.forEach { project ->
                val name = project.name?.takeIf { it.isNotBlank() } ?: untitled
                DropdownMenuItem(
                    text = {
                        Text(
                            name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = { expanded = false; onSelect(project.id) },
                    modifier = Modifier.heightIn(min = DhTokens.MinTouchTarget),
                )
            }
        }
    }
}
