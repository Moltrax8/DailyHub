package com.moltrax.personalnoteapp.ui.screen.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.moltrax.personalnoteapp.FeatureFlags
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.ui.navigation.Login
import com.moltrax.personalnoteapp.ui.navigation.Settings
import com.moltrax.personalnoteapp.ui.screen.home.BottomNavBar
import com.moltrax.personalnoteapp.ui.screen.settings.SettingsViewModel
import com.moltrax.personalnoteapp.ui.theme.AppColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Profil ekranı, uygulamanın genel karanlık/neon (mor) paletini kullanır — tüm ekranlarla tutarlı.
 * Roller (panel, kenar, vurgu, metin) doğrudan paylaşılan [AppColors] üzerinden tanımlanır.
 */
private object SoloColors {
    val BgTop      = AppColors.BgDeep
    val BgBottom   = AppColors.BgSurface
    val Panel      = AppColors.BgCard
    val PanelEdge  = AppColors.Accent
    val Neon       = AppColors.Accent
    val NeonDeep   = AppColors.Accent
    val TextBright = AppColors.TextPrimary
    val TextDim    = AppColors.TextSecondary
    val Track      = AppColors.BgSurface
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    nav: NavController,
    vm: SettingsViewModel = hiltViewModel(),
    pvm: ProfileViewModel = hiltViewModel(),
) {
    val birthDate by vm.birthDate.collectAsStateWithLifecycle()
    val age by vm.age.collectAsStateWithLifecycle()
    val status by pvm.uiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var showNameEditor by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = { BottomNavBar(nav) },
        modifier = Modifier.background(
            Brush.verticalGradient(listOf(SoloColors.BgTop, SoloColors.BgBottom)),
        ),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HunterHeaderPanel(status, onEditName = { showNameEditor = true })
            SettingsPanel(
                birthDate = birthDate,
                age = age,
                onPickDate = { showDatePicker = true },
                onOpenSettings = { nav.navigate(Settings) },
            )

            // Drive sync kapalıyken çıkış gizlenir (SettingsScreen ile tutarlı):
            // Login'e gidip çıkmaz sokakta kalmayı önler.
            if (FeatureFlags.DRIVE_SYNC_ENABLED) {
                Button(
                    onClick = { vm.signOut { nav.navigate(Login) { popUpTo(0) { inclusive = true } } } },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.profile_sign_out))
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    if (showDatePicker) {
        val initialMillis = birthDate
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
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
        NameEditorDialog(
            initialName = status.displayName,
            onDismiss = { showNameEditor = false },
            onSave = { name ->
                vm.setDisplayName(name)
                showNameEditor = false
            },
        )
    }
}

@Composable
private fun NameEditorDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_edit_name_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.profile_name_label)) },
                singleLine = true,
            )
        },
        // Boş bırakılırsa override temizlenir → Google hesabı adına geri döner.
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) } },
    )
}


// ----------------------------------------------------------------------------
// Statü Penceresi panelleri
// ----------------------------------------------------------------------------

/** Neon kenarlı, yarı saydam tematik panel — "system window" estetiği. */
@Composable
private fun StatusPanel(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SoloColors.Panel)
            .border(1.dp, SoloColors.PanelEdge.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(16.dp),
    ) {
        if (title != null) {
            Text(
                "⟦ $title ⟧",
                color = SoloColors.Neon,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = SoloColors.PanelEdge.copy(alpha = 0.25f))
            Spacer(Modifier.height(14.dp))
        }
        content()
    }
}

@Composable
private fun HunterHeaderPanel(status: ProfileUiState, onEditName: () -> Unit) {
    StatusPanel(title = stringResource(R.string.profile_panel_profile)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Parlayan dairesel avatar
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(SoloColors.NeonDeep.copy(alpha = 0.18f))
                    .border(2.dp, SoloColors.Neon, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val photo = status.photoUrl
                if (photo != null) {
                    AsyncImage(
                        model = photo,
                        contentDescription = stringResource(R.string.cd_profile_photo),
                        modifier = Modifier.size(68.dp).clip(CircleShape),
                    )
                } else {
                    Icon(Icons.Filled.Person, contentDescription = null,
                        tint = SoloColors.Neon, modifier = Modifier.size(36.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            // İsme tıklanabilir; yanındaki kalem ikonu da düzenleme diyaloğunu açar.
            Row(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).clickable(onClick = onEditName),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    status.displayName,
                    color = SoloColors.TextBright,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.profile_edit_name_title),
                    tint = SoloColors.Neon,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}


// ----------------------------------------------------------------------------
// Ayar/düzenleme paneli (tematik kabuk içinde mevcut işlevler)
// ----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPanel(
    birthDate: LocalDate?,
    age: Int?,
    onPickDate: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFmt = remember(locale) { DateTimeFormatter.ofPattern("d MMMM yyyy", locale) }
    StatusPanel(title = stringResource(R.string.profile_panel_info)) {
        SettingRow(
            icon = { Icon(Icons.Filled.Cake, contentDescription = null, tint = SoloColors.Neon) },
            title = stringResource(R.string.profile_birthdate),
            subtitle = birthDate?.format(dateFmt) ?: stringResource(R.string.profile_not_selected_tap),
            trailing = { age?.let { Text(stringResource(R.string.profile_age_value, it), color = SoloColors.Neon, fontSize = 13.sp) } },
            onClick = onPickDate,
        )
        ThemedRowDivider()
        SettingRow(
            icon = { Icon(Icons.Filled.Settings, contentDescription = null, tint = SoloColors.Neon) },
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.profile_settings_subtitle),
            trailing = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = SoloColors.TextDim) },
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun SettingRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = SoloColors.TextBright, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = SoloColors.TextDim, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun ThemedRowDivider() {
    Spacer(Modifier.height(10.dp))
    HorizontalDivider(color = SoloColors.PanelEdge.copy(alpha = 0.18f))
    Spacer(Modifier.height(10.dp))
}
