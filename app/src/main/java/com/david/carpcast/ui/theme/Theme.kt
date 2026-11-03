package com.david.carpcast.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FieldColors = darkColorScheme(
    primary = Color(0xFF8CC9F4), onPrimary = Color(0xFF082238),
    primaryContainer = Color(0xFF173951), onPrimaryContainer = Color(0xFFD4EDFF),
    secondary = Color(0xFFA7D7C1), onSecondary = Color(0xFF102C22),
    secondaryContainer = Color(0xFF1D3B32), onSecondaryContainer = Color(0xFFC6EEDD),
    tertiary = Color(0xFFF0CF91),
    background = Color(0xFF071522), onBackground = Color(0xFFF0F5FA),
    surface = Color(0xFF102333), onSurface = Color(0xFFF0F5FA),
    surfaceContainerLowest = Color(0xFF050F19), surfaceContainerLow = Color(0xFF0C1D2B),
    surfaceContainer = Color(0xFF102333), surfaceContainerHigh = Color(0xFF142B3C),
    surfaceContainerHighest = Color(0xFF193246),
    surfaceVariant = Color(0xFF1A3344), onSurfaceVariant = Color(0xFFB8CBD8),
    outline = Color(0xFF668293), outlineVariant = Color(0xFF294455),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005)
)

@Composable
fun CarpCastTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FieldColors, typography = Typography, content = content)
}
