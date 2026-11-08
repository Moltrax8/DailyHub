package com.moltrax.personalnoteapp.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

// ---------------------------------------------------------------------------
// Top bar: one shared bar — title titleLarge, back at start, actions at end.
// The bar owns the ONE status-bar inset (TopAppBarDefaults.windowInsets);
// screens must not add statusBarsPadding.
// ---------------------------------------------------------------------------

/**
 * Shared top bar. [onBack] shows the back arrow at the start; [actions] sit
 * at the end. Exactly one status-bar inset lives here — callers inside
 * DailyHubScaffold must not add their own top inset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DhTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backContentDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = backContentDescription,
                    )
                }
            }
        },
        actions = actions,
        windowInsets = TopAppBarDefaults.windowInsets,
    )
}
