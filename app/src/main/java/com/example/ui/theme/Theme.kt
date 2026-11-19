package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("Follow System")
}

val ZuvyraShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ZuvyraTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentOption: AccentColorOption = AccentColorOption.VIOLET,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = accentOption.primary,
            onPrimary = Color.White,
            primaryContainer = accentOption.primary.copy(alpha = 0.22f),
            onPrimaryContainer = ZuvyraVioletGlow,
            secondary = accentOption.secondary,
            onSecondary = StudioObsidian,
            secondaryContainer = accentOption.secondary.copy(alpha = 0.18f),
            onSecondaryContainer = accentOption.secondary,
            tertiary = ZuvyraAmber,
            onTertiary = StudioObsidian,
            background = StudioObsidian,
            onBackground = StudioTextPrimary,
            surface = StudioGraphite,
            onSurface = StudioTextPrimary,
            surfaceVariant = StudioCharcoal,
            onSurfaceVariant = StudioTextSecondary,
            surfaceContainerLowest = StudioObsidian,
            surfaceContainerLow = StudioGraphite,
            surfaceContainer = StudioCharcoal,
            surfaceContainerHigh = StudioSlate,
            surfaceContainerHighest = Color(0xFF242A42),
            outline = StudioBorder,
            outlineVariant = Color(0xFF1E2336),
            error = ZuvyraCoral,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = accentOption.primary,
            onPrimary = Color.White,
            primaryContainer = accentOption.primary.copy(alpha = 0.14f),
            onPrimaryContainer = accentOption.primary,
            secondary = accentOption.secondary,
            onSecondary = Color.White,
            secondaryContainer = accentOption.secondary.copy(alpha = 0.14f),
            onSecondaryContainer = accentOption.secondary,
            tertiary = ZuvyraAmber,
            onTertiary = Color.White,
            background = LightCanvas,
            onBackground = LightTextPrimary,
            surface = LightSurface,
            onSurface = LightTextPrimary,
            surfaceVariant = LightCard,
            onSurfaceVariant = LightTextSecondary,
            surfaceContainerLowest = LightSurface,
            surfaceContainerLow = LightCanvas,
            surfaceContainer = LightCard,
            surfaceContainerHigh = Color(0xFFE2E5F0),
            surfaceContainerHighest = Color(0xFFD7DBE8),
            outline = LightBorder,
            outlineVariant = Color(0xFFE2E5F0),
            error = ZuvyraCoral,
            onError = Color.White
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ZuvyraTypography,
        shapes = ZuvyraShapes,
        content = content
    )
}
