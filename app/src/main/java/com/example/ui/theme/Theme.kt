package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SecureColorScheme = darkColorScheme(
    primary = TechCyanAccent,
    secondary = ActiveSecurityGreen,
    tertiary = AlertWarningOrange,
    background = DeepSlateBg,
    surface = CardSurface,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onTertiary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    // Keep it exclusively matching our beautiful secure dark aesthetic for maximum stealth vibe!
    MaterialTheme(
        colorScheme = SecureColorScheme,
        typography = Typography,
        content = content
    )
}
