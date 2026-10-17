package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val LuxuryDarkColorScheme = darkColorScheme(
    primary = LuminousCrimson,
    onPrimary = Color.White,
    primaryContainer = DeepMaroon,
    onPrimaryContainer = SoftChampagneGold,
    secondary = ImperialGold,
    onSecondary = ObsidianMaroonBg,
    secondaryContainer = DarkGoldContainer,
    onSecondaryContainer = SoftChampagneGold,
    tertiary = BrightGold,
    onTertiary = ObsidianMaroonBg,
    background = ObsidianMaroonBg,
    onBackground = LightTextPrimary,
    surface = LuxuryDarkCard,
    onSurface = LightTextPrimary,
    surfaceVariant = LuxuryDarkElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = Color(0xFF52353E),
    error = RubyError,
    onError = Color.White
)

private val RoyalLightColorScheme = lightColorScheme(
    primary = RoyalMaroon,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9DF),
    onPrimaryContainer = DeepMaroon,
    secondary = Color(0xFFB38728),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF5C4004),
    tertiary = BrightGold,
    onTertiary = Color.White,
    background = WarmIvoryBg,
    onBackground = DarkTextPrimary,
    surface = PureWhiteSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = SoftMaroonSurfaceVariant,
    onSurfaceVariant = MutedTextSecondary,
    outline = Color(0xFFD8C2C7),
    error = RubyError,
    onError = Color.White
)

@Composable
fun YemenBalanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) LuxuryDarkColorScheme else RoyalLightColorScheme

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
