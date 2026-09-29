package com.verimark.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B2A52),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE9FF),
    onPrimaryContainer = Color(0xFF001D35),
    secondary = Color(0xFF0A6E80),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC9F0F7),
    onSecondaryContainer = Color(0xFF012A32),
    tertiary = Color(0xFFD63847),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDADB),
    onTertiaryContainer = Color(0xFF3B0710),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    background = Color(0xFFF4F7FB),
    onBackground = Color(0xFF171C22),
    surface = Color(0xFFFBFCFE),
    onSurface = Color(0xFF171C22),
    surfaceVariant = Color(0xFFE1E8F0),
    onSurfaceVariant = Color(0xFF414A54),
    outline = Color(0xFF717982)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA6D3FF),
    onPrimary = Color(0xFF003353),
    primaryContainer = Color(0xFF144B75),
    onPrimaryContainer = Color(0xFFCDE9FF),
    secondary = Color(0xFF7ED7E7),
    onSecondary = Color(0xFF00363F),
    secondaryContainer = Color(0xFF0A4B57),
    onSecondaryContainer = Color(0xFFC9F0F7),
    tertiary = Color(0xFFFFB3B8),
    onTertiary = Color(0xFF690012),
    tertiaryContainer = Color(0xFF8F1B27),
    onTertiaryContainer = Color(0xFFFFDADB),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    background = Color(0xFF0D1119),
    onBackground = Color(0xFFE1E5EA),
    surface = Color(0xFF12161D),
    onSurface = Color(0xFFE1E5EA),
    surfaceVariant = Color(0xFF414A54),
    onSurfaceVariant = Color(0xFFC1C9D4),
    outline = Color(0xFF8B939C)
)

private val VeriMarkShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun VeriMarkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = VeriMarkShapes,
        content = content
    )
}
