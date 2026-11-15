package me.muriloscolari.unicep.ui.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.muriloscolari.unicep.platform.PlatformBridge
import kotlin.math.roundToInt

enum class AppleSheetDetent { Medium, Large }

/**
 * Sheet em estilo iOS 26 Liquid Glass:
 * - Mobile: sobe de baixo com grabber, detents Medium (flutuante com inset) e Large, e gesto de arrastar.
 * - Desktop (>= 640dp): form sheet centralizado em vidro.
 */
@Composable
fun AppleSheet(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    initialDetent: AppleSheetDetent = AppleSheetDetent.Medium,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var detent by remember { mutableStateOf(initialDetent) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val dragOffsetY = remember { Animatable(0f) }

    val readBack = LocalPredictiveBackProgress.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isDesktop = maxWidth >= 640.dp
        val maxH = maxHeight
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        // Scrim
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(AppleMotion.standard),
            exit = fadeOut(AppleMotion.standard)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .predictiveBackScrimAlpha(readBack)
                    .background(colors.scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        if (isDesktop) {
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                enter = fadeIn(AppleMotion.standard) + scaleIn(
                    initialScale = 0.92f,
                    animationSpec = AppleMotion.sheet
                ),
                exit = fadeOut(AppleMotion.standard) + scaleOut(
                    targetScale = 0.94f,
                    animationSpec = AppleMotion.standard
                )
            ) {
                GlassSurface(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .heightIn(max = (maxH - 48.dp).coerceAtLeast(320.dp))
                        .predictiveBackSurfaceTransform(readBack)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        ),
                    shape = RoundedCornerShape(28.dp),
                    tint = colors.surface.copy(alpha = if (colors.isDark) 0.84f else 0.86f),
                    shadowElevation = 24.dp
                ) {
                    SheetInnerContent(
                        title = title,
                        subtitle = subtitle,
                        onDismiss = onDismiss,
                        showGrabber = false,
                        bottomPadding = 20.dp,
                        content = content
                    )
                }
            }
        } else {
            val isLarge = detent == AppleSheetDetent.Large
            val sideInset by animateDpAsState(
                targetValue = if (isLarge) 0.dp else 8.dp,
                animationSpec = AppleMotion.dpSheet
            )
            val bottomInset by animateDpAsState(
                targetValue = if (isLarge) 0.dp else (8.dp + navBottom),
                animationSpec = AppleMotion.dpSheet
            )
            val targetMaxHeight by animateDpAsState(
                targetValue = if (isLarge) (maxH - statusTop - 10.dp).coerceAtLeast(360.dp) else (maxH * 0.64f).coerceAtLeast(340.dp),
                animationSpec = AppleMotion.dpSheet
            )
            val cornerBottom by animateDpAsState(
                targetValue = if (isLarge) 0.dp else 34.dp,
                animationSpec = AppleMotion.dpSheet
            )
            val safeSideInset = sideInset.coerceAtLeast(0.dp)
            val safeBottomInset = bottomInset.coerceAtLeast(0.dp)
            val safeMaxHeight = targetMaxHeight.coerceAtLeast(200.dp)
            val safeCornerBottom = cornerBottom.coerceAtLeast(0.dp)
            val shape = RoundedCornerShape(
                topStart = 34.dp,
                topEnd = 34.dp,
                bottomStart = safeCornerBottom,
                bottomEnd = safeCornerBottom
            )

            val dismissThresholdPx = with(density) { 120.dp.toPx() }
            val expandThresholdPx = with(density) { 64.dp.toPx() }
            val velocityThresholdPx = with(density) { 1100.dp.toPx() }

            val draggableState = rememberDraggableState { delta ->
                scope.launch {
                    val next = (dragOffsetY.value + delta).coerceAtLeast(-expandThresholdPx)
                    dragOffsetY.snapTo(next)
                }
            }

            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 1.0f, stiffness = 300f)
                ) + fadeIn(AppleMotion.standard),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 1.0f, stiffness = 340f)
                ) + fadeOut(AppleMotion.standard)
            ) {
                GlassSurface(
                    modifier = Modifier
                        .padding(start = safeSideInset, end = safeSideInset, bottom = safeBottomInset)
                        .fillMaxWidth()
                        .heightIn(max = safeMaxHeight)
                        .offset { IntOffset(0, dragOffsetY.value.roundToInt().coerceAtLeast(0)) }
                        .predictiveBackSurfaceTransform(readBack)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = draggableState,
                            onDragStopped = { velocity ->
                                val offset = dragOffsetY.value
                                when {
                                    offset < -expandThresholdPx || velocity < -velocityThresholdPx -> {
                                        detent = AppleSheetDetent.Large
                                        scope.launch { dragOffsetY.animateTo(0f, AppleMotion.sheet) }
                                    }
                                    offset > dismissThresholdPx || velocity > velocityThresholdPx -> {
                                        if (detent == AppleSheetDetent.Large && velocity < velocityThresholdPx * 1.4f && offset < dismissThresholdPx * 1.5f) {
                                            detent = AppleSheetDetent.Medium
                                            scope.launch { dragOffsetY.animateTo(0f, AppleMotion.sheet) }
                                        } else {
                                            onDismiss()
                                        }
                                    }
                                    else -> {
                                        scope.launch { dragOffsetY.animateTo(0f, AppleMotion.sheet) }
                                    }
                                }
                            }
                        ),
                    shape = shape,
                    tint = colors.surface.copy(
                        alpha = when {
                            isLarge && colors.isDark -> 0.96f
                            isLarge && !colors.isDark -> 0.96f
                            colors.isDark -> 0.82f
                            else -> 0.86f
                        }
                    ),
                    shadowElevation = if (isLarge) 18.dp else 24.dp
                ) {
                    SheetInnerContent(
                        title = title,
                        subtitle = subtitle,
                        onDismiss = onDismiss,
                        showGrabber = true,
                        onToggleDetent = {
                            detent = if (detent == AppleSheetDetent.Medium) AppleSheetDetent.Large else AppleSheetDetent.Medium
                        },
                        bottomPadding = if (isLarge) (20.dp + navBottom) else 20.dp,
                        content = content
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetInnerContent(
    title: String,
    subtitle: String?,
    onDismiss: () -> Unit,
    showGrabber: Boolean,
    onToggleDetent: (() -> Unit)? = null,
    bottomPadding: androidx.compose.ui.unit.Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current

    Column(modifier = Modifier.fillMaxWidth()) {
        if (showGrabber) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onToggleDetent?.invoke() }
                    )
                    .padding(top = 8.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(colors.tertiaryLabel)
                )
            }
        } else {
            Spacer(Modifier.height(10.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    style = appleType.title3
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = colors.textSecondary,
                        style = appleType.footnote
                    )
                }
            }
            GlassButton(
                onClick = onDismiss,
                size = 32.dp,
                insideGlassBar = true,
                contentDescription = "Fechar"
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colors.secondaryFill),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = PortalIcons.Close,
                        contentDescription = "Fechar",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        PortalDivider(Modifier.padding(horizontal = 20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = bottomPadding),
            content = content
        )
    }
}

/**
 * Alerta estilo iOS 26 em vidro com botões em cápsula lado a lado.
 */
@Composable
fun AppleAlert(
    title: String,
    message: String? = null,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String? = "Cancelar",
    onDismiss: () -> Unit,
    confirmDestructive: Boolean = false,
    confirmEnabled: Boolean = true,
    dismissEnabled: Boolean = true,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val confirmColor = if (confirmDestructive) colors.red else colors.accent
    val readBack = LocalPredictiveBackProgress.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .predictiveBackScrimAlpha(readBack)
            .background(colors.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (dismissEnabled) onDismiss() }
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            modifier = Modifier
                .widthIn(max = 316.dp)
                .fillMaxWidth()
                .predictiveBackSurfaceTransform(readBack, slideDp = 40f, verticalSlideDp = 12f, scaleDrop = 0.08f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(28.dp),
            tint = colors.surface.copy(alpha = if (colors.isDark) 0.90f else 0.92f),
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    style = appleType.headline,
                    textAlign = TextAlign.Center
                )
                if (!message.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = message,
                        color = colors.textSecondary,
                        style = appleType.footnote,
                        textAlign = TextAlign.Center
                    )
                }
                if (extraContent != null) {
                    Spacer(Modifier.height(12.dp))
                    extraContent()
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (dismissText != null) {
                        AlertCapsuleButton(
                            text = dismissText,
                            backgroundColor = colors.secondaryFill,
                            textColor = colors.textPrimary,
                            enabled = dismissEnabled,
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    AlertCapsuleButton(
                        text = confirmText,
                        backgroundColor = confirmColor,
                        textColor = Color.White,
                        enabled = confirmEnabled,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertCapsuleButton(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = remember { PlatformBridge.isReduceMotionEnabled() }
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduceMotion) AppleMotion.PRESS_SCALE else 1f,
        animationSpec = AppleMotion.press
    )

    Box(
        modifier = modifier
            .height(42.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(backgroundColor.copy(alpha = if (enabled) (if (pressed) 0.78f else 1f) else 0.45f))
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor.copy(alpha = if (enabled) 1f else 0.65f),
            style = LocalAppleTypography.current.subheadline,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
