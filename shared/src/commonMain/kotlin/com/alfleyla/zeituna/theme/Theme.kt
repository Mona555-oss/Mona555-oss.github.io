package com.alfleyla.zeituna.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CreamyColorPalette = lightColors(
    primary = Color(0xFF556B2F), // Olive Green from screenshot
    primaryVariant = Color(0xFF43551D), // Deeper Olive
    secondary = Color(0xFF8B9467),
    background = Color(0xFFF7F8F0), // Light cream background from screenshot
    surface = Color(0xFFFFFFFF),    // Pure white for cards
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF556B2F), // Use Olive Green for main text/headings
    onSurface = Color(0xFF2D3126),    // Dark Moss for content text
)

@Composable
fun ZeitunaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = CreamyColorPalette,
        content = content
    )
}
