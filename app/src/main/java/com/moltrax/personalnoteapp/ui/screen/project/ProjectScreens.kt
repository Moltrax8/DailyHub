package com.moltrax.personalnoteapp.ui.screen.project

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.projects_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (projects.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.Dashboard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp),
                            )
                            Text(
                                stringResource(R.string.projects_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.projects_empty_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = { showCreate = true }) {
                                Text(stringResource(R.string.projects_new))
                            }
                        }
                    }
                }
            }
            items(projects, key = { it.id }) { space ->
                Card(Modifier.fillMaxWidth().clickable { nav.navigate(ProjectDetail(space.id)) }) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Dashboard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                space.name?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.projects_untitled),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                stringResource(R.string.projects_open),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(stringResource(R.string.projects_new)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.projects_new_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.projects_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text(stringResource(R.string.projects_description)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.createProject(name, desc) { id ->
                            showCreate = false
                            nav.navigate(ProjectDetail(id))
                        }
                    },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** One project: lightweight board (Idea → Planned → Developing → Finished). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(spaceId: String, nav: NavController, vm: ProjectViewModel = hiltViewModel()) {
    val state by vm.detail.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ProjectItem?>(null) }
    var mainTab by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(spaceId) { vm.openProject(spaceId); vm.loadGitHub(spaceId) }
    val board = groupBoardItems(state.items)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.projects_board)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { nav.navigate(DuoHub(spaceId)) }) {
                        Icon(Icons.Default.Hub, contentDescription = stringResource(R.string.projects_open_hub))
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = null)
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
                FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            com.moltrax.personalnoteapp.ui.components.DhSegmentedControl(
                options = listOf(
                    stringResource(R.string.projects_board),
                    stringResource(R.string.projects_github),
                ),
                selectedIndex = mainTab,
                onSelect = { mainTab = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
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
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.projects_new_item)) },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.task_title_field)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.addItem(spaceId, title) { showAdd = false } },
                    enabled = title.isNotBlank(),
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.action_cancel)) } },
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
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.projects_delete_title)) },
            text = { Text(stringResource(R.string.projects_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    vm.deleteProject(spaceId) { nav.popBackStack() }
                }) { Text(stringResource(R.string.projects_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
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
    if (state.busy) CircularProgressIndicator(modifier = Modifier.padding(16.dp))
    state.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
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
                            onDelete = { vm.deleteItem(spaceId, it.id) },
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
                            onDelete = { vm.deleteItem(spaceId, it.id) },
                        )
                    }
                }
            }
        }
    }
}

/** GitHub tab (Phase 7): connection badge, linked repos, per-kind toggles, activity feed. */
@Composable
private fun GitHubTab(vm: ProjectViewModel, spaceId: String) {
    val gh by vm.githubState.collectAsStateWithLifecycle()
    var showLink by remember { mutableStateOf(false) }
    var showBrowse by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (gh.connected?.githubLogin == null) {
            item {
                Text(
                    stringResource(R.string.github_connect_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item {
                Text(
                    stringResource(R.string.github_connected_as, gh.connected!!.githubLogin!!),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.github_repos),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showBrowse = true }) { Text(stringResource(R.string.github_browse)) }
                TextButton(onClick = { showLink = true }) { Text(stringResource(R.string.github_link)) }
            }
        }
        items(gh.repos, key = { it.id }) { repo ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(repo.fullName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { vm.unlinkRepo(spaceId, repo.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.github_unlink))
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.github_prefs), style = MaterialTheme.typography.titleSmall)
        }
        items(com.moltrax.personalnoteapp.domain.repository.GitHubRepository.KINDS, key = { it }) { kind ->
            val enabled = gh.prefs[kind] ?: true
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    com.moltrax.personalnoteapp.domain.model.githubActivityTitle(kind),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                androidx.compose.material3.Switch(
                    checked = enabled,
                    onCheckedChange = { vm.togglePref(spaceId, kind, it) },
                )
            }
        }
        item {
            OutlinedButton(
                onClick = { vm.enablePush() },
                enabled = !gh.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (gh.pushDone) R.string.github_push_done
                        else R.string.github_enable_push
                    )
                )
            }
        }
        item {
            Text(stringResource(R.string.github_activity), style = MaterialTheme.typography.titleSmall)
        }
        if (gh.activity.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.github_activity_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(gh.activity, key = { it.id }) { act ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        com.moltrax.personalnoteapp.domain.model.githubActivityTitle(act.kind),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    val summary = act.summary()
                    if (summary != act.kind) {
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    act.repoFull?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        gh.error?.let {
            item { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (showLink) {
        var repoId by remember { mutableStateOf("") }
        var fullName by remember { mutableStateOf("") }
        var isPrivate by remember { mutableStateOf(false) }
        var formError by remember { mutableStateOf<String?>(null) }
        val errId = stringResource(R.string.github_err_id)
        val errName = stringResource(R.string.github_err_name)
        AlertDialog(
            onDismissRequest = { showLink = false },
            title = { Text(stringResource(R.string.github_link_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.github_link_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = repoId,
                        onValueChange = { repoId = it },
                        label = { Text(stringResource(R.string.github_repo_id)) },
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text(stringResource(R.string.github_repo_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = isPrivate,
                            onCheckedChange = { isPrivate = it },
                        )
                        Text(stringResource(R.string.github_private))
                    }
                    formError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = repoId.toLongOrNull()?.takeIf { it > 0 }
                        val name = fullName.trim()
                        formError = when {
                            id == null -> errId
                            !name.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) -> errName
                            else -> null
                        }
                        if (formError == null) {
                            vm.linkRepo(spaceId, id!!, name, isPrivate) { showLink = false }
                        }
                    },
                    enabled = repoId.isNotBlank() && fullName.isNotBlank(),
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { showLink = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (showBrowse) {
        BrowseReposDialog(spaceId = spaceId, vm = vm, onDismiss = { showBrowse = false })
    }
}

@Composable
private fun BrowseReposDialog(spaceId: String, vm: ProjectViewModel, onDismiss: () -> Unit) {
    val browse by vm.browseState.collectAsStateWithLifecycle()
    var username by remember { mutableStateOf(browse.username) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.github_browse_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    Text(
                                        stringResource(R.string.github_fork_badge),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                items.size.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (items.isEmpty()) {
            Text("--", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        items.forEach { item ->
            Card(Modifier.fillMaxWidth().clickable { onTap(item) }) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall)
                        item.bodyMd?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item.bodyMd?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                item.linkedUrl?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                comments.forEach { c ->
                    Text(
                        "• ${c.bodyMd.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(stringResource(R.string.projects_comment_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { vm.addComment(spaceId, item.id, comment) { comment = "" } },
                    enabled = comment.isNotBlank(),
                ) { Text(stringResource(R.string.projects_comment_add)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_save)) } },
    )
}
