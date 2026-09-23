package com.dragansim.aureavision.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.dragansim.aureavision.domain.ThemeMode

private val DarkColors = darkColorScheme(
    primary = GoldSoft,
    onPrimary = Ink,
    primaryContainer = Gold,
    onPrimaryContainer = Ink,
    secondary = Gold,
    surface = SurfaceDark,
    onSurface = Color(0xFFE8E3D8),
    surfaceVariant = Ink,
    onSurfaceVariant = MutedDark,
    outline = LineDark,
    error = Danger,
    background = Ink,
    onBackground = Color(0xFFE8E3D8),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6B4E22),
    onPrimary = Paper,
    primaryContainer = GoldSoft,
    onPrimaryContainer = Ink,
    secondary = Color(0xFF6B4E22),
    surface = SurfaceLight,
    onSurface = Color(0xFF1C1A16),
    surfaceVariant = Paper,
    onSurfaceVariant = MutedLight,
    outline = LineLight,
    error = Danger,
    background = Paper,
    onBackground = Color(0xFF1C1A16),
)

@Composable
fun AppTheme(
    mode: ThemeMode = ThemeMode.Dark,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
