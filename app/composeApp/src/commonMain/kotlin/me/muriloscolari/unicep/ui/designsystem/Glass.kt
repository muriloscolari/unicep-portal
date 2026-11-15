package me.muriloscolari.unicep.ui.designsystem

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassMemberTransition
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import me.muriloscolari.unicep.platform.PlatformBridge

val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }
val LocalGlassEnabled = staticCompositionLocalOf { false }
val LocalAppleContentInsets = staticCompositionLocalOf { PaddingValues(0.dp) }

/**
 * Superfície Liquid Glass do modo Apple.
 * Quando [LocalGlassEnabled] é true e há um [HazeState] ativo, renderiza refração,
 * especular e blur reais via `hazeGlass`. Caso contrário, aplica fallback translúcido sólido.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = CircleShape,
    tint: Color = LocalPortalColors.current.glassTint,
    shadowElevation: Dp = 14.dp,
    clear: Boolean = false,
    useHaze: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    groupKey: Any? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val c = LocalPortalColors.current
    val haze = LocalHazeState.current
    val enabled = useHaze && LocalGlassEnabled.current && haze != null

    val glassStyle = remember(shape, tint, c.background, c.isDark, clear) {
        val base = if (clear) GlassStyle.clear else GlassStyle.regular
        base.then {
            shape(shape)
            tint(tint)
            backgroundColor(c.background.copy(alpha = if (c.isDark) 0.16f else 0.18f))
            specularIntensity(if (c.isDark) 0.42f else 0.52f)
            ambientResponse(if (c.isDark) 0.14f else 0.22f)
            edgeSoftness(1.2.dp)
            pressed {
                lightingIntensity(0f)
                whitePointDelta(if (c.isDark) 0.08f else -0.04f)
            }
            hovered {
                lightingIntensity(0f)
            }
        }
    }

    val shadowMod = if (shadowElevation > 0.dp) {
        Modifier.shadow(
            elevation = shadowElevation,
            shape = shape,
            clip = false,
            ambientColor = c.glassShadow,
            spotColor = c.glassShadow
        )
    } else Modifier

    val materialMod = if (enabled) {
        Modifier.hazeGlass(
            input = HazeInput.Sources(haze!!),
            style = glassStyle,
            interactionSource = interactionSource,
            memberTransition = GlassMemberTransition.MatchedGeometry,
            groupKey = groupKey
        )
    } else {
        Modifier
            .clip(shape)
            .background(c.glassTintFallback)
            .background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (c.isDark) 0.06f else 0.20f),
                    0.5f to Color.Transparent
                )
            )
            .border(
                width = 0.75.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        c.glassStrokeTop,
                        c.glassStrokeBottom,
                        c.glassStrokeTop.copy(alpha = c.glassStrokeTop.alpha * 0.45f)
                    ),
                    start = Offset.Zero,
                    end = Offset.Infinite
                ),
                shape = shape
            )
    }

    Box(
        modifier = modifier
            .then(shadowMod)
            .then(materialMod),
        content = content
    )
}

/**
 * Fundo de barra superior com scroll edge effect progressivo (fade + blur suave no topo).
 */
@Composable
fun GlassEdgeBarBackdrop(
    collapseProgress: Float,
    modifier: Modifier = Modifier
) {
    val c = LocalPortalColors.current
    val haze = LocalHazeState.current
    val enabled = LocalGlassEnabled.current && haze != null
    val p = collapseProgress.coerceIn(0f, 1f)

    if (p <= 0.01f) return

    val blurStyle = remember(c.background, c.isDark, p) {
        HazeBlurStyle {
            blurEnabled(true)
            blurRadius(20.dp)
            backgroundColor(c.background)
            colorEffects(
                listOf(
                    HazeColorEffect.tint(
                        c.background.copy(alpha = (if (c.isDark) 0.74f else 0.80f) * p)
                    )
                )
            )
            progressive(
                HazeProgressive.verticalGradient(
                    easing = EaseOut,
                    startIntensity = 1f,
                    endIntensity = 0.08f
                )
            )
            alpha(p)
        }
    }

    Box(
        modifier = modifier.then(
            if (enabled) {
                Modifier.hazeBlur(input = HazeInput.Sources(haze!!), style = blurStyle)
            } else {
                Modifier.background(
                    Brush.verticalGradient(
                        0f to c.glassTintFallback.copy(alpha = 0.96f * p),
                        0.82f to c.glassTintFallback.copy(alpha = 0.78f * p),
                        1f to Color.Transparent
                    )
                )
            }
        )
    )
}

/**
 * Botão circular ou em cápsula com material Liquid Glass (para nav bar e ações flutuantes).
 * Se [insideGlassBar] for true, evita blur aninhado usando lente/tint leve.
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    insideGlassBar: Boolean = false,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val c = LocalPortalColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val reduceMotion = remember { PlatformBridge.isReduceMotionEnabled() }

    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.94f else 1f,
        animationSpec = AppleMotion.press
    )

    val clickMod = Modifier
        .pointerHoverIcon(PointerIcon.Hand)
        .hoverable(interactionSource)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClickLabel = contentDescription,
            onClick = onClick
        )

    if (insideGlassBar) {
        Box(
            modifier = modifier
                .size(size)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(
                    when {
                        pressed -> c.systemFill
                        hovered -> c.systemFill.copy(alpha = 0.5f)
                        else -> Color.Transparent
                    }
                )
                .then(clickMod),
            contentAlignment = Alignment.Center,
            content = content
        )
    } else {
        GlassSurface(
            modifier = modifier
                .size(size)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .then(clickMod),
            shape = CircleShape,
            shadowElevation = 10.dp,
            interactionSource = interactionSource
        ) {
            Box(
                modifier = Modifier.matchParentSize(),
                contentAlignment = Alignment.Center,
                content = content
            )
        }
    }
}

/**
 * Botão em cápsula com vidro proeminente tingido na cor de destaque.
 */
@Composable
fun GlassProminentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    tint: Color = LocalPortalColors.current.accent
) {
    val c = LocalPortalColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = remember { PlatformBridge.isReduceMotionEnabled() }
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !loading && !reduceMotion) AppleMotion.PRESS_SCALE else 1f,
        animationSpec = AppleMotion.press
    )

    GlassSurface(
        modifier = modifier
            .height(50.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .alpha(if (enabled) 1f else 0.45f)
            .clip(CircleShape)
            .pointerHoverIcon(if (enabled && !loading) PointerIcon.Hand else PointerIcon.Default)
            .clickable(
                enabled = enabled && !loading,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        shape = CircleShape,
        tint = tint.copy(alpha = if (pressed) 0.78f else 0.88f),
        shadowElevation = 14.dp,
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(tint.copy(alpha = if (pressed) 0.55f else 0.70f)),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                AppleActivityIndicator(color = c.onAccent, size = 20.dp)
            } else {
                Text(
                    text = text,
                    color = c.onAccent,
                    style = LocalAppleTypography.current.headline,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

data class GlassMenuItem(
    val label: String,
    val icon: ImageVector? = null,
    val selected: Boolean = false,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Menu pull-down em estilo Liquid Glass iOS 26.
 */
@Composable
fun GlassMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<GlassMenuItem>,
    alignment: Alignment = Alignment.TopEnd
) {
    if (!expanded) return
    val c = LocalPortalColors.current
    val t = LocalAppleTypography.current

    Popup(
        alignment = alignment,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        GlassSurface(
            modifier = Modifier
                .padding(top = 6.dp)
                .width(248.dp),
            shape = RoundedCornerShape(20.dp),
            tint = c.surface.copy(alpha = if (c.isDark) 0.94f else 0.96f),
            useHaze = false,
            shadowElevation = 20.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                items.forEachIndexed { index, item ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(c.divider.copy(alpha = 0.55f))
                        )
                    }
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()
                    val hovered by interactionSource.collectIsHoveredAsState()
                    val rowColor = when {
                        item.destructive -> c.red
                        item.selected -> c.accent
                        else -> c.textPrimary
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 44.dp)
                            .background(
                                when {
                                    pressed -> c.systemFill
                                    hovered -> c.systemFill.copy(alpha = 0.45f)
                                    else -> Color.Transparent
                                }
                            )
                            .pointerHoverIcon(PointerIcon.Hand)
                            .hoverable(interactionSource)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                onDismiss()
                                item.onClick()
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (item.selected) {
                                Icon(
                                    imageVector = PortalIcons.Check,
                                    contentDescription = null,
                                    tint = c.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                            } else if (items.any { it.selected }) {
                                Spacer(Modifier.width(26.dp))
                            }
                            Text(
                                text = item.label,
                                color = rowColor,
                                style = t.body,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (item.icon != null) {
                            Spacer(Modifier.width(10.dp))
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = rowColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
