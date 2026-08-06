package xyz.younglion.flexilist.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentIndex: Int = 0,
    dynamicColor: Boolean = false, // Set to false to force our custom palette
    content: @Composable () -> Unit
) {
    val selectedAccent = ThemeAccentOptions.getOrElse(accentIndex) { ThemeAccentOptions[0] }

    val darkColorScheme = darkColorScheme(
        primary = selectedAccent.primary,
        onPrimary = selectedAccent.onPrimary,
        primaryContainer = selectedAccent.primaryContainer,
        onPrimaryContainer = selectedAccent.onPrimaryContainer,
        secondary = selectedAccent.secondary,
        onSecondary = ElegantOnSecondary,
        tertiary = selectedAccent.tertiary,
        background = ElegantBackground,
        surface = ElegantSurface,
        onBackground = TextPrimaryDark,
        onSurface = TextPrimaryDark,
        surfaceVariant = ElegantSurfaceVariant,
        onSurfaceVariant = TextSecondaryDark,
        outline = ElegantOutline
    )

    val lightColorScheme = lightColorScheme(
        primary = selectedAccent.primary,
        onPrimary = selectedAccent.onPrimary,
        primaryContainer = selectedAccent.primaryContainer,
        onPrimaryContainer = selectedAccent.onPrimaryContainer,
        secondary = selectedAccent.secondary,
        tertiary = selectedAccent.tertiary,
        background = LightBackground,
        surface = LightSurface,
        onBackground = TextPrimaryLight,
        onSurface = TextPrimaryLight,
        surfaceVariant = LightSurfaceElevated,
        onSurfaceVariant = TextSecondaryLight,
        outline = TextSecondaryLight
    )

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme
        else -> lightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

