package com.alfleyla.zeituna.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CreamyColorPalette = lightColors(
    primary = TurquoisePrimary, 
    primaryVariant = TurquoiseDark,
    secondary = Color(0xFF8B9467),
    // Rich Creamy Sage theme
    // Background is a muted olive-cream to provide depth behind the cards
    background = Color(0xFFDCE3C9), 
    // Surface (Cards) - Whiter cream for better pop and "whiter" look as requested
    surface = Color(0xFFF9FBF2),
    onPrimary = White,
    onSecondary = White,
    // Deep moss green for text
    onBackground = Color(0xFF2D3126), 
    onSurface = Color(0xFF2D3126),
)

@Composable
fun ZeitunaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = CreamyColorPalette,
        content = content
    )
}
