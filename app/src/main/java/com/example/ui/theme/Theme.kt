package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.data.CarrierType

private fun yemenLightScheme() = lightColorScheme(
    primary = YemenRedPrimary,
    onPrimary = Color.White,
    primaryContainer = YemenRedContainerLight,
    onPrimaryContainer = YemenRedDeep,
    secondary = YemenGoldAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF5C4004),
    background = LightBgYemen,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariantYemen,
    onSurfaceVariant = LightTextSecondary,
    outline = Color(0xFFE0C8CA),
    error = StatusError,
    onError = Color.White
)

private fun yemenDarkScheme() = darkColorScheme(
    primary = YemenRedVibrantDark,
    onPrimary = Color.White,
    primaryContainer = YemenRedContainerDark,
    onPrimaryContainer = YemenGoldSoft,
    secondary = YemenGoldAccent,
    onSecondary = DarkBgYemen,
    secondaryContainer = Color(0xFF3E2E08),
    onSecondaryContainer = YemenGoldSoft,
    background = DarkBgYemen,
    onBackground = DarkTextPrimary,
    surface = DarkSurfaceYemen,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariantYemen,
    onSurfaceVariant = DarkTextSecondary,
    outline = Color(0xFF4F343A),
    error = StatusError,
    onError = Color.White
)

private fun sabafonLightScheme() = lightColorScheme(
    primary = SabafonBluePrimary,
    onPrimary = Color.White,
    primaryContainer = SabafonBlueContainerLight,
    onPrimaryContainer = SabafonBlueDeep,
    secondary = SabafonCyanAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    background = LightBgSabafon,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariantSabafon,
    onSurfaceVariant = Color(0xFF4E6075),
    outline = Color(0xFFC5D8EC),
    error = StatusError,
    onError = Color.White
)

private fun sabafonDarkScheme() = darkColorScheme(
    primary = SabafonBlueVibrantDark,
    onPrimary = Color(0xFF001C3B),
    primaryContainer = SabafonBlueContainerDark,
    onPrimaryContainer = Color(0xFFD6E8FF),
    secondary = SabafonCyanAccent,
    onSecondary = DarkBgSabafon,
    secondaryContainer = Color(0xFF0C3554),
    onSecondaryContainer = Color(0xFFBAE6FD),
    background = DarkBgSabafon,
    onBackground = DarkTextPrimary,
    surface = DarkSurfaceSabafon,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariantSabafon,
    onSurfaceVariant = Color(0xFF9FB4CA),
    outline = Color(0xFF2B4159),
    error = StatusError,
    onError = Color.White
)

@Composable
private fun animateScheme(target: ColorScheme): ColorScheme {
    val animSpec = tween<Color>(durationMillis = 320)
    val primary by animateColorAsState(target.primary, animSpec, label = "primary")
    val onPrimary by animateColorAsState(target.onPrimary, animSpec, label = "onPrimary")
    val primaryContainer by animateColorAsState(target.primaryContainer, animSpec, label = "primaryContainer")
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, animSpec, label = "onPrimaryContainer")
    val secondary by animateColorAsState(target.secondary, animSpec, label = "secondary")
    val background by animateColorAsState(target.background, animSpec, label = "background")
    val surface by animateColorAsState(target.surface, animSpec, label = "surface")
    val surfaceVariant by animateColorAsState(target.surfaceVariant, animSpec, label = "surfaceVariant")
    val outline by animateColorAsState(target.outline, animSpec, label = "outline")

    return target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        outline = outline
    )
}

@Composable
fun YemenBalanceTheme(
    carrier: CarrierType = CarrierType.YEMEN_MOBILE,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val targetScheme = when (carrier) {
        CarrierType.YEMEN_MOBILE -> if (darkTheme) yemenDarkScheme() else yemenLightScheme()
        CarrierType.SABAFON -> if (darkTheme) sabafonDarkScheme() else sabafonLightScheme()
    }
    val animatedScheme = animateScheme(targetScheme)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = animatedScheme,
            typography = Typography,
            content = content
        )
    }
}
