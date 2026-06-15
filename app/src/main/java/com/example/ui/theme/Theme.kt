package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = GitBluePrimary,
    onPrimary = androidx.compose.ui.graphics.Color.Black,
    primaryContainer = GitBlueContainer,
    onPrimaryContainer = androidx.compose.ui.graphics.Color.White,
    secondary = GitPurpleSecondary,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = GitPurpleContainer,
    onSecondaryContainer = androidx.compose.ui.graphics.Color.White,
    tertiary = GitGreenTertiary,
    background = AppDarkBackground,
    onBackground = TextPrimary,
    surface = AppDarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = AppDarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = androidx.compose.ui.graphics.Color(0xFFF85149),
    errorContainer = androidx.compose.ui.graphics.Color(0xFF8E1519),
    onErrorContainer = androidx.compose.ui.graphics.Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force Dark theme
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Force custom Git theme
    content: @Composable () -> Unit,
) {
    val colorScheme = DarkColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
