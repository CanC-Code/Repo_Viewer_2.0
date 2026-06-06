package com.explorer.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TerminalDarkScheme = darkColorScheme(
    primary = Color(0xFF4CAF50),       // Operational Green
    secondary = Color(0xFF03A9F4),     // Info Blue
    background = Color(0xFF121212),    // Panel Base Backing
    surface = Color(0xFF1E1E1E),       // Workspace Cards/Tiers
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color(0xFFE0E0E0),  // Crisp Code Typography
    onSurface = Color(0xFFEEEEEE),
    error = Color(0xFFCF6679)
)

@Composable
fun ExplorerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TerminalDarkScheme,
        content = content
    )
}
