package me.muriloscolari.unicep.ui.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.muriloscolari.unicep.platform.DesignStyle

@Composable
fun PortalCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val style = LocalDesignStyle.current
    val colors = LocalPortalColors.current

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null && style == DesignStyle.APPLE) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    val shape = RoundedCornerShape(if (style == DesignStyle.APPLE) 20.dp else 16.dp)
    val border = if (style == DesignStyle.APPLE) BorderStroke(0.5.dp, colors.divider) else BorderStroke(0.8.dp, colors.divider.copy(alpha = 0.45f))
    val tonalElevation = if (style == DesignStyle.MATERIAL) 3.dp else 0.dp

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
                        indication = if (style == DesignStyle.MATERIAL) androidx.compose.material3.ripple() else null,
                        onClick = onClick
                    )
                } else Modifier
            ),
        shape = shape,
        color = colors.surface,
        tonalElevation = tonalElevation,
        border = border
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun PortalSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    val style = LocalDesignStyle.current
    val colors = LocalPortalColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (style == DesignStyle.APPLE) title.uppercase() else title,
            color = colors.textSecondary,
            fontSize = if (style == DesignStyle.APPLE) 11.5.sp else 13.sp,
            fontWeight = if (style == DesignStyle.APPLE) FontWeight.SemiBold else FontWeight.Medium,
            letterSpacing = if (style == DesignStyle.APPLE) 0.7.sp else 0.1.sp
        )
        trailing?.invoke()
    }
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

    if (options.isEmpty()) return

    if (style == DesignStyle.APPLE) {
        val containerShape = RoundedCornerShape(11.dp)
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(containerShape)
                .background(colors.surfaceSecondary)
                .padding(3.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                options.forEachIndexed { index, label ->
                    val selected = index == selectedIndex
                    val pillShape = RoundedCornerShape(8.5.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(pillShape)
                            .background(if (selected) colors.surface else Color.Transparent)
                            .then(
                                if (selected) {
                                    Modifier.border(0.5.dp, colors.divider, pillShape)
                                } else Modifier
                            )
                            .clickable { onSelect(index) }
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (selected) colors.textPrimary else colors.textSecondary,
                            fontSize = 13.sp,
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
                val selected = index == selectedIndex
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
    val shape = if (style == DesignStyle.APPLE) CircleShape else RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
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
    val shape = if (style == DesignStyle.APPLE) RoundedCornerShape(14.dp) else CircleShape

    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.height(48.dp),
        shape = shape,
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

@Composable
fun PortalSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    val style = LocalDesignStyle.current
    val colors = LocalPortalColors.current
    val fg = tint ?: colors.accent

    if (style == DesignStyle.APPLE) {
        val shape = RoundedCornerShape(13.dp)
        Box(
            modifier = modifier
                .height(44.dp)
                .clip(shape)
                .background(fg.copy(alpha = 0.13f))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
fun PortalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true
) {
    val style = LocalDesignStyle.current
    val colors = LocalPortalColors.current

    if (style == DesignStyle.APPLE) {
        // Native Cupertino-style filled rounded input (no Material floating outline border)
        val interactionSource = remember { MutableInteractionSource() }
        val focused by interactionSource.collectIsFocusedAsState()
        val shape = RoundedCornerShape(12.dp)

        Column(modifier = modifier.fillMaxWidth()) {
            Text(
                text = label,
                color = if (focused) colors.accent else colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp, bottom = 5.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.surfaceSecondary.copy(alpha = 0.78f))
                    .border(
                        width = if (focused) 1.5.dp else 0.5.dp,
                        color = if (focused) colors.accent else colors.divider,
                        shape = shape
                    )
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = label,
                        color = colors.textSecondary.copy(alpha = 0.65f),
                        fontSize = 15.sp
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = singleLine,
                    textStyle = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(colors.accent),
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    interactionSource = interactionSource
                )
            }
        }
    } else {
        // Material Design 3 OutlinedTextField
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
    onClick: (() -> Unit)? = null
) {
    val colors = LocalPortalColors.current
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
                color = colors.textPrimary,
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
        } else if (onClick != null) {
            Icon(
                imageVector = PortalIcons.ChevronRight,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

fun tabularNumberStyle(base: TextStyle): TextStyle =
    base.copy(fontFeatureSettings = "tnum")
