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
    primary = Purple10,
    onPrimary = Purple90,
    primaryContainer = Purple90,
    onPrimaryContainer = Purple05,
    inversePrimary = Grey90,
    secondary = DarkGreen80,
    onSecondary = DarkGreen20,
    secondaryContainer = Purple70,
    onSecondaryContainer = Grey90,
    tertiary = Violet80,
    onTertiary = Grey85,
    tertiaryContainer = Grey30,
    onTertiaryContainer = Grey85,
    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = Purple90,
    onBackground = Purple05,
    surface = Purple90,
    onSurface = Grey90,
    inverseSurface = Grey90,
    inverseOnSurface = Purple90,
    surfaceVariant = Purple90,
    onSurfaceVariant = Grey90,
    outline = Purple10
)

private val LightColorScheme = lightColorScheme(
    primary = Purple10,
    onPrimary = Purple90,
    primaryContainer = Purple01,
    onPrimaryContainer = Purple90,
    inversePrimary = Purple90,
    secondary = Purple90,
    onSecondary = Color.White,
    secondaryContainer = Purple05,
    onSecondaryContainer = Purple90,
    tertiary = Violet40,
    onTertiary = Color.White,
    tertiaryContainer = Purple10,
    onTertiaryContainer = Purple70,
    error = Red40,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = Purple05,
    onBackground = Purple90,
    surface = Purple05,
    onSurface = Purple90,
    inverseSurface = Grey20,
    inverseOnSurface = Grey95,
    surfaceVariant = Purple05,
    onSurfaceVariant = Purple90,
    outline = Purple60
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