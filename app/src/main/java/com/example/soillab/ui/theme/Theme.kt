package com.example.soillab.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Cyan500,
    background = Blue900,
    surface = Blue900, // For AppBars
    onPrimary = Blue900,
    onBackground = Neutral0,
    onSurface = Neutral0,
    surfaceVariant = Blue800, // For Cards
    onSurfaceVariant = Neutral400,
    error = Red500,
    outline = Neutral400
)

private val LightColorScheme = lightColorScheme(
    primary = Cyan500,
    background = Neutral50,
    surface = Neutral0, // App bars / cards
    onPrimary = Neutral0,
    onBackground = Neutral900,
    onSurface = Neutral900,
    surfaceVariant = Neutral100,
    onSurfaceVariant = Neutral800,
    error = Red500,
    outline = Neutral200
)

@Composable
fun SoilLabTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            // Light status bar icons only for light theme
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

