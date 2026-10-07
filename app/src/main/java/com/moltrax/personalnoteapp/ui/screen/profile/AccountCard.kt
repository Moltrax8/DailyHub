package com.moltrax.personalnoteapp.ui.screen.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moltrax.personalnoteapp.FeatureFlags
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.data.remote.supabase.SessionState
import com.moltrax.personalnoteapp.domain.model.FriendRequestStatus
import com.moltrax.personalnoteapp.ui.components.DhCard
import com.moltrax.personalnoteapp.ui.components.DhConfirmDialog
import com.moltrax.personalnoteapp.ui.navigation.SocialGraph
import com.moltrax.personalnoteapp.ui.navigation.SupabaseAuth
import com.moltrax.personalnoteapp.ui.screen.account.SupabaseAuthViewModel
import com.moltrax.personalnoteapp.ui.screen.social.SocialViewModel

/**
 * Managed-account card (Phase 3+): sign-in entry when signed out; friends
 * entry and sign-out when signed in. Identity (avatar, display name,
 * username) lives in the Profile header above — this card holds no second
 * copy. Hidden unless Supabase is enabled.
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
    var showSignOutConfirm by remember { mutableStateOf(false) }

    DhCard(modifier = Modifier.fillMaxWidth()) {
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
                            onClick = { showSignOutConfirm = true },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.profile_sign_out)) }
                    }
                }
            }
    }
    if (showSignOutConfirm) {
        DhConfirmDialog(
            title = stringResource(R.string.profile_sign_out),
            message = stringResource(R.string.profile_sign_out_confirm),
            confirmLabel = stringResource(R.string.profile_sign_out),
            onConfirm = { showSignOutConfirm = false; authVm.signOut() },
            onDismiss = { showSignOutConfirm = false },
            dismissLabel = stringResource(R.string.action_dismiss),
        )
    }
}
