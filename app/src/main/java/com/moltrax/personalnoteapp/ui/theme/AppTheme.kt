package com.moltrax.personalnoteapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightScheme = lightColorScheme(
    primary = AppColors.AccentStrong,
    onPrimary = Color.White,
    primaryContainer = AppColors.AccentSoftLight,
    onPrimaryContainer = AppColors.AccentStrong,
    secondary = Color(0xFF4A5568),
    onSecondary = Color.White,
    secondaryContainer = AppColors.LightSurfaceVariant,
    onSecondaryContainer = AppColors.LightTextPrimary,
    tertiary = Color(0xFF2E9E5B),
    background = AppColors.LightBackground,
    onBackground = AppColors.LightTextPrimary,
    surface = AppColors.LightSurface,
    onSurface = AppColors.LightTextPrimary,
    surfaceVariant = AppColors.LightSurfaceVariant,
    onSurfaceVariant = AppColors.LightTextSecondary,
    surfaceContainer = AppColors.LightSurfaceVariant,
    surfaceContainerHigh = AppColors.LightSurfaceContainer,
    outline = AppColors.LightBorder,
    outlineVariant = AppColors.LightBorder,
    error = AppColors.Error,
    onError = Color.White,
    errorContainer = AppColors.ErrorContainerLight,
    onErrorContainer = Color(0xFF7A2323),
    surfaceTint = AppColors.AccentStrong,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9DB1FF),
    onPrimary = Color(0xFF101A3A),
    primaryContainer = AppColors.AccentSoftDark,
    onPrimaryContainer = Color(0xFFD4DCFF),
    secondary = Color(0xFF9AA3B2),
    onSecondary = Color(0xFF161A23),
    secondaryContainer = AppColors.BgContainer,
    onSecondaryContainer = AppColors.TextPrimary,
    tertiary = Color(0xFF7BD39A),
    background = AppColors.BgDeep,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.BgSurface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.BgCard,
    onSurfaceVariant = AppColors.TextSecondary,
    surfaceContainer = AppColors.BgCard,
    surfaceContainerHigh = AppColors.BgContainer,
    outline = AppColors.BorderSubtle,
    outlineVariant = AppColors.BorderSubtle,
    error = Color(0xFFFF8A8A),
    onError = Color(0xFF3E2222),
    errorContainer = AppColors.ErrorContainerDark,
    onErrorContainer = Color(0xFFF6C9C9),
    surfaceTint = Color(0xFF9DB1FF),
)

/**
 * Intentional type hierarchy. Screens must consume these roles instead of
 * hardcoding fontSize values.
 *
 * - displaySmall: hero numbers (workout values, counts)
 * - headlineSmall/Medium: screen titles
 * - titleMedium: card titles, section titles
 * - titleSmall: list item titles, labels of emphasis
 * - bodyMedium: default body, form content
 * - bodySmall: metadata, secondary text, timestamps
 * - labelMedium: buttons, chips, segmented controls
 * - labelSmall: captions, overlines, tiny metadata
 */
private val DhTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.25).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.3.sp),
)

/**
 * Restrained radius system: inputs/buttons/chips small, cards medium,
 * sheets/dialogs large. No giant rounded rectangles by default.
 */
private val DhShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Appearance modes persisted in DataStore ("system" default). */
object DhThemeMode {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"
}

/**
 * DailyHub theme: explicit light + dark schemes, semantic typography,
 * restrained shapes, spacing + extra semantic colors.
 *
 * @param themeMode one of "system" | "light" | "dark".
 */
@Composable
fun AppTheme(
    themeMode: String = DhThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode.lowercase()) {
        DhThemeMode.LIGHT -> false
        DhThemeMode.DARK -> true
        else -> systemDark
    }
    val scheme = if (dark) DarkScheme else LightScheme
    val extra = if (dark) {
        DhExtraColors(
            success = Color(0xFF7BD39A),
            onSuccessContainer = Color(0xFFC9ECD6),
            successContainer = AppColors.SuccessContainerDark,
            warning = Color(0xFFE5B45C),
            onWarningContainer = Color(0xFFF3DCAE),
            warningContainer = AppColors.WarningContainerDark,
            info = Color(0xFF9DB1FF),
            textMuted = AppColors.TextMuted,
            borderSubtle = AppColors.BorderSubtle,
            accentSoft = AppColors.AccentSoftDark,
        )
    } else {
        DhExtraColors(
            success = AppColors.Success,
            onSuccessContainer = Color(0xFF1C5C36),
            successContainer = AppColors.SuccessContainerLight,
            warning = AppColors.Warning,
            onWarningContainer = Color(0xFF6B4A14),
            warningContainer = AppColors.WarningContainerLight,
            info = AppColors.Info,
            textMuted = AppColors.LightTextMuted,
            borderSubtle = AppColors.LightBorder,
            accentSoft = AppColors.AccentSoftLight,
        )
    }
    CompositionLocalProvider(
        LocalDhSpacing provides DhSpacing(),
        LocalDhExtraColors provides extra,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = DhTypography,
            shapes = DhShapes,
            content = content,
        )
    }
}

/** Convenience accessors inside composition. */
object Dh {
    val spacing: DhSpacing
        @Composable get() = LocalDhSpacing.current
    val extra: DhExtraColors
        @Composable get() = LocalDhExtraColors.current
}
