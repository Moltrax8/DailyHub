package com.moltrax.personalnoteapp.ui.screen.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.ui.navigation.Home
import com.moltrax.personalnoteapp.ui.navigation.SupabaseAuth

/**
 * Managed-account sign-in (Phase 3 Supabase Auth). Optional: the app is fully
 * usable offline, "continue offline" just goes Home. After credentials, first
 * logins pick a username (public identifier for friends/spaces).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupabaseAuthScreen(nav: NavController, vm: SupabaseAuthViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Auth finished (returning session or fresh username) → Home, Auth off the stack.
    LaunchedEffect(state.step) {
        if (state.step == AuthStep.Done) {
            nav.navigate(Home) { popUpTo<SupabaseAuth> { inclusive = true } }
            vm.consumeDone()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.supabase_auth_title)) }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!vm.isConfigured) {
                Text(
                    stringResource(R.string.supabase_auth_unconfigured),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            when (state.step) {
                AuthStep.Credentials -> CredentialsForm(state, vm)
                AuthStep.Username -> UsernameForm(state, vm)
                AuthStep.Done -> Unit
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { nav.navigate(Home) { popUpTo<SupabaseAuth> { inclusive = true } } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.supabase_auth_offline)) }
        }
    }
}

@Composable
private fun CredentialsForm(state: AuthUiState, vm: SupabaseAuthViewModel) {
    Text(
        if (state.signUpMode) stringResource(R.string.supabase_auth_create)
        else stringResource(R.string.supabase_auth_welcome),
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
    )
    OutlinedTextField(
        value = state.email,
        onValueChange = { v -> vm.update { it.copy(email = v) } },
        label = { Text(stringResource(R.string.supabase_auth_email)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = state.password,
        onValueChange = { v -> vm.update { it.copy(password = v) } },
        label = { Text(stringResource(R.string.supabase_auth_password)) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Button(
        onClick = { vm.submitCredentials() },
        enabled = !state.busy && vm.isConfigured,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.busy) CircularProgressIndicator(
            modifier = Modifier.padding(end = 8.dp),
            strokeWidth = 2.dp,
        )
        Text(
            if (state.signUpMode) stringResource(R.string.supabase_auth_sign_up)
            else stringResource(R.string.supabase_auth_sign_in),
        )
    }
    TextButton(onClick = { vm.update { it.copy(signUpMode = !it.signUpMode) } }) {
        Text(
            if (state.signUpMode) stringResource(R.string.supabase_auth_have_account)
            else stringResource(R.string.supabase_auth_need_account),
        )
    }
}

@Composable
private fun UsernameForm(state: AuthUiState, vm: SupabaseAuthViewModel) {
    Text(
        stringResource(R.string.supabase_auth_pick_username),
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
    )
    Text(
        stringResource(R.string.supabase_auth_username_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = state.username,
        onValueChange = { v -> vm.update { it.copy(username = v) } },
        label = { Text(stringResource(R.string.supabase_auth_username)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Button(
        onClick = { vm.submitUsername() },
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.busy) CircularProgressIndicator(
            modifier = Modifier.padding(end = 8.dp),
            strokeWidth = 2.dp,
        )
        Text(stringResource(R.string.supabase_auth_continue))
    }
}
