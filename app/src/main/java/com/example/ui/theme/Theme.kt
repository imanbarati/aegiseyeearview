package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TacticalColorScheme = darkColorScheme(
    primary = DivisionOrange,
    onPrimary = TacticalDarkBg,
    primaryContainer = TacticalSurfaceElevated,
    onPrimaryContainer = DivisionOrange,
    secondary = TacticalCyan,
    onSecondary = TacticalDarkBg,
    secondaryContainer = TacticalSurfaceElevated,
    onSecondaryContainer = TacticalCyan,
    tertiary = DivisionAmber,
    onTertiary = TacticalDarkBg,
    background = TacticalDarkBg,
    onBackground = TacticalTextPrimary,
    surface = TacticalSurface,
    onSurface = TacticalTextPrimary,
    surfaceVariant = TacticalSurfaceElevated,
    onSurfaceVariant = TacticalTextSecondary,
    outline = TacticalBorder,
    outlineVariant = TacticalBorderHighlight,
    error = ThreatCriticalRed,
    onError = TacticalDarkBg
)

@Composable
fun AegisEyeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TacticalColorScheme,
        typography = Typography,
        content = content
    )
}
