package com.moltrax.personalnoteapp.ui.screen.project

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.moltrax.personalnoteapp.ui.components.DhAvatarStack
import com.moltrax.personalnoteapp.ui.components.DhCard
import com.moltrax.personalnoteapp.ui.components.DhConfirmDialog
import com.moltrax.personalnoteapp.ui.components.DhDivider
import com.moltrax.personalnoteapp.ui.components.DhEmptyState
import com.moltrax.personalnoteapp.ui.components.DhErrorState
import com.moltrax.personalnoteapp.ui.components.DhFab
import com.moltrax.personalnoteapp.ui.components.DhFormDialog
import com.moltrax.personalnoteapp.ui.components.DhLoadingRow
import com.moltrax.personalnoteapp.ui.components.DhScrollTabs
import com.moltrax.personalnoteapp.ui.components.DhSection
import com.moltrax.personalnoteapp.ui.components.DhSettingsRow
import com.moltrax.personalnoteapp.ui.components.DhStatusChip
import com.moltrax.personalnoteapp.ui.components.DhTextField
import com.moltrax.personalnoteapp.ui.components.DhTonalIcon
import com.moltrax.personalnoteapp.ui.components.DhTopBar
import com.moltrax.personalnoteapp.ui.screen.space.SpaceViewModel
import com.moltrax.personalnoteapp.ui.screen.social.SocialViewModel
import com.moltrax.personalnoteapp.ui.components.DhSwitch
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.domain.model.ProjectItem
import com.moltrax.personalnoteapp.domain.model.ProjectStatus
import com.moltrax.personalnoteapp.domain.model.groupBoardItems
import com.moltrax.personalnoteapp.ui.navigation.DuoHub
import com.moltrax.personalnoteapp.ui.navigation.ProjectDetail
import com.moltrax.personalnoteapp.ui.navigation.SupabaseAuth
import kotlinx.coroutines.launch

private val COLUMNS = listOf(
    ProjectStatus.IDEA to "Idea",
    ProjectStatus.PLANNED to "Planned",
    ProjectStatus.DEVELOPING to "Developing",
    ProjectStatus.FINISHED to "Finished",
)

/** Project containers (Phase 6). Each is a PROJECT space with a board. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(nav: NavController, vm: ProjectViewModel = hiltViewModel()) {
    val projects by vm.myProjects.collectAsStateWithLifecycle()
    val repoCounts by vm.repoCounts.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(projects.map { it.id }) { vm.refreshRepoCounts() }

    fun showImportResult(imported: Int, failed: List<String>) {
        val msg = when {
            imported > 0 && failed.isEmpty() ->
                context.resources.getQuantityString(R.plurals.projects_imported, imported, imported)
            imported > 0 ->
                context.getString(R.string.projects_import_partial, imported, failed.size)
            else ->
                context.getString(R.string.projects_import_failed, failed.joinToString(", "))
        }
        scope.launch { snackbar.showSnackbar(msg) }
    }

    Scaffold(
        topBar = { DhTopBar(title = stringResource(R.string.projects_title)) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            Box {
                DhFab(onClick = { showFabMenu = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
                }
                DropdownMenu(expanded = showFabMenu, onDismissRequest = { showFabMenu = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.projects_new),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = { showFabMenu = false; showCreate = true },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.projects_import_github),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Hub, contentDescription = null) },
                        onClick = { showFabMenu = false; showImport = true },
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (projects.isEmpty()) {
                item {
                    DhEmptyState(
                        icon = Icons.Default.Dashboard,
                        title = stringResource(R.string.projects_empty_title),
                        description = stringResource(R.string.projects_empty_desc),
                        actionLabel = stringResource(R.string.projects_new),
                        onAction = { showCreate = true },
                    )
                    OutlinedButton(
                        onClick = { showImport = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(
                            stringResource(R.string.projects_import_github),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            items(projects, key = { it.id }) { space ->
                ProjectListCard(
                    spaceId = space.id,
                    spaceName = space.name,
                    repoCount = repoCounts[space.id] ?: 0,
                    vm = vm,
                    onOpen = { nav.navigate(ProjectDetail(space.id)) },
                )
            }
        }
    }

    if (showImport) {
        GithubImportDialog(
            vm = vm,
            projects = projects,
            onDismiss = { showImport = false },
            onSignIn = { showImport = false; nav.navigate(SupabaseAuth) },
            onResult = { imported, failed -> showImportResult(imported, failed) },
        )
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        DhFormDialog(
            title = stringResource(R.string.projects_new),
            onDismiss = { showCreate = false },
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = {
                vm.createProject(name.trim(), desc) { id ->
                    showCreate = false
                    nav.navigate(ProjectDetail(id))
                }
            },
            dismissLabel = stringResource(R.string.action_cancel),
            confirmEnabled = name.isNotBlank(),
            message = stringResource(R.string.projects_new_hint),
            modifier = Modifier.imePadding(),
            content = {
                DhTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.projects_name),
                    singleLine = true,
                )
                DhTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = stringResource(R.string.projects_description),
                    singleLine = false,
                )
            },
        )
    }
}

/** Project list card: name + description + repo chip + chevron (board/repo detail lives in the hub). */
@Composable
private fun ProjectListCard(
    spaceId: String,
    spaceName: String?,
    repoCount: Int,
    vm: ProjectViewModel,
    onOpen: () -> Unit,
) {
    // Read-only lookups (no behaviour change): description for the supporting line.
    val project by remember(spaceId) { vm.observeProject(spaceId) }
        .collectAsStateWithLifecycle(initialValue = null)
    DhCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DhTonalIcon(Icons.Default.Dashboard, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    spaceName?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.projects_untitled),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                project?.descriptionMd?.takeIf { it.isNotBlank() }?.let { desc ->
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (repoCount > 0) {
                    DhStatusChip(
                        label = pluralStringResource(R.plurals.projects_repo_count, repoCount, repoCount),
                        icon = Icons.Default.Hub,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One project: lightweight board (Idea → Planned → Developing → Finished). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProjectDetailScreen(
    spaceId: String,
    nav: NavController,
    vm: ProjectViewModel = hiltViewModel(),
    spaceVm: SpaceViewModel = hiltViewModel(),
    socialVm: SocialViewModel = hiltViewModel(),
) {
    val state by vm.detail.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ProjectItem?>(null) }
    var mainTab by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(spaceId) { vm.openProject(spaceId); vm.loadGitHub(spaceId) }
    // Read-only hub identity (name + members); the board itself stays on ProjectViewModel.
    LaunchedEffect(spaceId) { spaceVm.openSpace(spaceId); socialVm.refresh() }
    val spaceState by spaceVm.detail.collectAsStateWithLifecycle()
    val gh by vm.githubState.collectAsStateWithLifecycle()
    val board = groupBoardItems(state.items)
    val hubName = spaceState.space?.name?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.projects_board)
    val memberNames = spaceState.members.map { socialVm.displayNameOf(it.userId) }

    Scaffold(
        topBar = {
            DhTopBar(
                title = hubName,
                onBack = { nav.popBackStack() },
                backContentDescription = stringResource(R.string.action_back),
                actions = {
                    IconButton(onClick = { nav.navigate(DuoHub(spaceId)) }) {
                        Icon(Icons.Default.Hub, contentDescription = stringResource(R.string.projects_open_hub))
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.projects_delete)) },
                                onClick = { showMenu = false; showDelete = true },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (mainTab == 0) {
                DhFab(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ProjectHubHeader(
                memberNames = memberNames,
                repos = gh.repos.map { it.fullName },
            )
            DhScrollTabs(
                options = listOf(
                    stringResource(R.string.projects_board),
                    stringResource(R.string.projects_github),
                ),
                selectedIndex = mainTab,
                onSelect = { mainTab = it },
            )
            if (mainTab == 0) {
                BoardTab(vm = vm, spaceId = spaceId, state = state, board = board,
                    onAdd = { showAdd = true }, onSelect = { selected = it })
            } else {
                GitHubTab(vm = vm, spaceId = spaceId)
            }
        }
    }

    if (showAdd) {
        var title by remember { mutableStateOf("") }
        DhFormDialog(
            title = stringResource(R.string.projects_new_item),
            onDismiss = { showAdd = false },
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { vm.addItem(spaceId, title.trim()) { showAdd = false } },
            dismissLabel = stringResource(R.string.action_cancel),
            confirmEnabled = title.isNotBlank(),
            modifier = Modifier.imePadding(),
            content = {
                DhTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = stringResource(R.string.task_title_field),
                    singleLine = true,
                )
            },
        )
    }

    selected?.let { item ->
        ItemDetailDialog(
            spaceId = spaceId,
            item = item,
            onDismiss = { selected = null },
        )
    }

    if (showDelete) {
        DhConfirmDialog(
            title = stringResource(R.string.projects_delete_title),
            message = stringResource(R.string.projects_delete_confirm),
            confirmLabel = stringResource(R.string.projects_delete),
            onConfirm = {
                showDelete = false
                vm.deleteProject(spaceId) { nav.popBackStack() }
            },
            onDismiss = { showDelete = false },
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
}

/** Hub identity strip: member avatars + linked repo chips (read-only summary). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectHubHeader(
    memberNames: List<String>,
    repos: List<String>,
) {
    if (memberNames.isEmpty() && repos.isEmpty()) return
    DhCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (memberNames.isNotEmpty()) {
                    Text(
                        stringResource(R.string.projects_hub_members),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(4.dp))
                    DhAvatarStack(names = memberNames)
                }
                if (repos.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = if (memberNames.isNotEmpty()) 8.dp else 0.dp),
                    ) {
                        repos.take(3).forEach { fullName ->
                            DhStatusChip(label = fullName)
                        }
                        if (repos.size > 3) {
                            DhStatusChip(label = "+${repos.size - 3}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardTab(
    vm: ProjectViewModel,
    spaceId: String,
    state: com.moltrax.personalnoteapp.ui.screen.project.ProjectDetailUiState,
    board: Map<String, List<ProjectItem>>,
    onAdd: () -> Unit,
    onSelect: (ProjectItem) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<ProjectItem?>(null) }
    if (state.busy) DhLoadingRow(message = stringResource(R.string.loading))
    state.error?.let {
        DhErrorState(
            title = stringResource(R.string.sync_error_title),
            description = it,
            retryLabel = stringResource(R.string.action_retry),
            onRetry = { vm.openProject(spaceId) },
        )
    }
    // Mobile-first board: vertical status sections with a "Move to" status
    // picker per card (accessible, no tiny arrow buttons). Wide screens get
    // the same sections in a horizontal scroll row via adaptive layout below.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        if (wide) {
            Row(
                Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                COLUMNS.forEach { (status, label) ->
                    androidx.compose.foundation.layout.Box(Modifier.width(300.dp)) {
                        BoardSection(
                            label = label,
                            items = board[status.name.lowercase().replaceFirstChar { it.uppercase() }].orEmpty(),
                            current = status,
                            onMove = { item, target -> vm.moveItem(spaceId, item, target) },
                            onTap = onSelect,
                            onDelete = { pendingDelete = it },
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                COLUMNS.forEach { (status, label) ->
                    item(key = status.name) {
                        BoardSection(
                            label = label,
                            items = board[status.name.lowercase().replaceFirstChar { it.uppercase() }].orEmpty(),
                            current = status,
                            onMove = { item, target -> vm.moveItem(spaceId, item, target) },
                            onTap = onSelect,
                            onDelete = { pendingDelete = it },
                        )
                    }
                }
            }
        }
    }
    pendingDelete?.let { item ->
        DhConfirmDialog(
            title = stringResource(R.string.projects_card_delete_title),
            message = stringResource(R.string.projects_card_delete_confirm, item.title),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { pendingDelete = null; vm.deleteItem(spaceId, item.id) },
            onDismiss = { pendingDelete = null },
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
}

/** GitHub tab (Phase 7): account link, linked repos, per-kind toggles, activity feed. */
@Composable
private fun GitHubTab(vm: ProjectViewModel, spaceId: String) {
    val gh by vm.githubState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showMine by remember { mutableStateOf(false) }
    var showBrowse by remember { mutableStateOf(false) }
    var showDisconnect by remember { mutableStateOf(false) }
    val openFailed = stringResource(R.string.github_open_failed)
    val untrusted = stringResource(R.string.github_untrusted_link)
    val openUrl: (String) -> Unit = { url ->
        if (!com.moltrax.personalnoteapp.domain.model.isTrustedGitHubOpenUrl(url)) {
            vm.reportGithubError(untrusted)
        } else {
            runCatching {
                context.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(url),
                    ),
                )
            }.onFailure { vm.reportGithubError(openFailed) }
        }
    }

    // Deep link finish: dailyhub://github-callback?code=...&state=... (from MainActivity).
    val pendingCallback by com.moltrax.personalnoteapp.ui.github.GitHubCallbackBus.pending
        .collectAsStateWithLifecycle()
    LaunchedEffect(pendingCallback) {
        val cb = pendingCallback ?: return@LaunchedEffect
        com.moltrax.personalnoteapp.ui.github.GitHubCallbackBus.consume()
        vm.finishGithubLink(spaceId, cb.code, cb.state)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            DhSection(title = stringResource(R.string.projects_section_connection)) {
                if (gh.connected?.githubLogin == null) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.github_connect_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { vm.startGithubConnect(openUrl) },
                            enabled = !gh.connectBusy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            if (gh.connectBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(stringResource(R.string.github_connect), maxLines = 1)
                        }
                    }
                } else {
                    DhSettingsRow(
                        title = stringResource(
                            R.string.github_connected_as,
                            "@${gh.connected!!.githubLogin!!}",
                        ),
                        leading = {
                            DhTonalIcon(Icons.Default.Hub, contentDescription = null)
                        },
                        trailing = {
                            TextButton(
                                onClick = { showDisconnect = true },
                                enabled = !gh.connectBusy,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text(stringResource(R.string.github_disconnect), maxLines = 1) }
                        },
                    )
                }
            }
        }
        if (gh.busy) {
            item {
                DhLoadingRow(message = stringResource(R.string.loading))
            }
        }
        item {
            DhSection(
                title = stringResource(R.string.github_repos),
                subtitle = if (gh.repos.isEmpty()) null
                    else stringResource(R.string.projects_open_count, gh.repos.size),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { showMine = true },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.github_my_repos_title), maxLines = 1) }
                    OutlinedButton(
                        onClick = { showBrowse = true },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.github_browse), maxLines = 1) }
                }
                if (gh.repos.isEmpty() && !gh.busy) {
                    Text(
                        stringResource(R.string.github_browse_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                gh.repos.forEachIndexed { index, repo ->
                    if (index > 0) DhDivider()
                    DhSettingsRow(
                        title = repo.fullName,
                        leading = {
                            DhTonalIcon(Icons.Default.Hub, contentDescription = null)
                        },
                        trailing = {
                            IconButton(
                                onClick = { vm.unlinkRepo(spaceId, repo.id) },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.github_unlink),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
        }
        item {
            DhSection(title = stringResource(R.string.github_prefs)) {
                com.moltrax.personalnoteapp.domain.repository.GitHubRepository.KINDS
                    .forEachIndexed { index, kind ->
                        if (index > 0) DhDivider()
                        val enabled = gh.prefs[kind] ?: true
                        DhSettingsRow(
                            title = com.moltrax.personalnoteapp.domain.model.githubActivityTitle(kind),
                            trailing = {
                                DhSwitch(
                                    checked = enabled,
                                    onCheckedChange = { vm.togglePref(spaceId, kind, it) },
                                )
                            },
                            onClick = { vm.togglePref(spaceId, kind, !enabled) },
                        )
                    }
            }
        }
        item {
            OutlinedButton(
                onClick = { vm.enablePush() },
                enabled = !gh.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(
                    stringResource(
                        if (gh.pushDone) R.string.github_push_done
                        else R.string.github_enable_push
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item {
            DhSection(title = stringResource(R.string.github_activity)) {
                if (gh.activity.isEmpty()) {
                    Text(
                        stringResource(R.string.github_activity_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                gh.activity.forEachIndexed { index, act ->
                    if (index > 0) DhDivider()
                    DhSettingsRow(
                        title = com.moltrax.personalnoteapp.domain.model.githubActivityTitle(act.kind),
                        supporting = buildString {
                            val summary = act.summary()
                            if (summary != act.kind) append(summary)
                            act.repoFull?.let {
                                if (isNotEmpty()) append(" · ")
                                append(it)
                            }
                        }.takeIf { it.isNotBlank() },
                    )
                }
            }
        }
        gh.error?.let { msg ->
            item {
                DhErrorState(
                    title = stringResource(R.string.sync_error_title),
                    description = msg,
                    retryLabel = stringResource(R.string.action_retry),
                    onRetry = { vm.loadGitHub(spaceId); vm.loadAppRepos(spaceId) },
                )
            }
        }
    }

    if (showDisconnect) {
        DhConfirmDialog(
            title = stringResource(R.string.github_disconnect_title),
            message = stringResource(R.string.github_disconnect_confirm),
            confirmLabel = stringResource(R.string.github_disconnect),
            onConfirm = { showDisconnect = false; vm.disconnectGithub(spaceId) },
            onDismiss = { showDisconnect = false },
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }

    if (showMine) {
        MyReposDialog(spaceId = spaceId, vm = vm, onDismiss = { showMine = false })
    }

    if (showBrowse) {
        BrowseReposDialog(spaceId = spaceId, vm = vm, onDismiss = { showBrowse = false })
    }
}

/** The linked account's repos BY NAME: search, private/fork badges, multi-select. */
@Composable
private fun MyReposDialog(spaceId: String, vm: ProjectViewModel, onDismiss: () -> Unit) {
    val state by vm.appReposState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val openFailed = stringResource(R.string.github_open_failed)
    val untrusted = stringResource(R.string.github_untrusted_link)

    LaunchedEffect(spaceId) { vm.loadAppRepos(spaceId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.github_my_repos_title),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    state.busy && state.repos.isEmpty() -> {
                        com.moltrax.personalnoteapp.ui.components.DhLoadingRow(
                            message = stringResource(R.string.loading),
                        )
                    }
                    state.notConnected -> {
                        com.moltrax.personalnoteapp.ui.components.DhEmptyState(
                            icon = Icons.Default.Hub,
                            title = stringResource(R.string.github_not_connected_title),
                            description = stringResource(R.string.github_not_connected_desc),
                            actionLabel = stringResource(R.string.github_connect),
                            onAction = {
                                vm.startGithubConnect { url ->
                                    if (!com.moltrax.personalnoteapp.domain.model.isTrustedGitHubOpenUrl(url)) {
                                        vm.reportGithubError(untrusted)
                                    } else {
                                        runCatching {
                                            context.startActivity(
                                                android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse(url),
                                                ),
                                            )
                                        }.onFailure { vm.reportGithubError(openFailed) }
                                    }
                                }
                            },
                        )
                    }
                    state.error != null -> {
                        com.moltrax.personalnoteapp.ui.components.DhErrorState(
                            title = stringResource(R.string.github_my_repos_title),
                            description = state.error!!,
                            retryLabel = stringResource(R.string.action_retry),
                            onRetry = { vm.loadAppRepos(spaceId) },
                        )
                    }
                    else -> {
                        Text(
                            stringResource(R.string.github_my_repos_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        GithubRepoPickerList(
                            query = state.query,
                            onQueryChange = { vm.setAppRepoQuery(it) },
                            queryLabel = stringResource(R.string.github_search_repos),
                            hideForks = state.hideForks,
                            onToggleHideForks = { vm.toggleHideAppForks() },
                            hideForksLabel = stringResource(R.string.github_hide_forks),
                            repos = state.visibleRepos,
                            selectedIds = state.selectedIds,
                            onToggle = { vm.toggleAppRepo(it) },
                            emptyLabel = stringResource(R.string.github_my_repos_empty),
                        )
                        state.installUrl?.takeIf { it.isNotBlank() }?.let { installUrl ->
                            TextButton(
                                onClick = {
                                    if (!com.moltrax.personalnoteapp.domain.model.isTrustedGitHubOpenUrl(installUrl)) {
                                        vm.reportGithubError(untrusted)
                                    } else {
                                        runCatching {
                                            context.startActivity(
                                                android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse(installUrl),
                                                ),
                                            )
                                        }.onFailure { vm.reportGithubError(openFailed) }
                                    }
                                },
                                modifier = Modifier.heightIn(min = com.moltrax.personalnoteapp.ui.theme.DhTokens.MinTouchTarget),
                            ) { Text(stringResource(R.string.github_add_more)) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val freshCount = state.selectedIds.minus(state.trackedIds).size
            TextButton(
                onClick = { vm.applyAppRepos(spaceId) { onDismiss() } },
                enabled = !state.busy && !state.notConnected && state.repos.isNotEmpty(),
                modifier = Modifier.heightIn(min = com.moltrax.personalnoteapp.ui.theme.DhTokens.MinTouchTarget),
            ) { Text(stringResource(R.string.github_link_selected, freshCount)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun BrowseReposDialog(spaceId: String, vm: ProjectViewModel, onDismiss: () -> Unit) {
    val browse by vm.browseState.collectAsStateWithLifecycle()
    var username by remember { mutableStateOf(browse.username) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.github_browse_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.imePadding(),
            ) {
                Text(
                    stringResource(R.string.github_browse_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.github_browse_user)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedButton(
                        onClick = { vm.fetchBrowseRepos(spaceId, username) },
                        enabled = !browse.busy && username.isNotBlank(),
                    ) { Text(stringResource(R.string.github_browse_fetch)) }
                }
                if (browse.busy) {
                    CircularProgressIndicator(modifier = Modifier.padding(vertical = 8.dp))
                }
                browse.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (!browse.busy && browse.error == null && browse.repos.isEmpty() && browse.username.isNotBlank()) {
                    Text(
                        stringResource(R.string.github_browse_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (browse.repos.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = browse.hideForks,
                            onCheckedChange = { vm.toggleHideForks() },
                        )
                        Text(
                            stringResource(R.string.github_hide_forks),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(browse.visibleRepos, key = { it.id }) { repo ->
                        val checked = repo.id in browse.selectedIds
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { vm.toggleBrowseRepo(repo.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = checked,
                                onCheckedChange = { vm.toggleBrowseRepo(repo.id) },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(repo.fullName, style = MaterialTheme.typography.bodyMedium)
                                repo.description?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                                if (repo.fork) {
                                    DhStatusChip(
                                        label = stringResource(R.string.github_fork_badge),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.applyBrowseRepos(spaceId) { onDismiss() } },
                enabled = !browse.busy && browse.repos.isNotEmpty(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun BoardSection(
    label: String,
    items: List<ProjectItem>,
    current: ProjectStatus,
    onMove: (ProjectItem, ProjectStatus) -> Unit,
    onTap: (ProjectItem) -> Unit,
    onDelete: (ProjectItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DhStatusChip(label = items.size.toString())
        }
        if (items.isEmpty()) {
            Text(
                stringResource(R.string.projects_no_cards),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        items.forEach { item ->
            DhCard(onClick = { onTap(item) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        item.bodyMd?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Box {
                        var expanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.projects_move_to, label))
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            COLUMNS.filter { it.first != current }.forEach { (target, targetLabel) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.projects_move_to, targetLabel)) },
                                    onClick = { expanded = false; onMove(item, target) },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
                                onClick = { expanded = false; onDelete(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemDetailDialog(
    spaceId: String,
    item: ProjectItem,
    vm: ProjectViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
) {
    val comments by vm.observeComments(spaceId, item.id).collectAsStateWithLifecycle(initialValue = emptyList())
    var comment by remember { mutableStateOf("") }

    DhFormDialog(
        title = item.title,
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.action_close),
        onConfirm = onDismiss,
        modifier = Modifier.imePadding(),
        content = {
            item.bodyMd?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            item.linkedUrl?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            comments.forEach { c ->
                Text(
                    "• ${c.bodyMd.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DhTextField(
                value = comment,
                onValueChange = { comment = it },
                label = stringResource(R.string.projects_comment_hint),
                singleLine = false,
            )
            TextButton(
                onClick = { vm.addComment(spaceId, item.id, comment.trim()) { comment = "" } },
                enabled = comment.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.projects_comment_add)) }
        },
    )
}
