package com.agung.smartgrinder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = RoyalBlue,
    onPrimary = Color.White,
    secondary = CoffeeBrown,
    onSecondary = LightTextPrimary,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = LightDanger,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = CoffeeBrown,
    onPrimary = Color(0xFF1A1A1A),
    secondary = CoffeeBrown,
    onSecondary = Color(0xFF1A1A1A),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = DarkDanger,
    onError = Color.White,
)

/** Status colors aren't part of Material3's ColorScheme, so they're carried separately. */
data class StatusColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val navBackground: Color,
)

val LightStatusColors = StatusColors(LightSuccess, LightWarning, LightDanger, LightInfo, LightNavBackground)
val DarkStatusColors = StatusColors(DarkSuccess, DarkWarning, DarkDanger, DarkInfo, DarkNavBackground)

private val LocalStatusColors = androidx.compose.runtime.staticCompositionLocalOf { DarkStatusColors }

val MaterialTheme.status: StatusColors
    @Composable get() = LocalStatusColors.current

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun SmartGrinderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors

    androidx.compose.runtime.CompositionLocalProvider(LocalStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            content = content
        )
    }
}
