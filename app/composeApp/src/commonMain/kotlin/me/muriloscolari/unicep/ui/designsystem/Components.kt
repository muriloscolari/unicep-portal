package me.muriloscolari.unicep.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.muriloscolari.unicep.platform.DesignStyle
import me.muriloscolari.unicep.platform.PlatformBridge
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

sealed interface CardStyle {
    data object Grouped : CardStyle
    data object Plain : CardStyle
    data class Tinted(val color: Color) : CardStyle
}

@Composable
fun PortalCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    cardStyle: CardStyle = CardStyle.Grouped,
    content: @Composable ColumnScope.() -> Unit
) {
    val style = LocalDesignStyle.current
    val colors = LocalPortalColors.current

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()

    if (style == DesignStyle.APPLE) {
        val reduceMotion = remember { PlatformBridge.isReduceMotionEnabled() }
        val scale by animateFloatAsState(
            targetValue = if (pressed && onClick != null && !reduceMotion) AppleMotion.PRESS_SCALE else 1f,
            animationSpec = AppleMotion.press
        )
        val shape = RoundedCornerShape(20.dp)
        val baseColor = when (cardStyle) {
            CardStyle.Grouped -> colors.surface
            CardStyle.Plain -> colors.tertiaryGroupedBackground
            is CardStyle.Tinted -> cardStyle.color.copy(alpha = if (colors.isDark) 0.16f else 0.12f)
        }
        val overlayAlpha by animateFloatAsState(
            targetValue = when {
                pressed && onClick != null -> 0.45f
                hovered && onClick != null -> 0.22f
                else -> 0f
            },
            animationSpec = tween(120)
        )

        val shadowMod = if (!colors.isDark && cardStyle == CardStyle.Grouped) {
            Modifier.shadow(
                elevation = 2.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.05f),
                spotColor = Color.Black.copy(alpha = 0.05f)
            )
        } else Modifier

        Box(
            modifier = modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .then(shadowMod)
                .clip(shape)
                .background(baseColor)
                .then(
                    if (onClick != null) {
                        Modifier
                            .pointerHoverIcon(PointerIcon.Hand)
                            .hoverable(interactionSource)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = onClick
                            )
                    } else Modifier
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.systemFill.copy(alpha = overlayAlpha.coerceIn(0f, 1f)))
                    .padding(contentPadding),
                content = content
            )
        }
    } else {
        val scale by animateFloatAsState(
            targetValue = 1f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
        )
        val shape = RoundedCornerShape(16.dp)
        val border = BorderStroke(0.8.dp, colors.divider.copy(alpha = 0.45f))
        Surface(
            modifier = modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(shape)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = androidx.compose.material3.ripple(),
                            onClick = onClick
                        )
                    } else Modifier
                ),
            shape = shape,
            color = colors.surface,
            tonalElevation = 3.dp,
            border = border
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun PortalSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val style = LocalDesignStyle.current
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current

    if (style == DesignStyle.APPLE) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(
                    start = if (prominent) 4.dp else 16.dp,
                    end = 4.dp,
                    top = 6.dp,
                    bottom = 6.dp
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = if (prominent) colors.textPrimary else colors.textSecondary,
                style = if (prominent) appleType.title3 else appleType.sectionHeader,
                fontWeight = if (prominent) FontWeight.Bold else FontWeight.Normal
            )
            trailing?.invoke()
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.1.sp
            )
            trailing?.invoke()
        }
    }
}

@Composable
fun PortalSectionFooter(
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    Text(
        text = text,
        color = colors.textSecondary,
        style = appleType.footnote,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
fun PortalSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalDesignStyle.current
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val haptic = LocalHapticFeedback.current

    if (options.isEmpty()) return
    val clampedIndex = selectedIndex.coerceIn(0, options.lastIndex)

    if (style == DesignStyle.APPLE) {
        val containerShape = RoundedCornerShape(10.dp)
        val thumbShape = RoundedCornerShape(8.dp)
        val thumbColor = if (colors.isDark) Color(0xFF636366) else Color.White

        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(containerShape)
                .background(colors.secondaryFill)
                .padding(2.dp)
        ) {
            val count = options.size
            val segmentWidth = (maxWidth / count).coerceAtLeast(0.dp)
            val thumbOffset by animateDpAsState(
                targetValue = segmentWidth * clampedIndex,
                animationSpec = AppleMotion.dpSnappy
            )

            // Divisórias verticais entre segmentos não selecionados
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                for (i in 0 until count - 1) {
                    Spacer(Modifier.weight(1f))
                    val showDiv = i != clampedIndex && (i + 1) != clampedIndex
                    Box(
                        modifier = Modifier
                            .width(0.5.dp)
                            .height(14.dp)
                            .align(Alignment.CenterVertically)
                            .background(if (showDiv) colors.divider else Color.Transparent)
                    )
                }
                Spacer(Modifier.weight(1f))
            }

            // Thumb deslizante único sem borda
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .shadow(
                        elevation = 4.dp,
                        shape = thumbShape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.14f),
                        spotColor = Color.Black.copy(alpha = 0.14f)
                    )
                    .clip(thumbShape)
                    .background(thumbColor)
            )

            // Labels clicáveis
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                options.forEachIndexed { index, label ->
                    val selected = index == clampedIndex
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()
                    val labelScale by animateFloatAsState(
                        targetValue = if (pressed) 0.95f else 1f,
                        animationSpec = AppleMotion.press
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .graphicsLayer { scaleX = labelScale; scaleY = labelScale }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .semantics {
                                role = Role.Tab
                                this.selected = selected
                            }
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                if (!selected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelect(index)
                                }
                            }
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = colors.textPrimary,
                            style = appleType.subheadline,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    } else {
        // Material Design 3 Outlined Segmented Chip Strip
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == clampedIndex
                val shape = RoundedCornerShape(10.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(if (selected) colors.accentSoft else Color.Transparent)
                        .border(
                            width = 1.dp,
                            color = if (selected) colors.accent else colors.divider,
                            shape = shape
                        )
                        .clickable { onSelect(index) }
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (selected) colors.accent else colors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun PortalBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val style = LocalDesignStyle.current
    val appleType = LocalAppleTypography.current
    val shape = if (style == DesignStyle.APPLE) CircleShape else RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = if (style == DesignStyle.APPLE) appleType.caption2 else TextStyle(fontSize = 11.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
fun PortalStatusDot(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    tintText: Boolean = false
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = text,
            color = if (tintText) color else colors.textSecondary,
            style = appleType.footnote,
            fontWeight = if (tintText) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
fun PortalPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val style = LocalDesignStyle.current
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current

    if (style == DesignStyle.APPLE) {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val reduceMotion = remember { PlatformBridge.isReduceMotionEnabled() }
        val scale by animateFloatAsState(
            targetValue = if (pressed && enabled && !loading && !reduceMotion) AppleMotion.PRESS_SCALE else 1f,
            animationSpec = AppleMotion.press
        )
        Box(
            modifier = modifier
                .height(50.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(
                    when {
                        !enabled -> colors.accent.copy(alpha = 0.4f)
                        pressed -> colors.accent.copy(alpha = 0.85f)
                        else -> colors.accent
                    }
                )
                .pointerHoverIcon(if (enabled && !loading) PointerIcon.Hand else PointerIcon.Default)
                .clickable(
                    enabled = enabled && !loading,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                )
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                AppleActivityIndicator(color = colors.onAccent, size = 20.dp)
            } else {
                Text(
                    text = text,
                    color = colors.onAccent.copy(alpha = if (enabled) 1f else 0.78f),
                    style = appleType.headline,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = modifier.height(48.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.onAccent,
                disabledContainerColor = colors.accent.copy(alpha = 0.4f),
                disabledContentColor = colors.onAccent.copy(alpha = 0.75f)
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = colors.onAccent
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun PortalSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    val style = LocalDesignStyle.current
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val fg = tint ?: colors.accent

    if (style == DesignStyle.APPLE) {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val reduceMotion = remember { PlatformBridge.isReduceMotionEnabled() }
        val scale by animateFloatAsState(
            targetValue = if (pressed && !reduceMotion) AppleMotion.PRESS_SCALE else 1f,
            animationSpec = AppleMotion.press
        )
        Box(
            modifier = modifier
                .height(44.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(fg.copy(alpha = if (pressed) 0.24f else 0.15f))
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                )
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = fg,
                style = appleType.subheadline,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(44.dp),
            shape = CircleShape,
            border = BorderStroke(1.dp, fg.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = fg)
        ) {
            Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun PortalTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalPortalColors.current.accent
) {
    val appleType = LocalAppleTypography.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Text(
        text = text,
        color = color.copy(alpha = if (pressed) 0.55f else 1f),
        style = appleType.footnote,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 6.dp, vertical = 6.dp)
    )
}

@Composable
fun PortalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val style = LocalDesignStyle.current
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current

    if (style == DesignStyle.APPLE) {
        val interactionSource = remember { MutableInteractionSource() }
        val focused by interactionSource.collectIsFocusedAsState()
        val shape = RoundedCornerShape(12.dp)

        Box(
            modifier = modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 46.dp)
                .clip(shape)
                .background(colors.tertiaryFill)
                .then(
                    if (focused) {
                        Modifier.border(2.dp, colors.accent.copy(alpha = 0.35f), shape)
                    } else Modifier
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = label,
                            color = colors.tertiaryLabel,
                            style = appleType.body,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = singleLine,
                        textStyle = appleType.body.copy(color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        visualTransformation = visualTransformation,
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        interactionSource = interactionSource
                    )
                }
                if (trailing != null) {
                    Spacer(Modifier.width(8.dp))
                    trailing()
                }
            }
        }
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = modifier.fillMaxWidth(),
            singleLine = singleLine,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.divider,
                focusedLabelColor = colors.accent,
                unfocusedLabelColor = colors.textSecondary,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            )
        )
    }
}

@Composable
fun PortalSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "Buscar",
    modifier: Modifier = Modifier
) {
    val style = LocalDesignStyle.current
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current

    if (style != DesignStyle.APPLE) {
        PortalTextField(
            value = value,
            onValueChange = onValueChange,
            label = placeholder,
            modifier = modifier
        )
        return
    }

    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(shape)
            .background(colors.tertiaryFill)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = PortalIcons.Search,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(7.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = colors.textSecondary,
                    style = appleType.subheadline,
                    maxLines = 1
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = appleType.subheadline.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent)
            )
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(colors.tertiaryLabel)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { onValueChange("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = PortalIcons.Close,
                    contentDescription = "Limpar busca",
                    tint = colors.surface,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

@Composable
fun PortalDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 0.5.dp,
        color = LocalPortalColors.current.divider
    )
}

@Composable
fun PortalListRow(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    leadingTint: Color? = null,
    leadingDotColor: Color? = null,
    trailingValue: String? = null,
    showChevron: Boolean = onClick != null && trailing == null,
    destructive: Boolean = false
) {
    val style = LocalDesignStyle.current
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current

    if (style == DesignStyle.APPLE) {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val hovered by interactionSource.collectIsHoveredAsState()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = if (subtitle.isNullOrBlank()) 44.dp else 54.dp)
                .background(
                    when {
                        pressed && onClick != null -> colors.systemFill
                        hovered && onClick != null -> colors.systemFill.copy(alpha = 0.45f)
                        else -> Color.Transparent
                    }
                )
                .then(
                    if (onClick != null) {
                        Modifier
                            .pointerHoverIcon(PointerIcon.Hand)
                            .hoverable(interactionSource)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = onClick
                            )
                    } else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (destructive) Arrangement.Center else Arrangement.SpaceBetween
        ) {
            if (destructive) {
                Text(
                    text = title,
                    color = colors.red,
                    style = appleType.body,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            } else {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (leadingDotColor != null) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(leadingDotColor)
                        )
                        Spacer(Modifier.width(12.dp))
                    } else if (leadingIcon != null) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(leadingTint ?: colors.accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = leadingIcon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = colors.textPrimary,
                            style = appleType.body,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                color = colors.textSecondary,
                                style = appleType.footnote,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (trailing != null || trailingValue != null || showChevron) {
                    Spacer(Modifier.width(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (trailingValue != null) {
                            Text(
                                text = trailingValue,
                                color = colors.textSecondary,
                                style = tabularNumberStyle(appleType.body),
                                maxLines = 1
                            )
                        }
                        trailing?.invoke()
                        if (showChevron) {
                            Icon(
                                imageVector = PortalIcons.ChevronRight,
                                contentDescription = null,
                                tint = colors.tertiaryLabel,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (destructive) colors.red else colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            if (trailing != null) {
                trailing()
            } else if (trailingValue != null) {
                Text(trailingValue, color = colors.textSecondary, fontSize = 13.sp)
            } else if (onClick != null && !destructive) {
                Icon(
                    imageVector = PortalIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 5.dp,
    trackColor: Color = color.copy(alpha = 0.18f),
    centerContent: (@Composable () -> Unit)? = null
) {
    val target = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 650, easing = EaseOutCubic)
    )
    val animColor by animateColorAsState(targetValue = color, animationSpec = tween(300))

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokePx = strokeWidth.toPx()
            val inset = strokePx / 2f
            val arcSize = Size((this.size.width - strokePx).coerceAtLeast(1f), (this.size.height - strokePx).coerceAtLeast(1f))
            val topLeft = Offset(inset, inset)
            val safeProgress = animatedProgress.coerceIn(0f, 1f)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            if (safeProgress > 0.002f) {
                drawArc(
                    color = animColor,
                    startAngle = -90f,
                    sweepAngle = 360f * safeProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }
        centerContent?.invoke()
    }
}

@Composable
fun AppleProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp
) {
    val colors = LocalPortalColors.current
    val safeTarget = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    val animatedRatio by animateFloatAsState(
        targetValue = safeTarget,
        animationSpec = tween(durationMillis = 600, easing = EaseOutCubic)
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(colors.systemFill)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedRatio.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun AppleActivityIndicator(
    modifier: Modifier = Modifier,
    color: Color = LocalPortalColors.current.textSecondary,
    size: Dp = 20.dp
) {
    val transition = rememberInfiniteTransition()
    val step by transition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 720, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    Canvas(modifier = modifier.size(size)) {
        val count = 8
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val outerR = this.size.minDimension * 0.46f
        val innerR = this.size.minDimension * 0.22f
        val strokeW = this.size.minDimension * 0.11f
        val activeIdx = step.toInt() % count

        for (i in 0 until count) {
            val angle = (i * 2.0 * PI / count) - PI / 2.0
            val dist = (activeIdx - i + count) % count
            val alpha = (1f - dist * 0.11f).coerceIn(0.18f, 1f)
            val cosA = cos(angle).toFloat()
            val sinA = sin(angle).toFloat()
            drawLine(
                color = color.copy(alpha = alpha),
                start = Offset(cx + cosA * innerR, cy + sinA * innerR),
                end = Offset(cx + cosA * outerR, cy + sinA * outerR),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun AppleEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.tertiaryLabel,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            color = colors.textPrimary,
            style = appleType.headline,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            color = colors.textSecondary,
            style = appleType.subheadline,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AppleSkeletonBox(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp)
) {
    val colors = LocalPortalColors.current
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = EaseOutCubic),
            repeatMode = RepeatMode.Reverse
        )
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.surface,
                        colors.systemFill.copy(alpha = alpha * 0.5f),
                        colors.surface
                    )
                )
            )
    )
}

fun tabularNumberStyle(base: TextStyle): TextStyle =
    base.copy(fontFeatureSettings = "tnum")
