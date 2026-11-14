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
    surfaceChrome = Color(0xEBFFFFFF),
    accent = Color(0xFF007AFF),
    accentSoft = Color(0x1A007AFF),
    green = Color(0xFF34C759),
    orange = Color(0xFFFF9500),
    red = Color(0xFFFF3B30),
    indigo = Color(0xFF5856D6),
    textPrimary = Color(0xFF111113),
    textSecondary = Color(0xFF6E6E73),
    divider = Color(0x223C3C43),
    isDark = false
)

private val AppleDarkColors = PortalColors(
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceSecondary = Color(0xFF2C2C2E),
    surfaceChrome = Color(0xE61C1C1E),
    accent = Color(0xFF0A84FF),
    accentSoft = Color(0x260A84FF),
    green = Color(0xFF30D158),
    orange = Color(0xFFFF9F0A),
    red = Color(0xFFFF453A),
    indigo = Color(0xFF5E5CE6),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF98989D),
    divider = Color(0x38545458),
    isDark = true
)

private val MaterialLightScheme = lightColorScheme(
    primary = Color(0xFF0061A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    background = Color(0xFFF8F9FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEAECEF),
    onBackground = Color(0xFF1A1C1E),
    onSurface = Color(0xFF1A1C1E),
    onSurfaceVariant = Color(0xFF43474E),
    error = Color(0xFFBA1A1A)
)

private val MaterialDarkScheme = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD1E4FF),
    background = Color(0xFF111318),
    surface = Color(0xFF1B1D22),
    surfaceVariant = Color(0xFF282B31),
    onBackground = Color(0xFFE2E2E6),
    onSurface = Color(0xFFE2E2E6),
    onSurfaceVariant = Color(0xFFC3C7CF),
    error = Color(0xFFFFB4AB)
)

val LocalPortalColors = staticCompositionLocalOf { AppleLightColors }
val LocalDesignStyle = staticCompositionLocalOf { DesignStyle.APPLE }

private val PortalTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontSize = 20.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp
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
            accentSoft = m3Scheme.primary.copy(alpha = 0.14f),
            textPrimary = m3Scheme.onSurface,
            textSecondary = m3Scheme.onSurfaceVariant
        )
    }

    CompositionLocalProvider(
        LocalDesignStyle provides effectiveStyle,
        LocalPortalColors provides portalColors
    ) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography = PortalTypography,
            content = content
        )
    }
}
