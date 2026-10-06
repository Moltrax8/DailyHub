package com.moltrax.personalnoteapp.ui.screen.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.domain.model.FriendRequestStatus
import com.moltrax.personalnoteapp.domain.model.SupabaseProfile
import com.moltrax.personalnoteapp.ui.components.DhCard
import com.moltrax.personalnoteapp.ui.components.DhConfirmDialog
import com.moltrax.personalnoteapp.ui.components.DhDivider
import com.moltrax.personalnoteapp.ui.components.DhEmptyState
import com.moltrax.personalnoteapp.ui.components.DhErrorState
import com.moltrax.personalnoteapp.ui.components.DhLoadingRow
import com.moltrax.personalnoteapp.ui.components.DhSearchField
import com.moltrax.personalnoteapp.ui.components.DhSection
import com.moltrax.personalnoteapp.ui.components.DhSettingsRow
import com.moltrax.personalnoteapp.ui.components.DhStatusChip
import com.moltrax.personalnoteapp.ui.components.DhTopBar
import com.moltrax.personalnoteapp.ui.components.DhUserRow
import com.moltrax.personalnoteapp.ui.navigation.FriendRequests
import com.moltrax.personalnoteapp.ui.navigation.UserProfile
import com.moltrax.personalnoteapp.ui.navigation.DuoHub
import com.moltrax.personalnoteapp.ui.screen.space.SpaceViewModel

/** Friends graph: search + results, requests group, friends list, shared hubs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialGraphScreen(
    nav: NavController,
    vm: SocialViewModel = hiltViewModel(),
    spaceVm: SpaceViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val spaces by spaceVm.mySpaces.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }
    val pendingIn = state.incoming.filter { it.status == FriendRequestStatus.PENDING }
    val showResults = state.query.trim().length >= 2

    Scaffold(
        topBar = { DhTopBar(title = stringResource(R.string.social_title)) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DhSearchField(
                        value = state.query,
                        onValueChange = { vm.search(it) },
                        label = stringResource(R.string.social_find_title),
                        placeholder = stringResource(R.string.social_search_hint),
                        onClear = { vm.search("") },
                    )
                    Text(
                        stringResource(R.string.social_find_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (showResults) {
                item {
                    DhSection(title = stringResource(R.string.social_results_title)) {
                        if (state.results.isEmpty() && !state.busy) {
                            Text(
                                stringResource(R.string.social_no_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                        state.results.forEachIndexed { index, profile ->
                            if (index > 0) DhDivider()
                            SearchResultRow(
                                profile = profile,
                                status = profile.id.let { id ->
                                    if (id == state.myId) null else vm.statusOf(id)
                                },
                                canAdd = state.myId != null && state.myId != profile.id &&
                                    vm.statusOf(profile.id) == null,
                                onAdd = { vm.send(profile.id) },
                                onClick = { nav.navigate(UserProfile(profile.username)) },
                            )
                        }
                    }
                }
            }
            if (pendingIn.isNotEmpty()) {
                item {
                    DhSection(
                        title = stringResource(R.string.social_section_requests),
                        subtitle = stringResource(R.string.social_requests_pending, pendingIn.size),
                    ) {
                        pendingIn.forEachIndexed { index, req ->
                            if (index > 0) DhDivider()
                            RequestRow(
                                title = vm.displayNameOf(req.fromId),
                                incoming = true,
                                onAccept = { vm.accept(req.id) },
                                onReject = { vm.reject(req.id) },
                                onCancel = {},
                            )
                        }
                        TextButton(
                            onClick = { nav.navigate(FriendRequests) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text(
                                stringResource(R.string.social_view_all),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            item {
                if (state.friends.isEmpty() && !state.busy) {
                    DhEmptyState(
                        icon = Icons.Filled.People,
                        title = stringResource(R.string.social_friends),
                        description = stringResource(R.string.social_no_friends),
                    )
                } else {
                    DhSection(title = stringResource(R.string.social_friends)) {
                        state.friends.forEachIndexed { index, profile ->
                            if (index > 0) DhDivider()
                            DhUserRow(
                                displayName = profile.displayName?.takeIf { it.isNotBlank() }
                                    ?: profile.username,
                                username = profile.username,
                                photoUrl = profile.avatarUrl,
                                onClick = { nav.navigate(UserProfile(profile.username)) },
                                trailing = {
                                    TextButton(
                                        onClick = {
                                            spaceVm.openOrCreateDuo(profile.id, profile.username) { id ->
                                                nav.navigate(DuoHub(id))
                                            }
                                        },
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    ) {
                                        Text(
                                            stringResource(R.string.spaces_open_hub),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
            item {
                DhSection(
                    title = stringResource(R.string.spaces_section),
                    subtitle = stringResource(R.string.spaces_section_desc),
                ) {
                    if (spaces.isEmpty()) {
                        Text(
                            stringResource(R.string.spaces_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    spaces.forEachIndexed { index, space ->
                        if (index > 0) DhDivider()
                        DhSettingsRow(
                            title = space.name?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.spaces_duo),
                            supporting = space.type.name,
                            trailing = {
                                TextButton(
                                    onClick = { nav.navigate(DuoHub(space.id)) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.spaces_open_hub),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            },
                            onClick = { nav.navigate(DuoHub(space.id)) },
                        )
                    }
                }
            }
            if (state.busy) {
                item { DhLoadingRow(message = stringResource(R.string.loading)) }
            }
            state.error?.let {
                item {
                    DhErrorState(
                        title = stringResource(R.string.sync_error_title),
                        description = it,
                        retryLabel = stringResource(R.string.action_retry),
                        onRetry = { vm.refresh() },
                    )
                }
            }
        }
    }
}

/** Incoming (accept/reject) + outgoing (cancel) requests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendRequestsScreen(nav: NavController, vm: SocialViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pendingIn = state.incoming.filter { it.status == FriendRequestStatus.PENDING }
    val pendingOut = state.outgoing.filter { it.status == FriendRequestStatus.PENDING }

    Scaffold(
        topBar = {
            DhTopBar(
                title = stringResource(R.string.social_requests_title),
                onBack = { nav.popBackStack() },
                backContentDescription = stringResource(R.string.action_back),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                DhSection(
                    title = stringResource(R.string.social_incoming),
                    subtitle = pendingIn.size.toString(),
                ) {
                    if (pendingIn.isEmpty()) {
                        Text(
                            stringResource(R.string.social_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    pendingIn.forEachIndexed { index, req ->
                        if (index > 0) DhDivider()
                        RequestRow(
                            title = vm.displayNameOf(req.fromId),
                            incoming = true,
                            onAccept = { vm.accept(req.id) },
                            onReject = { vm.reject(req.id) },
                            onCancel = {},
                        )
                    }
                }
            }
            item {
                DhSection(
                    title = stringResource(R.string.social_outgoing),
                    subtitle = pendingOut.size.toString(),
                ) {
                    if (pendingOut.isEmpty()) {
                        Text(
                            stringResource(R.string.social_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    pendingOut.forEachIndexed { index, req ->
                        if (index > 0) DhDivider()
                        RequestRow(
                            title = vm.displayNameOf(req.toId),
                            incoming = false,
                            onAccept = {},
                            onReject = {},
                            onCancel = { vm.cancel(req.id) },
                        )
                    }
                }
            }
        }
    }
}

/** One user: profile + befriend / pending / friends(+remove) action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(username: String, nav: NavController, vm: SocialViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    // This screen gets its own ViewModel instance (empty search results), so
    // resolve the profile here instead of relying on the caller's results.
    LaunchedEffect(username) {
        vm.refresh()
        vm.search(username)
    }
    val profile = state.results.firstOrNull { it.username.equals(username, ignoreCase = true) }
        ?: state.friends.firstOrNull { it.username.equals(username, ignoreCase = true) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            DhTopBar(
                title = username,
                onBack = { nav.popBackStack() },
                backContentDescription = stringResource(R.string.action_back),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (profile == null) {
                if (state.busy) {
                    DhLoadingRow(message = stringResource(R.string.loading))
                } else {
                    DhEmptyState(
                        icon = Icons.Filled.People,
                        title = username,
                        description = stringResource(R.string.social_not_found),
                        actionLabel = stringResource(R.string.action_back),
                        onAction = { nav.popBackStack() },
                    )
                }
                return@Column
            }
            DhCard {
                DhUserRow(
                    displayName = profile.displayName?.takeIf { it.isNotBlank() } ?: profile.username,
                    username = profile.username,
                    photoUrl = profile.avatarUrl,
                )
            }
            val mine = state.myId
            if (mine != null && mine != profile.id) {
                when (vm.statusOf(profile.id)) {
                    null -> Button(
                        onClick = { vm.send(profile.id) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.social_add))
                    }
                    FriendRequestStatus.PENDING -> DhStatusChip(label = stringResource(R.string.social_pending))
                    FriendRequestStatus.ACCEPTED -> OutlinedButton(
                        onClick = { showRemoveConfirm = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Default.PersonRemove, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.social_remove))
                    }
                    else -> Unit
                }
            }
        }
    }

    if (showRemoveConfirm && profile != null) {
        DhConfirmDialog(
            title = stringResource(R.string.social_remove_title),
            message = stringResource(
                R.string.social_remove_confirm,
                profile.displayName?.takeIf { it.isNotBlank() } ?: profile.username,
            ),
            confirmLabel = stringResource(R.string.social_remove),
            onConfirm = { showRemoveConfirm = false; vm.removeFriend(profile.id) },
            onDismiss = { showRemoveConfirm = false },
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
}

@Composable
private fun SearchResultRow(
    profile: SupabaseProfile,
    status: FriendRequestStatus?,
    canAdd: Boolean,
    onAdd: () -> Unit,
    onClick: () -> Unit,
) {
    DhUserRow(
        displayName = profile.displayName?.takeIf { it.isNotBlank() } ?: profile.username,
        username = profile.username,
        photoUrl = profile.avatarUrl,
        onClick = onClick,
        trailing = {
            when {
                canAdd -> IconButton(onClick = onAdd, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = stringResource(R.string.social_add),
                    )
                }
                status == FriendRequestStatus.PENDING -> DhStatusChip(
                    label = stringResource(R.string.social_pending),
                )
                status == FriendRequestStatus.ACCEPTED -> Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

@Composable
private fun RequestRow(
    title: String,
    incoming: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (incoming) {
            TextButton(onClick = onAccept, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.social_accept), maxLines = 1)
            }
            TextButton(onClick = onReject, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.social_reject), maxLines = 1)
            }
        } else {
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.social_cancel), maxLines = 1)
            }
        }
    }
}
