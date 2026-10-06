package com.iranjan.hotspotscheduler.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006E54),
    primaryContainer = Color(0xFF84FAC6),
    secondary = Color(0xFF4A635D),
    secondaryContainer = Color(0xFFCCE8E1),
    tertiary = Color(0xFF3B605A),
    tertiaryContainer = Color(0xFFBDF0E8),
    background = Color(0xFFF7FDFB),
    onBackground = Color(0xFF191F1E),
    surface = Color(0xFFF7FDFB),
    onSurface = Color(0xFF191F1E)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF66DDB6),
    primaryContainer = Color(0xFF00533D),
    secondary = Color(0xFFB0C9C2),
    secondaryContainer = Color(0xFF334A45),
    tertiary = Color(0xFFA2D5CE),
    tertiaryContainer = Color(0xFF234943),
    background = Color(0xFF191F1E),
    onBackground = Color(0xFFE2E9E7),
    surface = Color(0xFF191F1E),
    onSurface = Color(0xFFE2E9E7)
)

@Composable
fun HotspotSchedulerTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}