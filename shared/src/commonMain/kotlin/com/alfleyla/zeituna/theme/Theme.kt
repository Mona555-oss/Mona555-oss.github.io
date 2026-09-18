package com.alfleyla.zeituna.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CreamyColorPalette = lightColors(
    primary = Color(0xFF556B2F), // Olive Green
    primaryVariant = Color(0xFF556B2F),
    secondary = Color(0xFF8B9467),
    // Theme colors exactly from your screenshot
    background = Color(0xFFF7F8F0), // Very light cream/off-white background
    surface = Color(0xFFFFFFFF),    // Pure white for cards to stand out
    onPrimary = Color.White,
    onSecondary = Color.White,
    // Using Olive Green for text to match the screenshot design
    onBackground = Color(0xFF556B2F), 
    onSurface = Color(0xFF556B2F),
)

@Composable
fun ZeitunaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = CreamyColorPalette,
        content = content
    )
}
