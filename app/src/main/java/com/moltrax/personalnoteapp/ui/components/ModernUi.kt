package com.moltrax.personalnoteapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Shared modern building blocks: one section header, one empty state, one
 * card style. New screens should use these instead of inventing new radii.
 */

/** Title + optional subtitle + optional trailing action, one consistent rhythm. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        action?.let {
            Spacer(Modifier.height(4.dp))
            it()
        }
    }
}

/** Centered icon + title + description + optional action (guided empty lists). */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action?.let { it() }
    }
}

/** The one card style: tonal surface, 20dp radius, 12dp content padding. */
@Composable
fun ModernCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    val shape = MaterialTheme.shapes.medium
    if (onClick != null) {
        androidx.compose.material3.Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = colors,
        ) { Column(Modifier.fillMaxWidth().padding(12.dp)) { content() } }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = colors,
        ) { Column(Modifier.fillMaxWidth().padding(12.dp)) { content() } }
    }
}
