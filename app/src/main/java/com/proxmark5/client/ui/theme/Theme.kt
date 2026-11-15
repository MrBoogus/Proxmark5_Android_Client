package com.proxmark5.client.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = OledBlack,
    primaryContainer = DarkSurfaceCard,
    onPrimaryContainer = ElectricCyan,
    secondary = NeonGreen,
    onSecondary = OledBlack,
    secondaryContainer = DarkSurfaceElevated,
    onSecondaryContainer = NeonGreen,
    tertiary = CyberPurple,
    onTertiary = OledBlack,
    background = OledBlack,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = DarkOutline,
    error = ErrorRed,
    onError = OledBlack
)

@Composable
fun Proxmark5Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
