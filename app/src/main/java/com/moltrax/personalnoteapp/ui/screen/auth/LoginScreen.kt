package com.moltrax.personalnoteapp.ui.screen.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.ui.navigation.Home
import com.moltrax.personalnoteapp.ui.navigation.Login
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(nav: NavController, vm: AuthViewModel = hiltViewModel()) {
    val isSignedIn by vm.isSignedIn.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.checkExistingSignIn() }
    // Go straight to the main screen once signed in (the onboarding/physical-info step was removed).
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

    // Returning from the Drive consent screen; when denied, offer a Snackbar + retry.
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            showSnackbarWithRetry(consentDeniedMsg)
        }
    }

    // Retry request: fetch a fresh consent Intent and relaunch it (the old Intent may be single-use).
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
            // The sensitive drive.appdata permission may not have come with sign-in; chain the consent
            // flow right after a sign-in success (open the consent screen if needed).
            requestDriveConsent()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.AccountCircle, contentDescription = null,
                modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)

            Spacer(Modifier.height(24.dp))

            Text(stringResource(R.string.app_name), fontSize = 28.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground)

            Spacer(Modifier.height(8.dp))

            Text(stringResource(R.string.login_subtitle), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(48.dp))

            Button(
                onClick = { launcher.launch(vm.signInIntent) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.login_button), fontSize = 16.sp)
            }
        }
    }
}
