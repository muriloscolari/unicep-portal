package me.muriloscolari.unicep.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import me.muriloscolari.unicep.platform.DesignStyle
import me.muriloscolari.unicep.platform.DesignStylePreference
import me.muriloscolari.unicep.platform.PlatformBridge

@Immutable
data class PortalColors(
    val background: Color,
    val surface: Color,
    val surfaceSecondary: Color,
    val surfaceChrome: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val green: Color,
    val orange: Color,
    val red: Color,
    val indigo: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val isDark: Boolean
)

private val AppleLightColors = PortalColors(
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    surfaceSecondary = Color(0xFFE5E5EA),
    surfaceChrome = Color(0xEEFFFFFF),
    accent = Color(0xFF007AFF),
    accentSoft = Color(0x1C007AFF),
    onAccent = Color.White,
    green = Color(0xFF34C759),
    orange = Color(0xFFFF9500),
    red = Color(0xFFFF3B30),
    indigo = Color(0xFF5856D6),
    textPrimary = Color(0xFF111113),
    textSecondary = Color(0xFF6E6E73),
    divider = Color(0x243C3C43),
    isDark = false
)

private val AppleDarkColors = PortalColors(
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceSecondary = Color(0xFF2C2C2E),
    surfaceChrome = Color(0xEB18181A),
    accent = Color(0xFF0A84FF),
    accentSoft = Color(0x280A84FF),
    onAccent = Color.White,
    green = Color(0xFF30D158),
    orange = Color(0xFFFF9F0A),
    red = Color(0xFFFF453A),
    indigo = Color(0xFF5E5CE6),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF98989F),
    divider = Color(0x40545458),
    isDark = true
)

private val MaterialLightScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    background = Color(0xFFF7F2FA),
    surface = Color(0xFFFEF7FF),
    surfaceVariant = Color(0xFFE7E0EC),
    onBackground = Color(0xFF1D1B20),
    onSurface = Color(0xFF1D1B20),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    error = Color(0xFFB3261E)
)

private val MaterialDarkScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    background = Color(0xFF141218),
    surface = Color(0xFF211F26),
    surfaceVariant = Color(0xFF322F3A),
    onBackground = Color(0xFFE6E0E9),
    onSurface = Color(0xFFE6E0E9),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    error = Color(0xFFF2B8B5)
)

val LocalPortalColors = staticCompositionLocalOf { AppleLightColors }
val LocalDesignStyle = staticCompositionLocalOf { DesignStyle.APPLE }

private val AppleTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 31.sp,
        lineHeight = 37.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.6).sp
    ),
    headlineSmall = TextStyle(
        fontSize = 20.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.25).sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.15).sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Normal
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    )
)

private val MaterialTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp
    )
)

fun resolveDesignStyle(pref: DesignStylePreference): DesignStyle {
    return when (pref) {
        DesignStylePreference.APPLE -> DesignStyle.APPLE
        DesignStylePreference.MATERIAL -> DesignStyle.MATERIAL
        DesignStylePreference.AUTO -> {
            if (PlatformBridge.capabilities().supportsAppleEffects) DesignStyle.APPLE
            else DesignStyle.MATERIAL
        }
    }
}

@Composable
fun PortalTheme(
    stylePreference: DesignStylePreference = DesignStylePreference.AUTO,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val effectiveStyle = resolveDesignStyle(stylePreference)
    val appleColors = if (darkTheme) AppleDarkColors else AppleLightColors
    val m3Scheme = if (darkTheme) MaterialDarkScheme else MaterialLightScheme

    val portalColors = if (effectiveStyle == DesignStyle.APPLE) {
        appleColors
    } else {
        appleColors.copy(
            background = m3Scheme.background,
            surface = m3Scheme.surface,
            surfaceSecondary = m3Scheme.surfaceVariant,
            surfaceChrome = m3Scheme.surface,
            accent = m3Scheme.primary,
            accentSoft = m3Scheme.primaryContainer,
            onAccent = m3Scheme.onPrimary,
            green = if (darkTheme) Color(0xFF7AD695) else Color(0xFF1F7A3E),
            orange = if (darkTheme) Color(0xFFFFC16B) else Color(0xFFB85C00),
            red = m3Scheme.error,
            textPrimary = m3Scheme.onSurface,
            textSecondary = m3Scheme.onSurfaceVariant,
            divider = m3Scheme.outline.copy(alpha = 0.28f),
            isDark = darkTheme
        )
    }

    CompositionLocalProvider(
        LocalDesignStyle provides effectiveStyle,
        LocalPortalColors provides portalColors
    ) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography = if (effectiveStyle == DesignStyle.APPLE) AppleTypography else MaterialTypography,
            content = content
        )
    }
}
