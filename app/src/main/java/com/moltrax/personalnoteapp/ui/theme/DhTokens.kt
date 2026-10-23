package com.moltrax.personalnoteapp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing scale — structural paddings should come from here. */
@Immutable
data class DhSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp,
)

val LocalDhSpacing = staticCompositionLocalOf { DhSpacing() }

/** Extended semantic colors not covered by MaterialTheme.colorScheme. */
@Immutable
data class DhExtraColors(
    val success: Color,
    val onSuccessContainer: Color,
    val successContainer: Color,
    val warning: Color,
    val onWarningContainer: Color,
    val warningContainer: Color,
    val info: Color,
    val textMuted: Color,
    val borderSubtle: Color,
    val accentSoft: Color,
)

val LocalDhExtraColors = staticCompositionLocalOf {
    DhExtraColors(
        success = AppColors.Success,
        onSuccessContainer = AppColors.Success,
        successContainer = AppColors.SuccessContainerLight,
        warning = AppColors.Warning,
        onWarningContainer = AppColors.Warning,
        warningContainer = AppColors.WarningContainerLight,
        info = AppColors.Info,
        textMuted = AppColors.LightTextMuted,
        borderSubtle = AppColors.LightBorder,
        accentSoft = AppColors.AccentSoftLight,
    )
}

object DhTokens {
    /** Single-column content max width so tablets don't stretch phone rows. */
    val MaxContentWidth = 720.dp
    /** Minimum touch target per accessibility pass. */
    val MinTouchTarget = 48.dp
}
