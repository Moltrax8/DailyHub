package com.moltrax.personalnoteapp.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * DailyHub semantic palette.
 *
 * Neutral graphite/slate surfaces + restrained indigo accent.
 * Accent identifies actions/selection only — never floods screens.
 * No neon, no glow.
 */
object AppColors {
    // Brand accent (cool indigo/blue), restrained use.
    val Accent = Color(0xFF4F6DF5)
    val AccentStrong = Color(0xFF3B5AE0)
    val AccentSoftLight = Color(0xFFE3E9FD)
    val AccentSoftDark = Color(0xFF25304D)

    // Kept for backward-compatible references; mapped to new accent.
    val AccentGlow = Color(0x1A4F6DF5)

    // Light theme surfaces (graphite-tinted neutrals, not pure white).
    val LightBackground = Color(0xFFF4F5F7)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFEBEDF1)
    val LightSurfaceContainer = Color(0xFFE1E4EA)
    val LightTextPrimary = Color(0xFF161A23)
    val LightTextSecondary = Color(0xFF525B6B)
    val LightTextMuted = Color(0xFF8A93A3)
    val LightBorder = Color(0xFFDDE1E8)

    // Dark theme surfaces (graphite/slate, calm).
    val BgDeep = Color(0xFF111318)
    val BgSurface = Color(0xFF191C23)
    val BgCard = Color(0xFF20242E)
    val BgContainer = Color(0xFF262B37)
    val TextPrimary = Color(0xFFE8EAF0)
    val TextSecondary = Color(0xFFA7AEBD)
    val TextMuted = Color(0xFF6E7686)
    val BorderSubtle = Color(0xFF2C323F)

    // Semantic states — readable on both themes.
    val Success = Color(0xFF2E9E5B)
    val SuccessContainerLight = Color(0xFFDDF2E5)
    val SuccessContainerDark = Color(0xFF1D3527)
    val Warning = Color(0xFFB7791F)
    val WarningContainerLight = Color(0xFFFBEED3)
    val WarningContainerDark = Color(0xFF3A2E14)
    val Error = Color(0xFFD64545)
    val ErrorContainerLight = Color(0xFFF9DEDE)
    val ErrorContainerDark = Color(0xFF3E2222)
    val Info = Color(0xFF4F6DF5)

    val PriorityHigh = Color(0xFFD64545)
    val PriorityMedium = Color(0xFFB7791F)
    val PriorityLow = Color(0xFF2E9E5B)

    // Domain tints (workout, GitHub/activity, due dates) — muted, not neon.
    val Workout = Color(0xFF2E9E5B)
    val Github = Color(0xFF6E7686)
    val DueOverdue = Color(0xFFD64545)
    val DueToday = Color(0xFFB7791F)
    val DueUpcoming = Color(0xFF525B6B)
}
