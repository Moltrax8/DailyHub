package com.moltrax.personalnoteapp.ui.screen.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.domain.model.FriendRequestStatus
import com.moltrax.personalnoteapp.domain.model.SupabaseProfile
import com.moltrax.personalnoteapp.ui.navigation.FriendRequests
import com.moltrax.personalnoteapp.ui.navigation.UserProfile
import com.moltrax.personalnoteapp.ui.navigation.DuoHub
import com.moltrax.personalnoteapp.ui.screen.home.BottomNavBar
import com.moltrax.personalnoteapp.ui.screen.space.SpaceRow
import com.moltrax.personalnoteapp.ui.screen.space.SpaceViewModel

/** Friends graph: search + results, pending counts link to requests, friends list. */
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

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.social_title)) }) },
        bottomBar = { BottomNavBar(nav) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { vm.search(it) },
                    label = { Text(stringResource(R.string.social_search_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            items(state.results, key = { it.id }) { profile ->
                ProfileRow(
                    profile = profile,
                    action = {
                        val mine = state.myId
                        if (mine != null && mine != profile.id && vm.statusOf(profile.id) == null) {
                            IconButton(onClick = { vm.send(profile.id) }) {
                                Icon(Icons.Default.PersonAdd, contentDescription = stringResource(R.string.social_add))
                            }
                        }
                    },
                    onClick = { nav.navigate(UserProfile(profile.username)) },
                )
            }
            if (state.incoming.any { it.status == FriendRequestStatus.PENDING }) {
                item {
                    OutlinedButton(
                        onClick = { nav.navigate(FriendRequests) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                R.string.social_requests_pending,
                                state.incoming.count { it.status == FriendRequestStatus.PENDING },
                            )
                        )
                    }
                }
            }
            item { Text(stringResource(R.string.social_friends), style = MaterialTheme.typography.titleMedium) }
            if (state.friends.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.social_no_friends),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.friends, key = { it.id }) { profile ->
                ProfileRow(
                    profile = profile,
                    action = {
                        OutlinedButton(onClick = {
                            spaceVm.openOrCreateDuo(profile.id, profile.username) { id ->
                                nav.navigate(DuoHub(id))
                            }
                        }) { Text(stringResource(R.string.spaces_open_hub)) }
                    },
                    onClick = { nav.navigate(UserProfile(profile.username)) },
                )
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.spaces_section), style = MaterialTheme.typography.titleMedium)
            }
            if (spaces.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.spaces_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(spaces, key = { it.id }) { space ->
                SpaceRow(
                    spaceName = space.name?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.spaces_duo),
                    typeLabel = space.type.name,
                    onClick = { nav.navigate(DuoHub(space.id)) },
                )
            }
            if (state.busy) {
                item { CircularProgressIndicator() }
            }
            state.error?.let {
                item { Text(it, color = MaterialTheme.colorScheme.error) }
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
            TopAppBar(
                title = { Text(stringResource(R.string.social_requests_title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(R.string.social_incoming), style = MaterialTheme.typography.titleMedium) }
            if (pendingIn.isEmpty()) {
                item { Text(stringResource(R.string.social_none), style = MaterialTheme.typography.bodyMedium) }
            }
            items(pendingIn, key = { it.id }) { req ->
                RequestRow(
                    title = vm.displayNameOf(req.fromId),
                    incoming = true,
                    onAccept = { vm.accept(req.id) },
                    onReject = { vm.reject(req.id) },
                    onCancel = {},
                )
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.social_outgoing), style = MaterialTheme.typography.titleMedium)
            }
            if (pendingOut.isEmpty()) {
                item { Text(stringResource(R.string.social_none), style = MaterialTheme.typography.bodyMedium) }
            }
            items(pendingOut, key = { it.id }) { req ->
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

/** One user: profile + befriend / pending / friends(+remove) action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(username: String, nav: NavController, vm: SocialViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val profile = state.results.firstOrNull { it.username.equals(username, ignoreCase = true) }
        ?: state.friends.firstOrNull { it.username.equals(username, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(username) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (profile == null) {
                Text(stringResource(R.string.social_not_found))
                return@Column
            }
            ProfileRow(profile = profile)
            val mine = state.myId
            if (mine != null && mine != profile.id) {
                when (vm.statusOf(profile.id)) {
                    null -> Button(onClick = { vm.send(profile.id) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.social_add))
                    }
                    FriendRequestStatus.PENDING -> Text(stringResource(R.string.social_pending))
                    FriendRequestStatus.ACCEPTED -> OutlinedButton(
                        onClick = { vm.removeFriend(profile.id) },
                        modifier = Modifier.fillMaxWidth(),
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
}

@Composable
private fun ProfileRow(profile: SupabaseProfile, action: @Composable () -> Unit = {}, onClick: () -> Unit = {}) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(profile.username, style = MaterialTheme.typography.bodyLarge)
                profile.displayName?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            action()
        }
    }
}

@Composable
private fun RequestRow(
    title: String,
    incoming: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (incoming) {
                IconButton(onClick = onAccept) {
                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.social_accept))
                }
                IconButton(onClick = onReject) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.social_reject))
                }
            } else {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.social_cancel))
                }
            }
        }
    }
}
