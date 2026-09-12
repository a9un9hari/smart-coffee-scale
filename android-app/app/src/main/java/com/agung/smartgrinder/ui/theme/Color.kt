package com.agung.smartgrinder.ui.theme

import androidx.compose.ui.graphics.Color

// La Mardjono Design System v1.0 - docs/UI/DARK_MODE_SUMMARY.md
// Coffee Brown is the constant brand accent across both modes.
val CoffeeBrown = Color(0xFFD4A574)
val RoyalBlue = Color(0xFF1E56DB)

// Light mode
val LightBackground = Color(0xFFFAFAFA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceElevated = Color(0xFFF5F5F5)
val LightBorder = Color(0xFFE0E0E0)
val LightTextPrimary = Color(0xFF1A1A1A)
val LightTextSecondary = Color(0xFF4D4D4D)
val LightTextMuted = Color(0xFF808080)
val LightSuccess = Color(0xFF2E8B57)
val LightWarning = Color(0xFFBA7517)
val LightDanger = Color(0xFFE24B4A)
val LightInfo = Color(0xFF4A90E2)

// Bottom nav sits one shade off the page background in both modes (mockup: #1A1A1A on a #121212 page).
val LightNavBackground = Color(0xFFF0F0F0)
val DarkNavBackground = Color(0xFF1A1A1A)

// Dark mode
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkSurfaceElevated = Color(0xFF2A2A2A)
val DarkBorder = Color(0xFF333333)
val DarkTextPrimary = Color(0xFFFFFFFF)
val DarkTextSecondary = Color(0xFFB3B3B3)
val DarkTextMuted = Color(0xFF808080)
val DarkSuccess = Color(0xFF4ECDC4)
val DarkWarning = Color(0xFFFFD700)
val DarkDanger = Color(0xFFFF6B6B)
val DarkInfo = Color(0xFF66D9EF)

// Shot/pour chart series colors - fixed regardless of theme so the legend
// stays consistent (teal/coral convention common in espresso shot-timer apps).
val WeightLineColor = Color(0xFF14B8A6)
val FlowLineColor = Color(0xFFEF4444)
