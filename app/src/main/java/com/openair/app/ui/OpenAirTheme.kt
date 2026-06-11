package com.openair.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OpenAirColors = darkColorScheme(
    primary = Color(0xFF8FD5C2),
    onPrimary = Color(0xFF06231D),
    secondary = Color(0xFFFFD166),
    onSecondary = Color(0xFF2A1B00),
    tertiary = Color(0xFFFF8A7A),
    background = Color(0xFF111827),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF172033),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF243044),
    onSurfaceVariant = Color(0xFFC8D1DF)
)

@Composable
fun OpenAirTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OpenAirColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
