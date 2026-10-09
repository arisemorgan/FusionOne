package com.fusionone.app.ui.theme

import androidx.compose.ui.graphics.Color

// --- Dark mode (default) — FusionOne brand spec ---
val RichBlack = Color(0xFF0A0A0A)       // Background
val CardDarkGray = Color(0xFF1A1A1A)    // Card surfaces
val PrimaryTeal = Color(0xFF00F5C8)     // CTA / live indicators / success
val AccentGold = Color(0xFFFFD700)      // Security score / alerts / premium
val TextOffWhite = Color(0xFFF5F5F5)    // Primary text
val TextGray = Color(0xFF8A8A8A)        // Secondary text / timestamps
val DangerRedOrange = Color(0xFFFF5757) // Threats / payload detected

// --- Light mode alt palette (from spec) ---
val LightBackground = Color(0xFFF8FAFC)
val LightCard = Color(0xFFFFFFFF)
val LightCardBorder = Color(0xFFE5E7EB)
val LightTealDarker = Color(0xFF00C2A8)
val LightGoldDarker = Color(0xFFD4AF37)

// Semantic aliases used throughout the app — keep these as the single source of truth
// so screens never hardcode a raw hex.
val SafeGreen = PrimaryTeal
val WarningAmber = AccentGold
val DangerRed = DangerRedOrange
val MetallicGold = AccentGold
val AccentTeal = PrimaryTeal
val PureBlack = RichBlack
val SurfaceBlack = CardDarkGray
val TextPrimary = TextOffWhite
val TextSecondary = TextGray
