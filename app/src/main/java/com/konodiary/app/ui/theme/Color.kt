package com.konodiary.app.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// "Neon Stage" palette — DESIGN.md §1. Dark is the brand identity; light is a
// soft lavender-tinted pastel. dynamicColor is disabled (see Theme.kt).
// ─────────────────────────────────────────────────────────────────────────────

// Dark (primary identity)
val DarkPrimary = Color(0xFFF472B6)
val DarkOnPrimary = Color(0xFF4A0D2E)
val DarkPrimaryContainer = Color(0xFF7A2853)
val DarkOnPrimaryContainer = Color(0xFFFFD9E9)

val DarkSecondary = Color(0xFFA78BFA)
val DarkOnSecondary = Color(0xFF2E1065)
val DarkSecondaryContainer = Color(0xFF4C3494)
val DarkOnSecondaryContainer = Color(0xFFE9DDFF)

val DarkTertiary = Color(0xFF67E8F9)
val DarkOnTertiary = Color(0xFF003A42)
val DarkTertiaryContainer = Color(0xFF0E5560)
val DarkOnTertiaryContainer = Color(0xFFC8F6FF)

val DarkBackground = Color(0xFF14101B)
val DarkOnBackground = Color(0xFFEBE4F2)
val DarkSurface = Color(0xFF14101B)
val DarkOnSurface = Color(0xFFEBE4F2)
val DarkSurfaceVariant = Color(0xFF241C31)
val DarkOnSurfaceVariant = Color(0xFFB3A8C4)

val DarkSurfaceContainerLowest = Color(0xFF0F0C15)
val DarkSurfaceContainerLow = Color(0xFF181220)
val DarkSurfaceContainer = Color(0xFF1B1526)
val DarkSurfaceContainerHigh = Color(0xFF221A30)
val DarkSurfaceContainerHighest = Color(0xFF2A2139)

val DarkOutline = Color(0xFF6E6480)
val DarkOutlineVariant = Color(0xFF3A3149)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

// Light
val LightPrimary = Color(0xFFC2337F)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFFFD9E9)
val LightOnPrimaryContainer = Color(0xFF3B0021)

val LightSecondary = Color(0xFF6D28D9)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE9DDFF)
val LightOnSecondaryContainer = Color(0xFF22005D)

val LightTertiary = Color(0xFF00838F)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFC8F6FF)
val LightOnTertiaryContainer = Color(0xFF00363D)

val LightBackground = Color(0xFFFFF7FB)
val LightOnBackground = Color(0xFF201A25)
val LightSurface = Color(0xFFFFF7FB)
val LightOnSurface = Color(0xFF201A25)
val LightSurfaceVariant = Color(0xFFEFE6F2)
val LightOnSurfaceVariant = Color(0xFF4E4459)

val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFFFF1F8)
val LightSurfaceContainer = Color(0xFFFBF1F7)
val LightSurfaceContainerHigh = Color(0xFFF5EBF3)
val LightSurfaceContainerHighest = Color(0xFFEFE4EF)

val LightOutline = Color(0xFF7F7490)
val LightOutlineVariant = Color(0xFFD3C6DB)

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

// ─────────────────────────────────────────────────────────────────────────────
// Semantic constants (DESIGN.md §1 "시맨틱 추가 색"). Public so screen agents can
// import them directly. Empty stars use the scheme's outlineVariant, not a const.
// ─────────────────────────────────────────────────────────────────────────────
val StarGold = Color(0xFFFFC24B)

val BestBadgeBgDark = Color(0xFF4A3A12)
val BestBadgeFgDark = Color(0xFFFFD873)
val BestBadgeBgLight = Color(0xFFFFF0C9)
val BestBadgeFgLight = Color(0xFF7A5B00)
