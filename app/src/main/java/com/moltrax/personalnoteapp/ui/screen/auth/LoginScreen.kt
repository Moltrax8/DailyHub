package com.moltrax.personalnoteapp.ui.screen.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.ui.components.DhCard
import com.moltrax.personalnoteapp.ui.navigation.Home
import com.moltrax.personalnoteapp.ui.navigation.Login
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(nav: NavController, vm: AuthViewModel = hiltViewModel()) {
    val isSignedIn by vm.isSignedIn.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.checkExistingSignIn() }
    LaunchedEffect(isSignedIn) {
        if (isSignedIn) {
            nav.navigate(Home) { popUpTo<Login> { inclusive = true } }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val consentDeniedMsg = stringResource(R.string.login_drive_consent_denied)
    val retryLabel = stringResource(R.string.action_retry)
    val driveErrorPrefix = stringResource(R.string.login_drive_permission_error, "")
    var consentRetryTick by remember { mutableStateOf(0) }

    fun showSnackbarWithRetry(message: String) {
        scope.launch {
            val res = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = retryLabel,
                duration = SnackbarDuration.Long,
            )
            if (res == SnackbarResult.ActionPerformed) {
                consentRetryTick++
            }
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            showSnackbarWithRetry(consentDeniedMsg)
        }
    }

    LaunchedEffect(consentRetryTick) {
        if (consentRetryTick > 0) {
            vm.ensureDriveConsent(
                onConsentRequired = { intent -> runCatching { consentLauncher.launch(intent) } },
                onError = { msg -> showSnackbarWithRetry(driveErrorPrefix + msg) },
            )
        }
    }

    fun requestDriveConsent() {
        vm.ensureDriveConsent(
            onConsentRequired = { intent -> runCatching { consentLauncher.launch(intent) } },
            onError = { msg -> showSnackbarWithRetry(driveErrorPrefix + msg) },
        )
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.handleSignInResult(result.data)
            requestDriveConsent()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        val context = LocalContext.current
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(72.dp)) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Workspaces, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp))
                }
            }
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(
                stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DhCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row2(Icons.Filled.CloudOff, stringResource(R.string.login_offline_title), stringResource(R.string.login_offline_desc))
                    Row2(Icons.Filled.CheckCircle, stringResource(R.string.login_sync_title), stringResource(R.string.login_sync_desc))
                }
            }
            Button(
                onClick = { launcher.launch(vm.signInIntent) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.login_button), style = MaterialTheme.typography.labelLarge)
            }
            // Publishing is not possible (Google verification), so sign-in is
            // granted per-person: tapping opens the owner's Telegram profile.
            // Quiet tonal info block (not a bordered card).
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                androidx.compose.foundation.layout.Column(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            stringResource(R.string.login_access_msg),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Moltrax")),
                                )
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text(stringResource(R.string.login_access_action)) }
                }
            }
            TextButton(onClick = { nav.navigate(Home) { popUpTo<Login> { inclusive = true } } }) {
                Text(stringResource(R.string.login_continue_offline))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Row2(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
