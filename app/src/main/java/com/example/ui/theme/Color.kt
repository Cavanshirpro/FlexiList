package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Brand Core Colors - Elegant Dark Theme Default
val ElegantPrimary = Color(0xFFD0BCFF)         // Light Lavender Primary
val ElegantOnPrimary = Color(0xFF381E72)       // Deep Violet
val ElegantPrimaryContainer = Color(0xFFE8DEF8)   // Soft Lavender Container
val ElegantOnPrimaryContainer = Color(0xFF1D192B) // Deep Violet Text

val ElegantSecondary = Color(0xFFCCC2DC)
val ElegantOnSecondary = Color(0xFF332D41)
val ElegantTertiary = Color(0xFFEFB8C8)

// Theme Accent Palette Options for User Customization
data class ThemeAccentOption(
    val id: Int,
    val name: String,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val tertiary: Color
)

val ThemeAccentOptions = listOf(
    ThemeAccentOption(
        id = 0,
        name = "Lavanta Moru",
        primary = Color(0xFFD0BCFF),
        onPrimary = Color(0xFF381E72),
        primaryContainer = Color(0xFF4F378B),
        onPrimaryContainer = Color(0xFFE8DEF8),
        secondary = Color(0xFFCCC2DC),
        tertiary = Color(0xFFEFB8C8)
    ),
    ThemeAccentOption(
        id = 1,
        name = "Zümrüt Yeşili",
        primary = Color(0xFF81C784),
        onPrimary = Color(0xFF1B5E20),
        primaryContainer = Color(0xFF2E7D32),
        onPrimaryContainer = Color(0xFFC8E6C9),
        secondary = Color(0xFFA5D6A7),
        tertiary = Color(0xFF80CBC4)
    ),
    ThemeAccentOption(
        id = 2,
        name = "Nehir Mavisi",
        primary = Color(0xFF64B5F6),
        onPrimary = Color(0xFF0D47A1),
        primaryContainer = Color(0xFF1565C0),
        onPrimaryContainer = Color(0xFFBBDEFB),
        secondary = Color(0xFF90CAF9),
        tertiary = Color(0xFF80DEEA)
    ),
    ThemeAccentOption(
        id = 3,
        name = "Amber Turuncu",
        primary = Color(0xFFFFB74D),
        onPrimary = Color(0xFFE65100),
        primaryContainer = Color(0xFFE65100),
        onPrimaryContainer = Color(0xFFFFE0B2),
        secondary = Color(0xFFFFCC80),
        tertiary = Color(0xFFFFAB91)
    ),
    ThemeAccentOption(
        id = 4,
        name = "Siber Kırmızı",
        primary = Color(0xFFE57373),
        onPrimary = Color(0xFFB71C1C),
        primaryContainer = Color(0xFFC62828),
        onPrimaryContainer = Color(0xFFFFCDD2),
        secondary = Color(0xFFEF9A9A),
        tertiary = Color(0xFFF48FB1)
    ),
    ThemeAccentOption(
        id = 5,
        name = "Siber Mor",
        primary = Color(0xFFBA68C8),
        onPrimary = Color(0xFF4A148C),
        primaryContainer = Color(0xFF7B1FA2),
        onPrimaryContainer = Color(0xFFE1BEE7),
        secondary = Color(0xFFCE93D8),
        tertiary = Color(0xFFB39DDB)
    ),
    ThemeAccentOption(
        id = 6,
        name = "Turkuaz Esintisi",
        primary = Color(0xFF4DD0E1),
        onPrimary = Color(0xFF006064),
        primaryContainer = Color(0xFF00838F),
        onPrimaryContainer = Color(0xFFB2EBF2),
        secondary = Color(0xFF80DEEA),
        tertiary = Color(0xFF80CBC4)
    ),
    ThemeAccentOption(
        id = 7,
        name = "Mercan Pembe",
        primary = Color(0xFFF06292),
        onPrimary = Color(0xFF880E4F),
        primaryContainer = Color(0xFFC2185B),
        onPrimaryContainer = Color(0xFFF8BBD0),
        secondary = Color(0xFFF48FB1),
        tertiary = Color(0xFFFFAB91)
    )
)

// Surface Palette (Elegant Dark)
val ElegantBackground = Color(0xFF1C1B1F)      // Rich Dark Charcoal Background
val ElegantSurface = Color(0xFF2B2930)         // Darker Purple-Gray Surface Container
val ElegantSurfaceVariant = Color(0xFF25232A)  // Sub Container Surface
val ElegantSurfaceDarker = Color(0xFF211F26)   // Navigation Bar Background
val TextPrimaryDark = Color(0xFFE6E1E5)        // Soft Off-White
val TextSecondaryDark = Color(0xFF938F99)      // Medium Gray
val ElegantOutline = Color(0xFF49454F)         // Muted Border Gray
val ElegantBorderDark = Color(0xFF312E37)      // Dark Border

// Light Theme Surface Palette (as secondary fallback, styled elegantly)
val LightBackground = Color(0xFFF4F6F9)        // Light Warm Gray
val LightSurface = Color(0xFFFFFFFF)           // White Cards
val LightSurfaceElevated = Color(0xFFECEFF1)
val TextPrimaryLight = Color(0xFF1C1B1F)
val TextSecondaryLight = Color(0xFF49454F)

// Customizable Notebook Cover Color Palette (Harmonized for Elegant Dark & Light)
val NotebookColors = listOf(
    Color(0xFFE57373), // Crimson Red
    Color(0xFFFFB74D), // Coral Orange
    Color(0xFFFFD54F), // Amber Yellow
    Color(0xFF81C784), // Emerald Green
    Color(0xFF4FC3F7), // Ocean Blue
    Color(0xFF7986CB), // Royal Indigo
    Color(0xFFBA68C8), // Electric Purple
    Color(0xFFF06292), // Hot Pink
    Color(0xFF4DD0E1), // Cyber Cyan
    Color(0xFFA1887F)  // Warm Bronze
)


