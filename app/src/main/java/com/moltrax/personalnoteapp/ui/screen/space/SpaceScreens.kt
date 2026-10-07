package com.moltrax.personalnoteapp.ui.screen.space

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.moltrax.personalnoteapp.ui.components.DhCheck
import com.moltrax.personalnoteapp.ui.components.DhCard
import com.moltrax.personalnoteapp.ui.components.DhDivider
import com.moltrax.personalnoteapp.ui.components.DhEmptyState
import com.moltrax.personalnoteapp.ui.components.DhErrorState
import com.moltrax.personalnoteapp.ui.components.DhFab
import com.moltrax.personalnoteapp.ui.components.DhFormDialog
import com.moltrax.personalnoteapp.ui.components.DhLoadingRow
import com.moltrax.personalnoteapp.ui.components.DhScrollTabs
import com.moltrax.personalnoteapp.ui.components.DhSection
import com.moltrax.personalnoteapp.ui.components.DhSettingsRow
import com.moltrax.personalnoteapp.ui.components.DhTextField
import com.moltrax.personalnoteapp.ui.components.DhTopBar
import com.moltrax.personalnoteapp.ui.components.DhAvatarStack
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.ui.screen.social.SocialViewModel
import kotlinx.coroutines.launch

private const val TAB_NOTES = 0
private const val TAB_TASKS = 1
private const val TAB_LINKS = 2
private const val TAB_CHAT = 3
private const val TAB_EVENTS = 4
private const val TAB_FILES = 5
private const val TAB_FEED = 6
private const val TAB_MEMBERS = 7

// Primary space sections: Overview (feed+recents) · Plan (tasks+events) ·
// Discuss (chat) · Library (notes+links+files) · People (members).
private const val SEC_OVERVIEW = 0
private const val SEC_PLAN = 1
private const val SEC_DISCUSS = 2
private const val SEC_LIBRARY = 3
private const val SEC_PEOPLE = 4

/**
 * One Duo hub: Notes | Tasks | Links tabs + members row (Phase 5 MVP).
 * Pulls on open; refresh button re-pulls (Realtime push arrives in Phase 9).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DuoHubScreen(
    spaceId: String,
    nav: NavController,
    vm: SpaceViewModel = hiltViewModel(),
    socialVm: SocialViewModel = hiltViewModel(),
) {
    val state by vm.detail.collectAsStateWithLifecycle()
    var section by remember { mutableIntStateOf(SEC_OVERVIEW) }
    var planSub by remember { mutableIntStateOf(0) } // 0 tasks, 1 events
    var librarySub by remember { mutableIntStateOf(0) } // 0 notes, 1 links, 2 files
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(spaceId) { vm.openSpace(spaceId) }

    // FAB target follows the visible composer: tasks/events in Plan, notes/links in Library.
    val showFab = (section == SEC_PLAN) || (section == SEC_LIBRARY && librarySub < 2)

    Scaffold(
        topBar = {
            DhTopBar(
                title = state.space?.name?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.spaces_duo),
                onBack = { nav.popBackStack() },
                backContentDescription = stringResource(R.string.action_back),
                actions = {
                    IconButton(onClick = { vm.refresh(spaceId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
            )
        },
        floatingActionButton = {
            if (showFab) {
                DhFab(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Primary space navigation: 5 sections in a clean scrollable tab row.
            val sections = listOf(
                stringResource(R.string.spaces_overview),
                stringResource(R.string.spaces_plan),
                stringResource(R.string.spaces_discuss),
                stringResource(R.string.spaces_library),
                stringResource(R.string.spaces_people),
            )
            DhScrollTabs(
                options = sections,
                selectedIndex = section,
                onSelect = { section = it },
            )
            // Secondary switchers live inside Plan + Library only.
            if (section == SEC_PLAN) {
                com.moltrax.personalnoteapp.ui.components.DhSegmentedControl(
                    options = listOf(stringResource(R.string.spaces_tasks), stringResource(R.string.spaces_events)),
                    selectedIndex = planSub,
                    onSelect = { planSub = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            if (section == SEC_LIBRARY) {
                com.moltrax.personalnoteapp.ui.components.DhSegmentedControl(
                    options = listOf(
                        stringResource(R.string.spaces_notes),
                        stringResource(R.string.spaces_links),
                        stringResource(R.string.spaces_files),
                    ),
                    selectedIndex = librarySub,
                    onSelect = { librarySub = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            if (state.busy) {
                DhLoadingRow(message = stringResource(R.string.loading))
            }
            state.error?.let {
                DhErrorState(
                    title = stringResource(R.string.sync_error_title),
                    description = it,
                    retryLabel = stringResource(R.string.action_retry),
                    onRetry = { vm.refresh(spaceId) },
                )
            }
            // Space identity strip: who the space is for + what changed recently.
            if (section == SEC_OVERVIEW) {
                SpaceOverview(
                    state = state,
                    memberNames = state.members.map { socialVm.displayNameOf(it.userId) },
                    onGoTasks = { section = SEC_PLAN; planSub = 0 },
                    onGoChat = { section = SEC_DISCUSS },
                    onGoLibrary = { section = SEC_LIBRARY },
                )
            }
            when (section) {
                SEC_PLAN -> if (planSub == 0) {
                    SharedTasksList(vm = vm, spaceId = spaceId, state = state, onAdd = { showAdd = true })
                } else {
                    EventsTab(vm = vm, spaceId = spaceId, state = state, onAdd = { showAdd = true })
                }
                SEC_DISCUSS -> ChatTab(vm = vm, spaceId = spaceId, state = state)
                SEC_LIBRARY -> when (librarySub) {
                    0 -> SharedNotesList(vm = vm, spaceId = spaceId, state = state, onAdd = { showAdd = true })
                    1 -> SharedLinksList(vm = vm, spaceId = spaceId, state = state, onAdd = { showAdd = true })
                    else -> FilesTab(vm = vm, spaceId = spaceId, state = state)
                }
                SEC_PEOPLE -> MembersTab(vm = vm, socialVm = socialVm, spaceId = spaceId, state = state, nav = nav)
                else -> {
                    // Overview feed lives above; show recent activity inline too.
                    FeedTab(state = state)
                }
            }
        }
    }

    if (showAdd) {
        if (section == SEC_PLAN && planSub == 0) {
            TaskDialog(onDismiss = { showAdd = false }) { title ->
                vm.addSharedTask(spaceId, title) { showAdd = false }
            }
        } else if (section == SEC_PLAN) {
            EventDialog(onDismiss = { showAdd = false }) { title, start, end ->
                vm.addEvent(spaceId, title, start, end) { showAdd = false }
            }
        } else if (section == SEC_LIBRARY && librarySub == 0) {
            NoteDialog(onDismiss = { showAdd = false }) { title, body ->
                vm.addNote(spaceId, title, body) { showAdd = false }
            }
        } else if (section == SEC_LIBRARY && librarySub == 1) {
            LinkDialog(onDismiss = { showAdd = false }) { url, title ->
                vm.addLink(spaceId, url, title) { showAdd = false }
            }
        }
    }
}

/** Overview: who the space is for, what changed recently, where things live. */
@Composable
private fun SpaceOverview(
    state: SpaceDetailUiState,
    memberNames: List<String>,
    onGoTasks: () -> Unit,
    onGoChat: () -> Unit,
    onGoLibrary: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val openTasks = state.tasks.count { !it.isDone }
        com.moltrax.personalnoteapp.ui.components.DhCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    state.space?.name?.takeIf { it.isNotBlank() } ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (memberNames.isNotEmpty()) {
                    DhAvatarStack(names = memberNames)
                }
                Text(
                    stringResource(R.string.spaces_overview_summary, state.tasks.size, openTasks, state.notes.size, state.files.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onGoTasks,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.spaces_tasks), maxLines = 1) }
                    OutlinedButton(
                        onClick = onGoChat,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.spaces_chat), maxLines = 1) }
                    OutlinedButton(
                        onClick = onGoLibrary,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.spaces_files), maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun SharedNotesList(vm: SpaceViewModel, spaceId: String, state: SpaceDetailUiState, onAdd: () -> Unit) {
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.notes.isEmpty()) {
            item {
                DhEmptyState(
                    icon = Icons.Default.Description,
                    title = stringResource(R.string.spaces_notes),
                    description = stringResource(R.string.spaces_empty_notes),
                    actionLabel = stringResource(R.string.spaces_new_note),
                    onAction = onAdd,
                )
            }
        } else {
            item {
                DhSection(title = stringResource(R.string.spaces_notes)) {
                    state.notes.forEachIndexed { index, note ->
                        if (index > 0) DhDivider()
                        DhSettingsRow(
                            title = note.title?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.spaces_untitled),
                            supporting = note.bodyMd?.takeIf { it.isNotBlank() },
                            trailing = {
                                IconButton(
                                    onClick = { pendingDeleteId = note.id },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.action_delete),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    if (pendingDeleteId != null) {
        com.moltrax.personalnoteapp.ui.components.DhConfirmDialog(
            title = stringResource(R.string.spaces_note_delete_title),
            message = stringResource(R.string.spaces_note_delete_confirm),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { val id = pendingDeleteId; pendingDeleteId = null; if (id != null) vm.deleteNote(spaceId, id) },
            onDismiss = { pendingDeleteId = null },
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
}

@Composable
private fun SharedTasksList(vm: SpaceViewModel, spaceId: String, state: SpaceDetailUiState, onAdd: () -> Unit) {
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.tasks.isEmpty()) {
            item {
                DhEmptyState(
                    icon = Icons.Default.Checklist,
                    title = stringResource(R.string.spaces_tasks),
                    description = stringResource(R.string.spaces_empty_tasks),
                    actionLabel = stringResource(R.string.spaces_new_task),
                    onAction = onAdd,
                )
            }
        } else {
            item {
                DhSection(title = stringResource(R.string.spaces_tasks)) {
                    state.tasks.forEachIndexed { index, task ->
                        if (index > 0) DhDivider()
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DhCheck(
                                checked = task.isDone,
                                onCheckedChange = { vm.toggleSharedTask(spaceId, task) },
                                contentDescription = task.title,
                            )
                            Text(
                                task.title,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                                ),
                                color = if (task.isDone) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(
                                onClick = { pendingDeleteId = task.id },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (pendingDeleteId != null) {
        com.moltrax.personalnoteapp.ui.components.DhConfirmDialog(
            title = stringResource(R.string.spaces_task_delete_title),
            message = stringResource(R.string.spaces_task_delete_confirm),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { val id = pendingDeleteId; pendingDeleteId = null; if (id != null) vm.deleteSharedTask(spaceId, id) },
            onDismiss = { pendingDeleteId = null },
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
}

@Composable
private fun SharedLinksList(vm: SpaceViewModel, spaceId: String, state: SpaceDetailUiState, onAdd: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.links.isEmpty()) {
            item {
                DhEmptyState(
                    icon = Icons.Default.Link,
                    title = stringResource(R.string.spaces_links),
                    description = stringResource(R.string.spaces_empty_links),
                    actionLabel = stringResource(R.string.spaces_new_link),
                    onAction = onAdd,
                )
            }
        } else {
            item {
                DhSection(title = stringResource(R.string.spaces_links)) {
                    state.links.forEachIndexed { index, link ->
                        if (index > 0) DhDivider()
                        DhSettingsRow(
                            title = link.title?.takeIf { it.isNotBlank() } ?: link.url,
                            supporting = if (!link.title.isNullOrBlank()) link.url else null,
                            trailing = {
                                IconButton(
                                    onClick = { vm.deleteLink(spaceId, link.id) },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.action_delete),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Collaborators tab: hubs hold the whole repo crew, not just a duo. Owners invite by username. */
@Composable
private fun MembersTab(
    vm: SpaceViewModel,
    socialVm: SocialViewModel,
    spaceId: String,
    state: SpaceDetailUiState,
    nav: NavController,
) {
    val social by socialVm.state.collectAsStateWithLifecycle()
    var showInvite by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.spaces_members_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showInvite = true },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.spaces_invite), maxLines = 1) }
                OutlinedButton(
                    onClick = { showLeaveConfirm = true },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.spaces_leave), maxLines = 1) }
            }
        }
        item {
            DhSection(title = stringResource(R.string.spaces_members)) {
                state.members.forEachIndexed { index, member ->
                    if (index > 0) DhDivider()
                    val name = socialVm.displayNameOf(member.userId) +
                        if (member.userId == social.myId) stringResource(R.string.spaces_you_suffix) else ""
                    DhSettingsRow(
                        title = name,
                        supporting = stringResource(
                            if (member.isOwner) R.string.spaces_role_owner
                            else R.string.spaces_role_member,
                        ),
                        trailing = {
                            if (member.userId != social.myId) {
                                IconButton(
                                    onClick = { removeTarget = member.userId to name },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.spaces_remove),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
    if (showLeaveConfirm) {
        com.moltrax.personalnoteapp.ui.components.DhConfirmDialog(
            title = stringResource(R.string.spaces_leave_title),
            message = stringResource(R.string.spaces_leave_confirm),
            confirmLabel = stringResource(R.string.spaces_leave),
            onConfirm = { showLeaveConfirm = false; vm.leaveSpace(spaceId) { nav.popBackStack() } },
            onDismiss = { showLeaveConfirm = false },
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
    removeTarget?.let { (userId, name) ->
        com.moltrax.personalnoteapp.ui.components.DhConfirmDialog(
            title = stringResource(R.string.spaces_remove_title),
            message = stringResource(R.string.spaces_remove_confirm, name),
            confirmLabel = stringResource(R.string.spaces_remove),
            onConfirm = { removeTarget = null; vm.removeMember(spaceId, userId) },
            onDismiss = { removeTarget = null },
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
    if (showInvite) {
        var username by remember { mutableStateOf("") }
        DhFormDialog(
            title = stringResource(R.string.spaces_invite_title),
            onDismiss = { showInvite = false },
            confirmLabel = stringResource(R.string.spaces_invite),
            onConfirm = { vm.inviteByUsername(spaceId, username.trim()) { showInvite = false } },
            dismissLabel = stringResource(R.string.action_cancel),
            confirmEnabled = username.isNotBlank(),
            message = stringResource(R.string.spaces_invite_hint),
            modifier = Modifier.imePadding(),
            content = {
                DhTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = stringResource(R.string.spaces_username),
                    singleLine = true,
                )
            },
        )
    }
}

@Composable
private fun NoteDialog(onDismiss: () -> Unit, onConfirm: (String?, String?) -> Unit) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    DhFormDialog(
        title = stringResource(R.string.spaces_new_note),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.action_save),
        onConfirm = {
            onConfirm(title.takeIf { it.isNotBlank() }, body.takeIf { it.isNotBlank() })
        },
        dismissLabel = stringResource(R.string.action_cancel),
        modifier = Modifier.imePadding(),
        content = {
            DhTextField(
                value = title,
                onValueChange = { title = it },
                label = stringResource(R.string.spaces_note_title),
                singleLine = true,
            )
            DhTextField(
                value = body,
                onValueChange = { body = it },
                label = stringResource(R.string.spaces_note_body),
                singleLine = false,
            )
        },
    )
}

@Composable
private fun TaskDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    DhFormDialog(
        title = stringResource(R.string.spaces_new_task),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.action_save),
        onConfirm = { onConfirm(title.trim()) },
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

@Composable
private fun LinkDialog(onDismiss: () -> Unit, onConfirm: (String, String?) -> Unit) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    val valid = url.trim().startsWith("http://") || url.trim().startsWith("https://")
    DhFormDialog(
        title = stringResource(R.string.spaces_new_link),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.action_save),
        onConfirm = { onConfirm(url.trim(), title.takeIf { it.isNotBlank() }) },
        dismissLabel = stringResource(R.string.action_cancel),
        confirmEnabled = valid,
        modifier = Modifier.imePadding(),
        content = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.spaces_link_url)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = url.isNotBlank() && !valid,
                shape = MaterialTheme.shapes.small,
            )
            DhTextField(
                value = title,
                onValueChange = { title = it },
                label = stringResource(R.string.spaces_link_title),
                singleLine = true,
            )
        },
    )
}

/** Chat tab (Phase 8): pull on open + 15s poll until Realtime (Phase 9). */
@Composable
private fun ChatTab(
    vm: SpaceViewModel,
    spaceId: String,
    state: com.moltrax.personalnoteapp.ui.screen.space.SpaceDetailUiState,
) {
    var draft by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.messages.isEmpty()) {
                item {
                    DhEmptyState(
                        icon = Icons.Default.ChatBubbleOutline,
                        title = stringResource(R.string.spaces_chat),
                        description = stringResource(R.string.spaces_empty_chat),
                    )
                }
            } else {
                item {
                    DhSection(title = stringResource(R.string.spaces_chat)) {
                        state.messages.forEachIndexed { index, msg ->
                            if (index > 0) DhDivider()
                            DhSettingsRow(
                                title = msg.body,
                                trailing = {
                                    IconButton(
                                        onClick = { vm.deleteMessage(spaceId, msg.id) },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.action_delete),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().imePadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text(stringResource(R.string.spaces_chat_hint)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.sendMessage(spaceId, draft) { draft = "" } }, enabled = draft.isNotBlank()) {
                Text(stringResource(R.string.spaces_send))
            }
        }
    }
}

/** Events tab (Phase 8): single instances (recurrence deferred). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventsTab(
    vm: SpaceViewModel,
    spaceId: String,
    state: com.moltrax.personalnoteapp.ui.screen.space.SpaceDetailUiState,
    onAdd: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.events.isEmpty()) {
            item {
                DhEmptyState(
                    icon = Icons.Default.Event,
                    title = stringResource(R.string.spaces_events),
                    description = stringResource(R.string.spaces_empty_events),
                    actionLabel = stringResource(R.string.spaces_new_event),
                    onAction = onAdd,
                )
            }
        } else {
            item {
                DhSection(title = stringResource(R.string.spaces_events)) {
                    state.events.forEachIndexed { index, event ->
                        if (index > 0) DhDivider()
                        DhSettingsRow(
                            title = event.title,
                            supporting = fmtRange(event.startAt, event.endAt),
                            trailing = {
                                IconButton(
                                    onClick = { vm.deleteEvent(spaceId, event.id) },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.action_delete),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Files tab (Phase 8): space-files bucket (25 MB cap), tap to view. */
@Composable
private fun FilesTab(
    vm: SpaceViewModel,
    spaceId: String,
    state: com.moltrax.personalnoteapp.ui.screen.space.SpaceDetailUiState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val name = contentName(context, uri) ?: "file"
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Unreadable file.")
            }.onSuccess { bytes ->
                val name = contentName(context, uri) ?: "file"
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                vm.uploadFile(spaceId, name, bytes, mime)
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Button(
            onClick = { picker.launch("*/*") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .heightIn(min = 48.dp),
        ) {
            Text(
                stringResource(R.string.spaces_upload),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.files.isEmpty()) {
                item {
                    DhEmptyState(
                        icon = Icons.Default.FolderOpen,
                        title = stringResource(R.string.spaces_files),
                        description = stringResource(R.string.spaces_empty_files),
                    )
                }
            } else {
                item {
                    DhSection(title = stringResource(R.string.spaces_files)) {
                        state.files.forEachIndexed { index, file ->
                            if (index > 0) DhDivider()
                            DhSettingsRow(
                                title = file.displayName,
                                supporting = kbLabel(file.size),
                                trailing = {
                                    IconButton(
                                        onClick = { vm.deleteFile(spaceId, file) },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.action_delete),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = {
                                    scope.launch {
                                        runCatching { vm.downloadBytes(file) }.onSuccess { bytes ->
                                            openBytes(context, file.displayName, bytes)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Feed tab (Phase 8): server-side event log, best-effort writes. */
@Composable
private fun FeedTab(state: com.moltrax.personalnoteapp.ui.screen.space.SpaceDetailUiState) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.feed.isEmpty()) {
            item {
                DhEmptyState(
                    icon = Icons.Default.Stream,
                    title = stringResource(R.string.spaces_feed),
                    description = stringResource(R.string.spaces_empty_feed),
                )
            }
        } else {
            item {
                DhSection(title = stringResource(R.string.spaces_feed)) {
                    state.feed.forEachIndexed { index, entry ->
                        if (index > 0) DhDivider()
                        DhSettingsRow(
                            title = com.moltrax.personalnoteapp.domain.model.feedKindTitle(entry.kind),
                            supporting = feedTime(entry.createdAt),
                        )
                    }
                }
            }
        }
    }
}

/** Locale-aware range formatter (composition locale, so it follows the app language). */
@Composable
private fun fmtRange(startMs: Long, endMs: Long?): String {
    val locale = LocalConfiguration.current.locales[0]
    val fmt = remember(locale) { java.text.SimpleDateFormat("d MMM HH:mm", locale) }
    val start = fmt.format(java.util.Date(startMs))
    return if (endMs == null) start else "$start → ${fmt.format(java.util.Date(endMs))}"
}

/** Locale-aware feed timestamp: parses the ISO instant, falls back to the raw value. */
@Composable
private fun feedTime(iso: String): String {
    val locale = LocalConfiguration.current.locales[0]
    val fmt = remember(locale) { java.text.SimpleDateFormat("d MMM HH:mm", locale) }
    return remember(iso, fmt) {
        runCatching {
            val instant = runCatching { java.time.Instant.parse(iso) }.getOrNull()
                ?: java.time.OffsetDateTime.parse(iso).toInstant()
            fmt.format(java.util.Date.from(instant))
        }.getOrNull() ?: iso
    }
}

@Composable
private fun kbLabel(bytes: Long): String =
    if (bytes < 1024) stringResource(R.string.spaces_file_size_bytes, bytes)
    else stringResource(R.string.spaces_file_size_kb, bytes / 1024)

private fun contentName(context: android.content.Context, uri: android.net.Uri): String? =
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else null
        }
    }.getOrNull()

private fun openBytes(context: android.content.Context, name: String, bytes: ByteArray) {
    runCatching {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "file" }
        val out = java.io.File(context.cacheDir, "shared").apply { mkdirs() }.resolve(safe)
        out.writeBytes(bytes)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", out,
        )
        val mime = when (safe.substringAfterLast('.', "").lowercase()) {
            "png", "jpg", "jpeg", "gif", "webp" -> "image/*"
            "mp4", "mkv" -> "video/*"
            "pdf" -> "application/pdf"
            "txt", "md" -> "text/plain"
            else -> "*/*"
        }
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDialog(onDismiss: () -> Unit, onConfirm: (String, Long, Long?) -> Unit) {
    var title by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(System.currentTimeMillis() + 3600_000L) }
    var useEnd by remember { mutableStateOf(false) }
    var end by remember { mutableStateOf(start + 3600_000L) }
    var picking by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spaces_new_event)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.task_title_field)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedButton(onClick = { picking = "start" }, modifier = Modifier.fillMaxWidth()) {
                    Text(fmtRange(start, null))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useEnd, onCheckedChange = { useEnd = it })
                    Text(stringResource(R.string.spaces_event_end))
                }
                if (useEnd) {
                    OutlinedButton(onClick = { picking = "end" }, modifier = Modifier.fillMaxWidth()) {
                        Text(fmtRange(end, null))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.trim(), start, if (useEnd) maxOf(end, start) else null) },
                enabled = title.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    when (picking) {
        "start", "end" -> {
            val initial = if (picking == "start") start else end
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = initial }
            val dateState = rememberDatePickerState(initialSelectedDateMillis = initial)
            var dateDone by remember { mutableStateOf(false) }
            if (!dateDone) {
                DatePickerDialog(
                    onDismissRequest = { picking = null },
                    confirmButton = {
                        TextButton(onClick = { dateDone = true }) { Text(stringResource(R.string.action_save)) }
                    },
                ) { DatePicker(state = dateState) }
            } else {
                val timeState = rememberTimePickerState(
                    initialHour = cal.get(java.util.Calendar.HOUR_OF_DAY),
                    initialMinute = cal.get(java.util.Calendar.MINUTE),
                )
                AlertDialog(
                    onDismissRequest = { picking = null },
                    title = { Text(stringResource(R.string.spaces_event_time)) },
                    text = { TimePicker(state = timeState) },
                    confirmButton = {
                        TextButton(onClick = {
                            val picked = java.util.Calendar.getInstance().apply {
                                timeInMillis = dateState.selectedDateMillis ?: initial
                                set(java.util.Calendar.HOUR_OF_DAY, timeState.hour)
                                set(java.util.Calendar.MINUTE, timeState.minute)
                                set(java.util.Calendar.SECOND, 0)
                                set(java.util.Calendar.MILLISECOND, 0)
                            }.timeInMillis
                            if (picking == "start") start = picked else end = picked
                            picking = null
                        }) { Text(stringResource(R.string.action_save)) }
                    },
                    dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.action_cancel)) } },
                )
            }
        }
        else -> Unit
    }
}

/** Row for one space in lists (name + type label). */
@Composable
fun SpaceRow(spaceName: String, typeLabel: String, onClick: () -> Unit) {
    DhCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(spaceName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(typeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
