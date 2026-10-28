package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
    tertiary = WarningAmber,
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    CuboMestreTheme(darkTheme = darkTheme, content = content)
}
