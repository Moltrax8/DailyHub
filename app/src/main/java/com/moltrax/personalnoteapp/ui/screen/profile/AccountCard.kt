package com.moltrax.personalnoteapp.ui.screen.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.moltrax.personalnoteapp.FeatureFlags
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.data.remote.supabase.SessionState
import com.moltrax.personalnoteapp.domain.model.FriendRequestStatus
import com.moltrax.personalnoteapp.ui.navigation.SocialGraph
import com.moltrax.personalnoteapp.ui.navigation.SupabaseAuth
import com.moltrax.personalnoteapp.ui.screen.account.SupabaseAuthViewModel
import com.moltrax.personalnoteapp.ui.screen.social.SocialViewModel
import kotlinx.coroutines.launch

/**
 * Managed-account card (Phase 3+): sign-in entry when signed out; avatar
 * (Photo Picker → upload), username editor, friends entry and sign-out
 * when signed in. Hidden unless Supabase is enabled.
 */
@Composable
fun AccountCard(
    nav: NavController,
    authVm: SupabaseAuthViewModel = hiltViewModel(),
    socialVm: SocialViewModel = hiltViewModel(),
) {
    if (!FeatureFlags.SUPABASE_ENABLED) return
    val session by authVm.sessionState.collectAsStateWithLifecycle()
    val social by socialVm.state.collectAsStateWithLifecycle()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.supabase_auth_title), style = MaterialTheme.typography.titleMedium)
            when (val s = session) {
                SessionState.SignedOut -> {
                    Text(
                        stringResource(R.string.profile_account_offline_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { nav.navigate(SupabaseAuth) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.supabase_auth_sign_in)) }
                }
                is SessionState.SignedIn -> {
                    ProfileRow(
                        avatarUrl = social.myProfile?.avatarUrl,
                        username = social.myProfile?.username
                            ?: stringResource(R.string.profile_account_loading),
                        displayName = social.myProfile?.displayName,
                        busy = social.busy,
                        onPickAvatar = { bytes, ext -> socialVm.uploadMyAvatar(bytes, ext) },
                        onRename = { name, done -> socialVm.renameMe(name, done) },
                    )
                    social.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    val pending = social.incoming.count { it.status == FriendRequestStatus.PENDING }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BadgedBox(
                            badge = { if (pending > 0) Badge { Text(pending.toString()) } },
                            modifier = Modifier.weight(1f),
                        ) {
                            OutlinedButton(
                                onClick = { nav.navigate(SocialGraph) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.social_title)) }
                        }
                        OutlinedButton(
                            onClick = { authVm.signOut() },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.profile_sign_out)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(
    avatarUrl: String?,
    username: String,
    displayName: String?,
    busy: Boolean,
    onPickAvatar: (ByteArray, String) -> Unit,
    onRename: (String, () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showRename by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val ext = when {
                    mime.endsWith("png") -> "png"
                    mime.endsWith("webp") -> "webp"
                    else -> "jpg"
                }
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Unreadable image.")
                onPickAvatar(bytes, ext)
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (avatarUrl.isNullOrBlank()) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(CircleShape)
                    .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AsyncImage(
                model = avatarUrl,
                contentDescription = stringResource(R.string.profile_avatar),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(CircleShape)
                    .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(username, style = MaterialTheme.typography.bodyLarge)
            displayName?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = { showRename = true }) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.profile_rename))
            }
        }
    }

    if (showRename) {
        var name by remember(username) { mutableStateOf(username) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.supabase_auth_pick_username)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { v -> name = v },
                    label = { Text(stringResource(R.string.supabase_auth_username)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(name.trim()) { showRename = false } }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
