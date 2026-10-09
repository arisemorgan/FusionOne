package com.fusionone.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = PrimaryTeal,
    onPrimary = RichBlack,
    secondary = AccentGold,
    onSecondary = RichBlack,
    background = RichBlack,
    onBackground = TextOffWhite,
    surface = CardDarkGray,
    onSurface = TextOffWhite,
    surfaceVariant = CardDarkGray,
    error = DangerRedOrange,
    onError = RichBlack
)

private val LightColors = lightColorScheme(
    primary = LightTealDarker,
    onPrimary = Color.White,
    secondary = LightGoldDarker,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF0A0A0A),
    surface = LightCard,
    onSurface = Color(0xFF0A0A0A),
    error = DangerRedOrange,
    onError = Color.White
)

@Composable
fun FusionOneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default: FusionOne's brand identity is a deliberate 3-color system
    // (teal / gold / near-black). Material You per-device dynamic color would override
    // that on Android 12+, so it stays opt-in rather than the default.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FusionOneTypography,
        content = content
    )
}
