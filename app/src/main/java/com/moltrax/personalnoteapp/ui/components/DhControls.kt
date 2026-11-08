package com.moltrax.personalnoteapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// Controls: the ONE shared switch and the ONE shared round check
// ---------------------------------------------------------------------------

/**
 * The single shared switch for the whole app (Settings, Task detail, ...).
 * Checked = primary track + onPrimary thumb. Unchecked = surfaceVariant track
 * with outline thumb + border. Disabled states stay explicit theme colours —
 * never the muddy default.
 */
@Composable
fun DhSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedBorderColor = MaterialTheme.colorScheme.primary,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            disabledCheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledCheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            disabledCheckedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledUncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            disabledUncheckedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        ),
    )
}

/**
 * The single shared round check for the whole app (task rows, subtask lists).
 * 26dp visual circle, checked = filled primary with onPrimary tick,
 * unchecked = outline ring. Never use raw [androidx.compose.material3.Checkbox].
 */
@Composable
fun DhCheck(
    checked: Boolean,
    onCheckedChange: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier.size(48.dp).clickable(
            enabled = enabled,
            role = Role.Button,
            onClickLabel = contentDescription,
            onClick = onCheckedChange,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(26.dp).clip(CircleShape)
                .background(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent)
                .border(
                    1.5.dp,
                    if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
