package com.privatesms.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private fun getColorScheme(themeIndex: Int, darkTheme: Boolean) = if (darkTheme) {
    val (primary, secondary, tertiary) = when (themeIndex) {
        1 -> Triple(GreenPrimary, GreenSecondary, GreenTertiary)
        2 -> Triple(PurplePrimary, PurpleSecondary, PurpleTertiary)
        3 -> Triple(OrangePrimary, OrangeSecondary, OrangeTertiary)
        4 -> Triple(RedPrimary, RedSecondary, RedTertiary)
        5 -> Triple(TealPrimary, TealSecondary, TealTertiary)
        else -> Triple(BluePrimary, BlueSecondary, BlueTertiary)
    }
    darkColorScheme(
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        background = DarkBg,
        surface = DarkSurf,
        onBackground = DarkOnBg,
        onSurface = DarkOnSurf
    )
} else {
    val (primary, secondary, tertiary) = when (themeIndex) {
        1 -> Triple(GreenPrimary, GreenSecondary, GreenTertiary)
        2 -> Triple(PurplePrimary, PurpleSecondary, PurpleTertiary)
        3 -> Triple(OrangePrimary, OrangeSecondary, OrangeTertiary)
        4 -> Triple(RedPrimary, RedSecondary, RedTertiary)
        5 -> Triple(TealPrimary, TealSecondary, TealTertiary)
        else -> Triple(BluePrimary, BlueSecondary, BlueTertiary)
    }
    lightColorScheme(
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        background = LightBg,
        surface = LightSurf,
        onBackground = LightOnBg,
        onSurface = LightOnSurf
    )
}

@Composable
fun PrivateSmsTheme(
    themeIndex: Int = 0,
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontSize: String = "medium",
    content: @Composable () -> Unit
) {
    val colorScheme = getColorScheme(themeIndex, darkTheme)
    
    val multiplier = when (fontSize) {
        "small" -> 0.85f
        "large" -> 1.15f
        else -> 1.0f
    }
    
    val scaledTypography = androidx.compose.material3.Typography(
        bodyLarge = Typography.bodyLarge.copy(fontSize = Typography.bodyLarge.fontSize * multiplier),
        titleLarge = Typography.titleLarge.copy(fontSize = Typography.titleLarge.fontSize * multiplier),
        labelSmall = Typography.labelSmall.copy(fontSize = Typography.labelSmall.fontSize * multiplier)
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = scaledTypography,
        content = content
    )
}
