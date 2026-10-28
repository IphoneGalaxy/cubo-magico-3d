package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@Immutable
data class CuboMestreColors(
    val isDark: Boolean,
    val turquoiseAccent: Color,
    val onTurquoiseAccent: Color,
    val violetAccent: Color,
    val violetText: Color,
    val successText: Color,
    val successBadgeBg: Color,
    val warningText: Color,
    val miniCubeSurface: Color,
    val miniCubeDimmedCell: Color,
    val miniCubeStickerStroke: Color
)

val DarkCuboMestreColors = CuboMestreColors(
    isDark = true,
    turquoiseAccent = TurquoisePrimary,
    onTurquoiseAccent = OnTurquoise,
    violetAccent = VioletButtonDark,
    violetText = VioletLight,
    successText = SuccessEmerald,
    successBadgeBg = SuccessBadgeBackground,
    warningText = WarningAmber,
    miniCubeSurface = NavySurface,
    miniCubeDimmedCell = StickerDimmed,
    miniCubeStickerStroke = MiniCubeStickerStrokeDark
)

val LightCuboMestreColors = CuboMestreColors(
    isDark = false,
    turquoiseAccent = TurquoiseLightMode,
    onTurquoiseAccent = Color.White,
    violetAccent = VioletLightMode,
    violetText = VioletLightMode,
    successText = SuccessEmeraldLightMode,
    successBadgeBg = SuccessBadgeBackground,
    warningText = WarningAmberLightMode,
    miniCubeSurface = MiniCubeLightSurface,
    miniCubeDimmedCell = MiniCubeLightDimmed,
    miniCubeStickerStroke = MiniCubeStickerStrokeLight
)

val LocalCuboMestreColors = staticCompositionLocalOf { DarkCuboMestreColors }

object CuboMestreTheme {
    val colors: CuboMestreColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCuboMestreColors.current
}

/**
 * Calcula a luminância relativa WCAG 2.1 de uma cor sRGB.
 */
fun Color.wcagRelativeLuminance(): Float {
    fun channelLum(c: Float): Float {
        return if (c <= 0.03928f) {
            c / 12.92f
        } else {
            ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        }
    }
    val r = channelLum(red.coerceIn(0f, 1f))
    val g = channelLum(green.coerceIn(0f, 1f))
    val b = channelLum(blue.coerceIn(0f, 1f))
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

/**
 * Calcula a razão de contraste WCAG 2.1 efetiva entre [foreground] (com possível transparência)
 * renderizado sobre [background] opaco.
 */
fun calculateEffectiveWcagContrast(foreground: Color, background: Color): Float {
    val effectiveFg = if (foreground.alpha < 1f) {
        foreground.compositeOver(background)
    } else {
        foreground
    }
    val l1 = effectiveFg.wcagRelativeLuminance()
    val l2 = background.wcagRelativeLuminance()
    val lighter = max(l1, l2)
    val darker = min(l1, l2)
    return (lighter + 0.05f) / (darker + 0.05f)
}

private val DarkColorScheme = darkColorScheme(
    primary = TurquoisePrimary,
    onPrimary = OnTurquoise,
    primaryContainer = TurquoiseContainer,
    onPrimaryContainer = TurquoisePrimary,
    secondary = VioletAccent,
    onSecondary = Color.White,
    secondaryContainer = VioletContainer,
    onSecondaryContainer = VioletLight,
    tertiary = WarningAmber,
    background = NavyBackground,
    onBackground = TextPrimaryDark,
    surface = NavySurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = GraphiteCard,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerHigh = GraphiteElevated,
    outline = GraphiteBorder,
    error = ErrorCoral
)

private val LightColorScheme = lightColorScheme(
    primary = TurquoiseLightMode,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2F1),
    onPrimaryContainer = Color(0xFF004D40),
    secondary = VioletLightMode,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE7F6),
    onSecondaryContainer = Color(0xFF311B92),
    tertiary = WarningAmberLightMode,
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightCard,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerHigh = Color(0xFFE2E8F0),
    outline = LightBorder,
    error = ErrorCoral
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CuboMestreTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val customColors = if (darkTheme) DarkCuboMestreColors else LightCuboMestreColors

    CompositionLocalProvider(LocalCuboMestreColors provides customColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    CuboMestreTheme(darkTheme = darkTheme, content = content)
}
