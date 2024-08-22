package com.autosec.pie.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Green80,
    onPrimary = Green20,
    primaryContainer = Grey10,
    onPrimaryContainer = Grey90,
    inversePrimary = Grey90,
    secondary = DarkGreen80,
    onSecondary = DarkGreen20,
    secondaryContainer = Grey20,
    onSecondaryContainer = Grey90,
    tertiary = Violet80,
    onTertiary = Grey85,
    tertiaryContainer = Grey30,
    onTertiaryContainer = Grey85,
    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = Grey10,
    onBackground = Grey90,
    surface = Grey10,
    onSurface = Grey90,
    inverseSurface = Grey90,
    inverseOnSurface = Grey10,
    surfaceVariant = Grey30,
    onSurfaceVariant = Grey60,
    outline = GreenGrey80
)

private val LightColorScheme = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Grey99,
    onPrimaryContainer = Grey20,
    inversePrimary = Grey20,
    secondary = DarkGreen40,
    onSecondary = Color.White,
    secondaryContainer = Grey95,
    onSecondaryContainer = Grey20,
    tertiary = Violet40,
    onTertiary = Color.White,
    tertiaryContainer = Grey90,
    onTertiaryContainer = Grey10,
    error = Red40,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = Grey99,
    onBackground = Grey10,
    surface = GreenGrey90,
    onSurface = GreenGrey10,
    inverseSurface = Grey20,
    inverseOnSurface = Grey95,
    surfaceVariant = GreenGrey60,
    onSurfaceVariant = GreenGrey30,
    outline = GreenGrey50
)

@Composable
fun AutoPieTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window

            WindowCompat.setDecorFitsSystemWindows(window, false)

            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()


            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}