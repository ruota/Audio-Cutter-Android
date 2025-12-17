package com.workwavestudio.audiocutter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = ElectricViolet,
    onPrimary = Color.White,
    secondary = AquaGlow,
    onSecondary = Midnight,
    tertiary = HotMagenta,
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Graphite,
    surface = Color.White,
    onSurface = Graphite,
    surfaceVariant = Color(0xFFE5E7EB),
    onSurfaceVariant = Graphite
)

private val DarkColors = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = Color.White,
    secondary = AquaGlow,
    onSecondary = Midnight,
    tertiary = HotMagenta,
    onTertiary = Color.White,
    background = Midnight,
    onBackground = Mist,
    surface = Graphite,
    onSurface = Mist,
    surfaceVariant = Color(0xFF111827),
    onSurfaceVariant = Mist
)

@Composable
fun AudioCutterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(),
        typography = Typography,
        content = content
    )
}
