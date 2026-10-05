package com.moltrax.personalnoteapp.ui.screen.space

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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

/**
 * One Duo hub: Notes | Tasks | Links tabs + members row (Phase 5 MVP).
 * Pulls on open; refresh button re-pulls (Realtime push arrives in Phase 9).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuoHubScreen(
    spaceId: String,
    nav: NavController,
    vm: SpaceViewModel = hiltViewModel(),
    socialVm: SocialViewModel = hiltViewModel(),
) {
    val state by vm.detail.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(TAB_NOTES) }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(spaceId) { vm.openSpace(spaceId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.space?.name ?: stringResource(R.string.spaces_duo)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh(spaceId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
            )
        },
        floatingActionButton = {
            if (tab == TAB_NOTES || tab == TAB_TASKS || tab == TAB_LINKS || tab == TAB_EVENTS) {
                FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                Tab(selected = tab == TAB_NOTES, onClick = { tab = TAB_NOTES }, text = { Text(stringResource(R.string.spaces_notes)) })
                Tab(selected = tab == TAB_TASKS, onClick = { tab = TAB_TASKS }, text = { Text(stringResource(R.string.spaces_tasks)) })
                Tab(selected = tab == TAB_LINKS, onClick = { tab = TAB_LINKS }, text = { Text(stringResource(R.string.spaces_links)) })
                Tab(selected = tab == TAB_CHAT, onClick = { tab = TAB_CHAT }, text = { Text(stringResource(R.string.spaces_chat)) })
                Tab(selected = tab == TAB_EVENTS, onClick = { tab = TAB_EVENTS }, text = { Text(stringResource(R.string.spaces_events)) })
                Tab(selected = tab == TAB_FILES, onClick = { tab = TAB_FILES }, text = { Text(stringResource(R.string.spaces_files)) })
                Tab(selected = tab == TAB_FEED, onClick = { tab = TAB_FEED }, text = { Text(stringResource(R.string.spaces_feed)) })
                Tab(selected = tab == TAB_MEMBERS, onClick = { tab = TAB_MEMBERS }, text = { Text(stringResource(R.string.spaces_members)) })
            }
            if (state.busy) {
                Box(
                    Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            when (tab) {
                TAB_NOTES -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.notes.isEmpty()) {
                        item { Text(stringResource(R.string.spaces_empty_notes)) }
                    }
                    items(state.notes, key = { it.id }) { note ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        note.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.spaces_untitled),
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { vm.deleteNote(spaceId, note.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                                    }
                                }
                                note.bodyMd?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                TAB_TASKS -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.tasks.isEmpty()) {
                        item { Text(stringResource(R.string.spaces_empty_tasks)) }
                    }
                    items(state.tasks, key = { it.id }) { task ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = task.isDone,
                                    onCheckedChange = { vm.toggleSharedTask(spaceId, task) },
                                )
                                Text(
                                    task.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { vm.deleteSharedTask(spaceId, task.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                                }
                            }
                        }
                    }
                }
                TAB_LINKS -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.links.isEmpty()) {
                        item { Text(stringResource(R.string.spaces_empty_links)) }
                    }
                    items(state.links, key = { it.id }) { link ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        link.title?.takeIf { it.isNotBlank() } ?: link.url,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    if (!link.title.isNullOrBlank()) {
                                        Text(
                                            link.url,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                IconButton(onClick = { vm.deleteLink(spaceId, link.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                                }
                            }
                        }
                    }
                }
                TAB_CHAT -> ChatTab(vm = vm, spaceId = spaceId, state = state)
                TAB_EVENTS -> EventsTab(vm = vm, spaceId = spaceId, state = state)
                TAB_FILES -> FilesTab(vm = vm, spaceId = spaceId, state = state)
                TAB_FEED -> FeedTab(state = state)
                TAB_MEMBERS -> MembersTab(vm = vm, socialVm = socialVm, spaceId = spaceId, state = state, nav = nav)
            }
        }
    }

    if (showAdd) {
        when (tab) {
            TAB_NOTES -> NoteDialog(onDismiss = { showAdd = false }) { title, body ->
                vm.addNote(spaceId, title, body) { showAdd = false }
            }
            TAB_TASKS -> TaskDialog(onDismiss = { showAdd = false }) { title ->
                vm.addSharedTask(spaceId, title) { showAdd = false }
            }
            TAB_LINKS -> LinkDialog(onDismiss = { showAdd = false }) { url, title ->
                vm.addLink(spaceId, url, title) { showAdd = false }
            }
            TAB_EVENTS -> EventDialog(onDismiss = { showAdd = false }) { title, start, end ->
                vm.addEvent(spaceId, title, start, end) { showAdd = false }
            }
            else -> Unit
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
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.spaces_invite)) }
                OutlinedButton(
                    onClick = { vm.leaveSpace(spaceId) { nav.popBackStack() } },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.spaces_leave)) }
            }
        }
        items(state.members, key = { it.userId }) { member ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            socialVm.displayNameOf(member.userId) +
                                if (member.userId == social.myId) " (you)" else "",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            stringResource(
                                if (member.isOwner) R.string.spaces_role_owner
                                else R.string.spaces_role_member
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (member.userId != social.myId) {
                        IconButton(onClick = { vm.removeMember(spaceId, member.userId) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.spaces_remove))
                        }
                    }
                }
            }
        }
    }
    if (showInvite) {
        var username by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showInvite = false },
            title = { Text(stringResource(R.string.spaces_invite_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.spaces_invite_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.spaces_username)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.inviteByUsername(spaceId, username) { showInvite = false } },
                    enabled = username.isNotBlank(),
                ) { Text(stringResource(R.string.spaces_invite)) }
            },
            dismissButton = { TextButton(onClick = { showInvite = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun NoteDialog(onDismiss: () -> Unit, onConfirm: (String?, String?) -> Unit) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spaces_new_note)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.spaces_note_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(stringResource(R.string.spaces_note_body)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(title.takeIf { it.isNotBlank() }, body.takeIf { it.isNotBlank() })
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun TaskDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spaces_new_task)) },
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
            TextButton(onClick = { onConfirm(title.trim()) }, enabled = title.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun LinkDialog(onDismiss: () -> Unit, onConfirm: (String, String?) -> Unit) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    val valid = url.trim().startsWith("http://") || url.trim().startsWith("https://")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spaces_new_link)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.spaces_link_url)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = url.isNotBlank() && !valid,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.spaces_link_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url.trim(), title.takeIf { it.isNotBlank() }) },
                enabled = valid,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
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
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.messages.isEmpty()) {
                item { Text(stringResource(R.string.spaces_empty_chat)) }
            }
            items(state.messages, key = { it.id }) { msg ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(msg.body, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { vm.deleteMessage(spaceId, msg.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
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
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.events.isEmpty()) {
            item { Text(stringResource(R.string.spaces_empty_events)) }
        }
        items(state.events, key = { it.id }) { event ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(event.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            fmtRange(event.startAt, event.endAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { vm.deleteEvent(spaceId, event.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) { Text(stringResource(R.string.spaces_upload)) }
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.files.isEmpty()) {
                item { Text(stringResource(R.string.spaces_empty_files)) }
            }
            items(state.files, key = { it.id }) { file ->
                Card(Modifier.fillMaxWidth().clickable {
                    scope.launch {
                        runCatching { vm.downloadBytes(file) }.onSuccess { bytes ->
                            openBytes(context, file.displayName, bytes)
                        }
                    }
                }) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(file.displayName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                kbLabel(file.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { vm.deleteFile(spaceId, file) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
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
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.feed.isEmpty()) {
            item { Text(stringResource(R.string.spaces_empty_feed)) }
        }
        items(state.feed, key = { it.id }) { entry ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        com.moltrax.personalnoteapp.domain.model.feedKindTitle(entry.kind),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        entry.createdAt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun fmtRange(startMs: Long, endMs: Long?): String {
    val fmt = java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.getDefault())
    val start = fmt.format(java.util.Date(startMs))
    return if (endMs == null) start else "$start → ${fmt.format(java.util.Date(endMs))}"
}

private fun kbLabel(bytes: Long): String =
    if (bytes < 1024) "$bytes B" else "${bytes / 1024} KB"

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
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(spaceName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(typeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
