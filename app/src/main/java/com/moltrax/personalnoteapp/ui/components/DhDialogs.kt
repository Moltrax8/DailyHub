package com.moltrax.personalnoteapp.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

// ---------------------------------------------------------------------------
// Dialogs: one destructive/neutral confirmation
// ---------------------------------------------------------------------------

/** Destructive confirmation dialog with consistent copy + roles. */
@Composable
fun DhConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = null,
    isDestructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            if (isDestructive) {
                Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text(confirmLabel)
                }
            } else {
                TextButton(onClick = onConfirm) { Text(confirmLabel) }
            }
        },
        dismissButton = dismissLabel?.let { { TextButton(onClick = onDismiss) { Text(it) } } },
        shape = MaterialTheme.shapes.large,
    )
}
