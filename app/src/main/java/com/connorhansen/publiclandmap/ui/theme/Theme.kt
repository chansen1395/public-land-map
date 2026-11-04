package com.connorhansen.publiclandmap.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryGreenLight,
    onPrimaryContainer = PrimaryGreenDark,
    secondary = SecondaryBrown,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryBrown,
    onSecondaryContainer = SecondaryBrownDark,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnBackgroundLight,
    error = ErrorRed,
    onError = OnPrimary
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGreenLight,
    onPrimary = PrimaryGreenDark,
    primaryContainer = PrimaryGreen,
    onPrimaryContainer = PrimaryGreenLight,
    secondary = SecondaryBrown,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryBrownDark,
    onSecondaryContainer = SecondaryBrown,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnBackgroundDark,
    error = ErrorRed,
    onError = OnPrimary
)

@Composable
fun PublicLandMapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
