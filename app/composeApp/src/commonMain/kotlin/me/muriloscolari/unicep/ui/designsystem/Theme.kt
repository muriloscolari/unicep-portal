package me.muriloscolari.unicep.ui.designsystem

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import me.muriloscolari.unicep.platform.DesignStyle
import me.muriloscolari.unicep.platform.DesignStylePreference
import me.muriloscolari.unicep.platform.PlatformBridge
import me.muriloscolari.unicep.resources.Res
import me.muriloscolari.unicep.resources.inter_bold
import me.muriloscolari.unicep.resources.inter_display_bold
import me.muriloscolari.unicep.resources.inter_display_semibold
import me.muriloscolari.unicep.resources.inter_medium
import me.muriloscolari.unicep.resources.inter_regular
import me.muriloscolari.unicep.resources.inter_semibold
import org.jetbrains.compose.resources.Font

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
    val isDark: Boolean,
    // iOS System & Liquid Glass tokens
    val tertiaryGroupedBackground: Color = surfaceSecondary,
    val tertiaryLabel: Color = textSecondary.copy(alpha = 0.6f),
    val quaternaryLabel: Color = textSecondary.copy(alpha = 0.35f),
    val opaqueSeparator: Color = divider,
    val systemFill: Color = surfaceSecondary,
    val secondaryFill: Color = surfaceSecondary,
    val tertiaryFill: Color = surfaceSecondary,
    val yellow: Color = orange,
    val teal: Color = accent,
    val purple: Color = indigo,
    val pink: Color = red,
    val gray: Color = textSecondary,
    val glassTint: Color = surfaceChrome,
    val glassTintFallback: Color = surfaceChrome,
    val glassStrokeTop: Color = Color.White.copy(alpha = 0.3f),
    val glassStrokeBottom: Color = Color.White.copy(alpha = 0.08f),
    val glassShadow: Color = Color.Black.copy(alpha = 0.16f),
    val glassLens: Color = accentSoft,
    val scrim: Color = Color.Black.copy(alpha = 0.45f)
)

@Immutable
data class AppleType(
    val largeTitle: TextStyle,
    val title1: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val subheadline: TextStyle,
    val footnote: TextStyle,
    val caption1: TextStyle,
    val caption2: TextStyle,
    val sectionHeader: TextStyle
)

object AppleMotion {
    val standard: SpringSpec<Float> = spring(dampingRatio = 1.0f, stiffness = 380f)
    val snappy: SpringSpec<Float> = spring(dampingRatio = 1.0f, stiffness = 520f)
    val sheet: SpringSpec<Float> = spring(dampingRatio = 1.0f, stiffness = 300f)
    val press: SpringSpec<Float> = spring(dampingRatio = 1.0f, stiffness = 900f)

    val dpStandard: SpringSpec<Dp> = spring(dampingRatio = 1.0f, stiffness = 380f)
    val dpSnappy: SpringSpec<Dp> = spring(dampingRatio = 1.0f, stiffness = 520f)
    val dpSheet: SpringSpec<Dp> = spring(dampingRatio = 1.0f, stiffness = 300f)

    const val PRESS_SCALE = 0.97f
}

private val AppleLightColors = PortalColors(
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    surfaceSecondary = Color(0xFFF2F2F7),
    surfaceChrome = Color(0xEEFFFFFF),
    accent = Color(0xFF007AFF),
    accentSoft = Color(0x1F007AFF),
    onAccent = Color.White,
    green = Color(0xFF34C759),
    orange = Color(0xFFFF9500),
    red = Color(0xFFFF3B30),
    indigo = Color(0xFF5856D6),
    textPrimary = Color(0xFF000000),
    textSecondary = Color(0x993C3C43),
    divider = Color(0x4A3C3C43),
    isDark = false,
    tertiaryGroupedBackground = Color(0xFFF2F2F7),
    tertiaryLabel = Color(0x4D3C3C43),
    quaternaryLabel = Color(0x2E3C3C43),
    opaqueSeparator = Color(0xFFC6C6C8),
    systemFill = Color(0x33787880),
    secondaryFill = Color(0x29787880),
    tertiaryFill = Color(0x1F767680),
    yellow = Color(0xFFFFCC00),
    teal = Color(0xFF30B0C7),
    purple = Color(0xFFAF52DE),
    pink = Color(0xFFFF2D55),
    gray = Color(0xFF8E8E93),
    glassTint = Color(0x8CFFFFFF),
    glassTintFallback = Color(0xF0F9F9FB),
    glassStrokeTop = Color(0xB3FFFFFF),
    glassStrokeBottom = Color(0x26FFFFFF),
    glassShadow = Color(0x1F000000),
    glassLens = Color(0x12000000),
    scrim = Color(0x33000000)
)

private val AppleDarkColors = PortalColors(
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceSecondary = Color(0xFF2C2C2E),
    surfaceChrome = Color(0xEB1C1C1E),
    accent = Color(0xFF0A84FF),
    accentSoft = Color(0x290A84FF),
    onAccent = Color.White,
    green = Color(0xFF30D158),
    orange = Color(0xFFFF9F0A),
    red = Color(0xFFFF453A),
    indigo = Color(0xFF5E5CE6),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0x99EBEBF5),
    divider = Color(0x99545458),
    isDark = true,
    tertiaryGroupedBackground = Color(0xFF2C2C2E),
    tertiaryLabel = Color(0x4DEBEBF5),
    quaternaryLabel = Color(0x29EBEBF5),
    opaqueSeparator = Color(0xFF38383A),
    systemFill = Color(0x5C787880),
    secondaryFill = Color(0x52787880),
    tertiaryFill = Color(0x3D767680),
    yellow = Color(0xFFFFD60A),
    teal = Color(0xFF40C8E0),
    purple = Color(0xFFBF5AF2),
    pink = Color(0xFFFF375F),
    gray = Color(0xFF8E8E93),
    glassTint = Color(0x801C1C1E),
    glassTintFallback = Color(0xF01C1C1E),
    glassStrokeTop = Color(0x47FFFFFF),
    glassStrokeBottom = Color(0x0FFFFFFF),
    glassShadow = Color(0x73000000),
    glassLens = Color(0x22FFFFFF),
    scrim = Color(0x73000000)
)

private val MaterialLightScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF0F295E),
    secondary = Color(0xFF475569),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF1E293B),
    tertiary = Color(0xFF4F46E5),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E7FF),
    onTertiaryContainer = Color(0xFF1E1B4B),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceContainerHighest = Color(0xFFCBD5E1),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val MaterialDarkScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF082047),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF243042),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFF818CF8),
    onTertiary = Color(0xFF1E1B4B),
    tertiaryContainer = Color(0xFF312E81),
    onTertiaryContainer = Color(0xFFE0E7FF),
    background = Color(0xFF0F141C),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF18202C),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF222C3C),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = Color(0xFF0B0F15),
    surfaceContainerLow = Color(0xFF131A24),
    surfaceContainer = Color(0xFF18202C),
    surfaceContainerHigh = Color(0xFF222C3C),
    surfaceContainerHighest = Color(0xFF2D3A4E),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF243042),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

private fun buildAppleType(
    textFamily: FontFamily,
    displayFamily: FontFamily
): AppleType = AppleType(
    largeTitle = TextStyle(
        fontFamily = displayFamily,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.8).sp
    ),
    title1 = TextStyle(
        fontFamily = displayFamily,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.6).sp
    ),
    title2 = TextStyle(
        fontFamily = displayFamily,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp
    ),
    title3 = TextStyle(
        fontFamily = displayFamily,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp
    ),
    headline = TextStyle(
        fontFamily = textFamily,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp
    ),
    body = TextStyle(
        fontFamily = textFamily,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.3).sp
    ),
    callout = TextStyle(
        fontFamily = textFamily,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.2).sp
    ),
    subheadline = TextStyle(
        fontFamily = textFamily,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.15).sp
    ),
    footnote = TextStyle(
        fontFamily = textFamily,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.05).sp
    ),
    caption1 = TextStyle(
        fontFamily = textFamily,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    ),
    caption2 = TextStyle(
        fontFamily = textFamily,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.05.sp
    ),
    sectionHeader = TextStyle(
        fontFamily = textFamily,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.05).sp
    )
)

private val DefaultAppleType = buildAppleType(FontFamily.Default, FontFamily.Default)

val LocalPortalColors = staticCompositionLocalOf { AppleLightColors }
val LocalDesignStyle = staticCompositionLocalOf { DesignStyle.APPLE }
val LocalAppleTypography = staticCompositionLocalOf { DefaultAppleType }

private fun buildMaterialTypography(
    textFamily: FontFamily,
    displayFamily: FontFamily
): Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = displayFamily,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = displayFamily,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = displayFamily,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = displayFamily,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = displayFamily,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.15).sp
    ),
    titleMedium = TextStyle(
        fontFamily = textFamily,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = textFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.05.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = textFamily,
        fontSize = 15.5.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = textFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.05.sp
    ),
    bodySmall = TextStyle(
        fontFamily = textFamily,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.1.sp
    ),
    labelLarge = TextStyle(
        fontFamily = textFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.05.sp
    ),
    labelMedium = TextStyle(
        fontFamily = textFamily,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = textFamily,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.25.sp
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

    val interRegular = Font(Res.font.inter_regular, FontWeight.Normal)
    val interMedium = Font(Res.font.inter_medium, FontWeight.Medium)
    val interSemiBold = Font(Res.font.inter_semibold, FontWeight.SemiBold)
    val interBold = Font(Res.font.inter_bold, FontWeight.Bold)
    val interDisplaySemiBold = Font(Res.font.inter_display_semibold, FontWeight.SemiBold)
    val interDisplayBold = Font(Res.font.inter_display_bold, FontWeight.Bold)

    val interTextFamily = remember(interRegular, interMedium, interSemiBold, interBold) {
        FontFamily(interRegular, interMedium, interSemiBold, interBold)
    }
    val interDisplayFamily = remember(interDisplaySemiBold, interDisplayBold, interRegular, interMedium) {
        FontFamily(interRegular, interMedium, interDisplaySemiBold, interDisplayBold)
    }
    val appleType = remember(interTextFamily, interDisplayFamily) {
        buildAppleType(interTextFamily, interDisplayFamily)
    }
    val appleM3Typography = remember(appleType) {
        Typography(
            headlineLarge = appleType.largeTitle,
            headlineMedium = appleType.title1,
            headlineSmall = appleType.title2,
            titleLarge = appleType.title3,
            titleMedium = appleType.headline,
            bodyLarge = appleType.body,
            bodyMedium = appleType.subheadline,
            bodySmall = appleType.footnote,
            labelLarge = appleType.callout,
            labelMedium = appleType.caption1,
            labelSmall = appleType.caption2
        )
    }

    val materialTypography = remember(interTextFamily, interDisplayFamily) {
        buildMaterialTypography(interTextFamily, interDisplayFamily)
    }

    val portalColors = if (effectiveStyle == DesignStyle.APPLE) {
        appleColors
    } else {
        appleColors.copy(
            background = m3Scheme.background,
            surface = m3Scheme.surface,
            surfaceSecondary = m3Scheme.surfaceVariant,
            surfaceChrome = m3Scheme.surfaceContainerLow,
            accent = m3Scheme.primary,
            accentSoft = m3Scheme.primaryContainer,
            onAccent = m3Scheme.onPrimary,
            green = if (darkTheme) Color(0xFF4ADE80) else Color(0xFF16A34A),
            orange = if (darkTheme) Color(0xFFFBBF24) else Color(0xFFD97706),
            red = m3Scheme.error,
            indigo = m3Scheme.tertiary,
            textPrimary = m3Scheme.onSurface,
            textSecondary = m3Scheme.onSurfaceVariant,
            divider = m3Scheme.outlineVariant,
            isDark = darkTheme,
            tertiaryGroupedBackground = m3Scheme.surfaceContainer,
            tertiaryLabel = m3Scheme.onSurfaceVariant.copy(alpha = 0.72f),
            quaternaryLabel = m3Scheme.onSurfaceVariant.copy(alpha = 0.42f),
            opaqueSeparator = m3Scheme.outlineVariant,
            systemFill = m3Scheme.surfaceContainer,
            secondaryFill = m3Scheme.surfaceContainerHigh,
            tertiaryFill = m3Scheme.surfaceContainerLow,
            yellow = if (darkTheme) Color(0xFFFBBF24) else Color(0xFFD97706),
            teal = m3Scheme.primary,
            purple = m3Scheme.tertiary,
            pink = m3Scheme.error,
            gray = m3Scheme.onSurfaceVariant
        )
    }

    CompositionLocalProvider(
        LocalDesignStyle provides effectiveStyle,
        LocalPortalColors provides portalColors,
        LocalAppleTypography provides appleType
    ) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography = if (effectiveStyle == DesignStyle.APPLE) appleM3Typography else materialTypography,
            content = content
        )
    }
}
