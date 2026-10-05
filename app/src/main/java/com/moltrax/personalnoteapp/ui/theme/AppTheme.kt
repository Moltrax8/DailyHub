package com.moltrax.personalnoteapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary          = AppColors.Accent,
    onPrimary        = Color.White,
    primaryContainer = AppColors.AccentGlow,
    background       = AppColors.BgDeep,
    surface          = AppColors.BgSurface,
    surfaceVariant   = AppColors.BgCard,
    onBackground     = AppColors.TextPrimary,
    onSurface        = AppColors.TextPrimary,
    onSurfaceVariant = AppColors.TextSecondary,
    outline          = AppColors.BorderSubtle,
    error            = AppColors.Error,
)

/**
 * The app uses a SINGLE consistent dark/neon theme (there is no light theme).
 * Shapes are modern-rounded everywhere: cards 20dp, sheets/dialogs 28dp,
 * inputs/buttons/chips 12-16dp via the M3 defaults below.
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AppTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        shapes = AppShapes,
        content = content,
    )
}
