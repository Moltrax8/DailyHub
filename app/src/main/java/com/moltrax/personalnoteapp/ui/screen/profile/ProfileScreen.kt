package com.moltrax.personalnoteapp.ui.screen.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.moltrax.personalnoteapp.FeatureFlags
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.data.remote.supabase.SessionState
import com.moltrax.personalnoteapp.ui.components.DhCard
import com.moltrax.personalnoteapp.ui.components.DhSectionHeader
import com.moltrax.personalnoteapp.ui.components.DhSettingsRow
import com.moltrax.personalnoteapp.ui.navigation.Login
import com.moltrax.personalnoteapp.ui.navigation.Settings
import com.moltrax.personalnoteapp.ui.screen.account.SupabaseAuthViewModel
import com.moltrax.personalnoteapp.ui.screen.settings.SettingsViewModel
import com.moltrax.personalnoteapp.ui.screen.social.SocialViewModel
import com.moltrax.personalnoteapp.ui.theme.AppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    nav: NavController,
    vm: SettingsViewModel = hiltViewModel(),
    pvm: ProfileViewModel = hiltViewModel(),
    authVm: SupabaseAuthViewModel = hiltViewModel(),
    socialVm: SocialViewModel = hiltViewModel(),
) {
    val birthDate by vm.birthDate.collectAsStateWithLifecycle()
    val age by vm.age.collectAsStateWithLifecycle()
    val status by pvm.uiState.collectAsStateWithLifecycle()
    val session by authVm.sessionState.collectAsStateWithLifecycle()
    val social by socialVm.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var showNameEditor by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    val signedIn = session is SessionState.SignedIn
    val headerAvatar = social.myProfile?.avatarUrl?.takeIf { it.isNotBlank() } ?: status.photoUrl
    val headerUsername = social.myProfile?.username?.takeIf { signedIn }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null || !signedIn) return@rememberLauncherForActivityResult
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
                socialVm.uploadMyAvatar(bytes, ext)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_panel_profile), style = MaterialTheme.typography.titleMedium) },
                actions = {
                    IconButton(onClick = { nav.navigate(Settings) }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Identity header: avatar + name + handle.
            DhCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(64.dp)
                            .let { m ->
                                if (signedIn) m.clickable {
                                    avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                } else m
                            },
                    ) {
                        if (headerAvatar != null) {
                            AsyncImage(
                                model = headerAvatar,
                                contentDescription = stringResource(R.string.cd_profile_photo),
                                modifier = Modifier.size(64.dp).clip(CircleShape),
                            )
                        } else {
                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                status.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(onClick = { showNameEditor = true }) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.profile_edit_name_title), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        headerUsername?.takeIf { it.isNotBlank() }?.let {
                            Text("@$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (signedIn) {
                            Text(
                                stringResource(R.string.profile_tap_avatar_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Personal section.
            DhSectionHeader(
                title = stringResource(R.string.profile_panel_info),
                subtitle = stringResource(R.string.profile_info_subtitle),
            )
            DhCard {
                val locale = LocalConfiguration.current.locales[0]
                val dateFmt = remember(locale) { DateTimeFormatter.ofPattern("d MMMM yyyy", locale) }
                DhSettingsRow(
                    title = stringResource(R.string.profile_birthdate),
                    supporting = birthDate?.format(dateFmt) ?: stringResource(R.string.profile_not_selected_tap),
                    leading = { Icon(Icons.Filled.Cake, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailing = { age?.let { Text(stringResource(R.string.profile_age_value, it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) } },
                    onClick = { showDatePicker = true },
                )
            }

            // Managed account section (Supabase): sign-in, friends, sign-out.
            DhSectionHeader(
                title = stringResource(R.string.profile_panel_account),
                subtitle = stringResource(R.string.profile_account_subtitle),
            )
            AccountCard(nav)

            if (FeatureFlags.DRIVE_SYNC_ENABLED) {
                OutlinedButton(
                    onClick = { showSignOutConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.profile_sign_out))
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text(stringResource(R.string.profile_sign_out), style = MaterialTheme.typography.titleMedium) },
            text = { Text(stringResource(R.string.profile_sign_out_confirm), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutConfirm = false
                    vm.signOut { nav.navigate(Login) { popUpTo(0) { inclusive = true } } }
                }) { Text(stringResource(R.string.profile_sign_out)) }
            },
            dismissButton = { TextButton(onClick = { showSignOutConfirm = false }) { Text(stringResource(R.string.action_dismiss)) } },
            shape = MaterialTheme.shapes.large,
        )
    }

    if (showDatePicker) {
        val initialMillis = birthDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val today = remember { LocalDate.now() }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return !date.isAfter(today)
                }
                override fun isSelectableYear(year: Int): Boolean = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            vm.setBirthDate(date)
                        }
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_dismiss)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showNameEditor) {
        var name by remember { mutableStateOf(status.displayName) }
        AlertDialog(
            onDismissRequest = { showNameEditor = false },
            title = { Text(stringResource(R.string.profile_edit_name_title), style = MaterialTheme.typography.titleMedium) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.profile_name_label)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setDisplayName(name.trim())
                    if (signedIn) socialVm.setMyDisplayName(name.trim())
                    showNameEditor = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { showNameEditor = false }) { Text(stringResource(R.string.action_dismiss)) } },
            shape = MaterialTheme.shapes.large,
        )
    }
}

@Preview(name = "Profile — light", showBackground = true)
@Composable
private fun ProfilePreview() {
    AppTheme(themeMode = "light") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DhSectionHeader(title = "Profile", subtitle = "Personal identity")
            DhCard {
                DhSettingsRow(title = "Birthday", supporting = "12 May 1990", trailing = { Text("36") })
            }
        }
    }
}
