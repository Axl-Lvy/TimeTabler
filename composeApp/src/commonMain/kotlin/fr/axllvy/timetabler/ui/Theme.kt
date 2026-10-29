package fr.axllvy.timetabler.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colors =
  darkColorScheme(
    primary = Color(0xFFE0457B),
    secondary = Color(0xFF5B3BD6),
    background = Color(0xFF111111),
    surface = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFF2A2A2A),
  )

/** Dark theme of the app, made for reading at night. */
@Composable
fun TimeTablerTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = colors, content = content)
}
