package com.verimark.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VeriGreen = Color(0xFF1B5E20)
private val VeriGreenDark = Color(0xFFA5D6A7)
private val VeriGreenDeep = Color(0xFF102A12)

private val LightColors = lightColorScheme(
    primary = VeriGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F0B4),
    onPrimaryContainer = VeriGreenDeep,
    secondary = Color(0xFF52634F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E8CF),
    onSecondaryContainer = Color(0xFF101F10),
    surface = Color(0xFFFCFDF6),
    onSurface = Color(0xFF1A1C19),
    background = Color(0xFFF7FAF4),
    onBackground = Color(0xFF1A1C19)
)

private val DarkColors = darkColorScheme(
    primary = VeriGreenDark,
    onPrimary = Color(0xFF00390F),
    primaryContainer = Color(0xFF1E4620),
    onPrimaryContainer = Color(0xFFB7F0B4),
    secondary = Color(0xFFB9CCB3),
    onSecondary = Color(0xFF233523),
    secondaryContainer = Color(0xFF3A4B37),
    onSecondaryContainer = Color(0xFFD5E8CF),
    surface = Color(0xFF111411),
    onSurface = Color(0xFFE2E3DD),
    background = Color(0xFF111411),
    onBackground = Color(0xFFE2E3DD)
)

@Composable
fun VeriMarkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
