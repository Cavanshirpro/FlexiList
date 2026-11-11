package xyz.younglion.flexilist.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

internal fun contrastRatio(a: Color, b: Color): Float {
    val lighter = maxOf(a.luminance(), b.luminance())
    val darker = minOf(a.luminance(), b.luminance())
    return (lighter + .05f) / (darker + .05f)
}

/** Retain each user's accent hue while supplying readable text in light mode. */
internal fun lightAccent(accent: Color): Color = readableAccent(accent, LightSurfaceElevated, Color.Black)
internal fun darkAccent(accent: Color): Color = readableAccent(accent, Color(0xFF403E45), Color.White)
private fun readableAccent(accent: Color, surface: Color, toward: Color): Color {
    if (contrastRatio(accent, surface) >= 4.5f) return accent
    var low = 0f
    var high = 1f
    repeat(24) {
        val mid = (low + high) / 2
        if (contrastRatio(lerp(accent, toward, mid), surface) >= 4.5f) high = mid else low = mid
    }
    return lerp(accent, toward, high)
}
private fun onColor(color: Color) = if (contrastRatio(color, Color.White) > contrastRatio(color, Color.Black)) Color.White else Color.Black

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentIndex: Int = 0,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val accent = ThemeAccentOptions.getOrElse(accentIndex) { ThemeAccentOptions[0] }
    val lightPrimary = if (accentIndex == 0) Color(0xFF6750A4) else lightAccent(accent.primary)
    val lightContainer = lerp(lightPrimary, Color.White, .9f)
    val darkPrimary = darkAccent(accent.primary)
    val darkSecondary = darkAccent(accent.secondary)
    val darkTertiary = darkAccent(accent.tertiary)
    val dark = darkColorScheme(
        primary = darkPrimary, onPrimary = onColor(darkPrimary),
        primaryContainer = accent.primaryContainer, onPrimaryContainer = onColor(accent.primaryContainer),
        secondary = darkSecondary, onSecondary = onColor(darkSecondary),
        tertiary = darkTertiary, onTertiary = onColor(darkTertiary),
        background = ElegantBackground, onBackground = TextPrimaryDark,
        surface = ElegantSurface, onSurface = TextPrimaryDark,
        surfaceVariant = ElegantSurfaceVariant, onSurfaceVariant = Color(0xFFCAC4D0),
        outline = Color(0xFF938F99), outlineVariant = ElegantOutline,
        surfaceContainerLowest = ElegantBackground, surfaceContainerLow = ElegantSurfaceDarker,
        surfaceContainer = ElegantSurface, surfaceContainerHigh = Color(0xFF36343B),
        surfaceContainerHighest = Color(0xFF403E45), surfaceTint = darkPrimary
    )
    val light = lightColorScheme(
        primary = lightPrimary, onPrimary = Color.White,
        primaryContainer = lightContainer, onPrimaryContainer = Color(0xFF211F26),
        secondary = lightAccent(accent.secondary), onSecondary = Color.White,
        secondaryContainer = lightContainer, onSecondaryContainer = Color(0xFF211F26),
        tertiary = lightAccent(accent.tertiary), onTertiary = Color.White,
        background = LightBackground, onBackground = TextPrimaryLight,
        surface = LightSurface, onSurface = TextPrimaryLight,
        surfaceVariant = LightSurfaceElevated, onSurfaceVariant = TextSecondaryLight,
        outline = Color(0xFF79747E), outlineVariant = Color(0xFFCAC4D0),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F5FA),
        surfaceContainer = Color(0xFFF0EDF4), surfaceContainerHigh = Color(0xFFEAE7EE),
        surfaceContainerHighest = Color(0xFFE4E1E8), surfaceTint = lightPrimary
    )
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) dark else light
    MaterialTheme(colorScheme = colors, typography = Typography, shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)), content = content)
}
