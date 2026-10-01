package com.moltrax.personalnoteapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
 * Uygulama TEK bir tutarlı karanlık/neon tema kullanır (açık tema yoktur).
 */
@Composable
fun AppTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content     = content,
    )
}
