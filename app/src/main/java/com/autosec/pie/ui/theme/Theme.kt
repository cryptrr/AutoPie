package com.autopi.ui.theme

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.autopi.autopieapp.data.preferences.AppPreferences
import org.koin.java.KoinJavaComponent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFCBC2FF),
    onPrimary = Color(0xFF33275B),
    primaryContainer = Color(0xFF4A3E73),
    onPrimaryContainer = Color(0xFFE8DEFF),
    inversePrimary = Color(0xFF62558C),
    secondary = Color(0xFFCBC2DB),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4358),
    onSecondaryContainer = Color(0xFFE7DEF8),
    tertiary = Color(0xFFF0B8C6),
    onTertiary = Color(0xFF4A2530),
    tertiaryContainer = Color(0xFF633B46),
    onTertiaryContainer = Color(0xFFFFD9E2),
    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    inverseSurface = Color(0xFFE6E0E9),
    inverseOnSurface = Color(0xFF322F35),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceTint = Color(0xFFCBC2FF),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    surfaceDim = Color(0xFF141218),
    surfaceBright = Color(0xFF3B383E),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF62558C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8DEFF),
    onPrimaryContainer = Color(0xFF1E1144),
    inversePrimary = Color(0xFFCBC2FF),
    secondary = Color(0xFF625B70),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7DEF8),
    onSecondaryContainer = Color(0xFF1E192B),
    tertiary = Color(0xFF7E525E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E2),
    onTertiaryContainer = Color(0xFF31101B),
    error = Red40,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = Color(0xFFFEF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1B20),
    inverseSurface = Color(0xFF322F35),
    inverseOnSurface = Color(0xFFF5EFF7),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    surfaceTint = Color(0xFF62558C),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    surfaceDim = Color(0xFFDED8E1),
    surfaceBright = Color(0xFFFEF7FF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
)

enum class ThemeMode(val preferenceValue: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromPreference(value: String): ThemeMode =
            entries.firstOrNull { it.preferenceValue == value } ?: SYSTEM
    }
}

@Composable
fun rememberThemeMode(preferences: AppPreferences): ThemeMode {
    val value by remember(preferences) {
        preferences.getString(AppPreferences.CURRENT_THEME)
    }.collectAsState(initial = remember(preferences) {
        preferences.getStringSync(AppPreferences.CURRENT_THEME)
    })
    return ThemeMode.fromPreference(value)
}

@Composable
fun rememberDynamicColorsEnabled(preferences: AppPreferences): Boolean {
    val enabled by remember(preferences) {
        preferences.getBool(AppPreferences.DYNAMIC_COLOR_ENABLED, true)
    }.collectAsState(initial = remember(preferences) {
        preferences.getBoolSync(AppPreferences.DYNAMIC_COLOR_ENABLED, true)
    })
    return enabled
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val preferences = remember { KoinJavaComponent.get<AppPreferences>(AppPreferences::class.java) }
    AutoPieTheme(
        darkTheme = rememberThemeMode(preferences).isDark(isSystemInDarkTheme()),
        dynamicColor = rememberDynamicColorsEnabled(preferences),
        content = content,
    )
}

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