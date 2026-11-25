package me.muriloscolari.unicep

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.muriloscolari.unicep.platform.PlatformBridge
import me.muriloscolari.unicep.platform.PortalHapticType
import me.muriloscolari.unicep.ui.designsystem.AppleActivityIndicator
import me.muriloscolari.unicep.ui.designsystem.AppleMotion
import me.muriloscolari.unicep.ui.designsystem.GlassButton
import me.muriloscolari.unicep.ui.designsystem.GlassSurface
import me.muriloscolari.unicep.ui.designsystem.LocalAppleTypography
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.ui.designsystem.PortalIcons
import me.muriloscolari.unicep.ui.util.initialsFromName
import me.muriloscolari.unicep.ui.util.toDisplayTitle
import kotlin.math.roundToInt

internal enum class MainTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    SEMESTER("semestre", "Semestre", PortalIcons.Grades, PortalIcons.GradesFill),
    COURSE("curso", "Curso", PortalIcons.Academic, PortalIcons.AcademicFill),
    VIRTUAL("virtual", "Virtual", PortalIcons.Virtual, PortalIcons.VirtualFill),
    CALENDAR("calendario", "Calendário", PortalIcons.Calendar, PortalIcons.CalendarFill),
    CENTRAL("central", "Central", PortalIcons.More, PortalIcons.MoreFill)
}

@Composable
internal fun AppleNavBar(
    title: String,
    collapseProgress: () -> Float,
    statusTop: Dp,
    unreadCount: Int,
    isDesktop: Boolean,
    loading: Boolean,
    onOpenNotifications: () -> Unit,
    onRefresh: () -> Unit
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = statusTop, start = 16.dp, end = 16.dp)
            .height(52.dp)
    ) {
        Text(
            text = title,
            color = colors.textPrimary,
            style = appleType.headline,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { alpha = collapseProgress().coerceIn(0f, 1f) }
        )

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isDesktop) {
                GlassButton(
                    onClick = onRefresh,
                    size = 40.dp,
                    contentDescription = "Atualizar"
                ) {
                    if (loading) {
                        AppleActivityIndicator(size = 18.dp, color = colors.accent)
                    } else {
                        Icon(
                            imageVector = PortalIcons.Refresh,
                            contentDescription = "Atualizar",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Box {
                GlassButton(
                    onClick = onOpenNotifications,
                    size = 40.dp,
                    contentDescription = "Notificações"
                ) {
                    Icon(
                        imageVector = PortalIcons.Bell,
                        contentDescription = "Central e Notificações",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 4.dp)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(colors.red)
                    )
                }
            }
        }
    }
}

@Composable
internal fun AppleToastHost(
    message: String?,
    isError: Boolean,
    topOffset: Dp,
    startOffset: Dp,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val scope = rememberCoroutineScope()
    var dragY by remember(message) { mutableFloatStateOf(0f) }

    LaunchedEffect(message, isError) {
        if (!message.isNullOrBlank() && !isError) {
            delay(4000)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        modifier = modifier.padding(top = topOffset, start = startOffset + 16.dp, end = 16.dp),
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(AppleMotion.standard),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(AppleMotion.standard)
    ) {
        GlassSurface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .offset(y = (dragY / 3f).dp)
                .clip(CircleShape)
                .pointerHoverIcon(PointerIcon.Hand)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        dragY = (dragY + delta).coerceAtMost(20f)
                    },
                    onDragStopped = {
                        if (dragY < -28f) onDismiss() else scope.launch { dragY = 0f }
                    }
                )
                .clickable { onDismiss() },
            shape = CircleShape,
            shadowElevation = 16.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isError) PortalIcons.Warning else PortalIcons.CheckCircle,
                    contentDescription = null,
                    tint = if (isError) colors.red else colors.green,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = message.orEmpty(),
                    color = colors.textPrimary,
                    style = appleType.subheadline,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun AppleLiquidGlassTabBar(
    currentTab: MainTab,
    pagerProgress: Float,
    minimized: Boolean,
    navBottom: Dp,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val tabs = MainTab.entries
    val selectedIndex = tabs.indexOf(currentTab).coerceAtLeast(0)

    val barHeight by animateDpAsState(
        targetValue = if (minimized) 48.dp else 62.dp,
        animationSpec = AppleMotion.dpStandard
    )
    val horizontalPad by animateDpAsState(
        targetValue = if (minimized) 44.dp else 12.dp,
        animationSpec = AppleMotion.dpStandard
    )

    val safePad = horizontalPad.coerceAtLeast(0.dp)
    val safeHeight = barHeight.coerceAtLeast(1.dp)

    var isDraggingLens by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragHoverIndex by remember { mutableIntStateOf(selectedIndex) }

    val lensScaleX by animateFloatAsState(
        targetValue = if (isDraggingLens) 1.07f else 1f,
        animationSpec = AppleMotion.press
    )
    val lensScaleY by animateFloatAsState(
        targetValue = if (isDraggingLens) 1.08f else 1f,
        animationSpec = AppleMotion.press
    )

    Box(
        modifier = modifier
            .padding(bottom = (navBottom + 12.dp).coerceAtLeast(0.dp), start = safePad, end = safePad)
            .widthIn(max = 460.dp)
            .fillMaxWidth()
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(safeHeight),
            shape = CircleShape,
            shadowElevation = 20.dp
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            ) {
                val itemWidth = (maxWidth / tabs.size).coerceAtLeast(1.dp)
                val itemWidthPx = with(density) { itemWidth.toPx() }.coerceAtLeast(1f)
                val maxOffsetPx = (itemWidthPx * (tabs.size - 1)).coerceAtLeast(0f)
                val clampedPager = pagerProgress.coerceIn(0f, tabs.lastIndex.toFloat())
                val lensOffsetDp = if (isDraggingLens) {
                    with(density) { dragOffsetPx.coerceIn(0f, maxOffsetPx).toDp() }
                } else {
                    itemWidth * clampedPager
                }
                val highlightedIndex = if (isDraggingLens) {
                    dragHoverIndex.coerceIn(0, tabs.lastIndex)
                } else {
                    clampedPager.roundToInt().coerceIn(0, tabs.lastIndex)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(tabs.size, itemWidthPx) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    val initial = (offset.x - itemWidthPx * 0.5f).coerceIn(0f, maxOffsetPx)
                                    dragOffsetPx = initial
                                    dragHoverIndex = (initial / itemWidthPx).roundToInt().coerceIn(0, tabs.lastIndex)
                                    isDraggingLens = true
                                    PlatformBridge.performHaptic(PortalHapticType.SELECTION)
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    val next = (dragOffsetPx + dragAmount).coerceIn(0f, maxOffsetPx)
                                    dragOffsetPx = next
                                    val nextIdx = (next / itemWidthPx).roundToInt().coerceIn(0, tabs.lastIndex)
                                    if (nextIdx != dragHoverIndex) {
                                        dragHoverIndex = nextIdx
                                        PlatformBridge.performHaptic(PortalHapticType.SELECTION)
                                    }
                                },
                                onDragEnd = {
                                    val targetIdx = (dragOffsetPx / itemWidthPx).roundToInt().coerceIn(0, tabs.lastIndex)
                                    isDraggingLens = false
                                    onSelect(tabs[targetIdx])
                                },
                                onDragCancel = {
                                    isDraggingLens = false
                                }
                            )
                        }
                ) {
                    // Lente Liquid Glass deslizante (segue o HorizontalPager e o arraste direto do dedo)
                    Box(
                        modifier = Modifier
                            .offset(x = lensOffsetDp)
                            .width(itemWidth)
                            .fillMaxHeight()
                            .graphicsLayer {
                                scaleX = lensScaleX.coerceIn(0.9f, 1.15f)
                                scaleY = lensScaleY.coerceIn(0.9f, 1.15f)
                            }
                            .clip(CircleShape)
                            .background(colors.glassLens)
                    )

                    Row(modifier = Modifier.fillMaxSize()) {
                        tabs.forEachIndexed { idx, tab ->
                            val isSelected = idx == highlightedIndex
                            val interactionSource = remember { MutableInteractionSource() }
                            val pressed by interactionSource.collectIsPressedAsState()
                            val itemScale by animateFloatAsState(
                                targetValue = if (pressed) 0.93f else 1f,
                                animationSpec = AppleMotion.press
                            )
                            val safeItemScale = itemScale.coerceIn(0.8f, 1.2f)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .graphicsLayer { scaleX = safeItemScale; scaleY = safeItemScale }
                                    .clip(CircleShape)
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .semantics {
                                        role = Role.Tab
                                        this.selected = isSelected
                                    }
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null
                                    ) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSelect(tab)
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (isSelected) colors.accent else colors.textPrimary,
                                    modifier = Modifier.size(if (minimized) 21.dp else 22.dp)
                                )
                                AnimatedVisibility(
                                    visible = !minimized,
                                    enter = fadeIn(tween(150)),
                                    exit = fadeOut(tween(110))
                                ) {
                                    Text(
                                        text = tab.label,
                                        color = if (isSelected) colors.accent else colors.textPrimary,
                                        style = appleType.caption2.copy(fontSize = 10.5.sp),
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppleDesktopSidebar(
    currentTab: MainTab,
    studentName: String?,
    studentRa: String?,
    unreadCount: Int,
    onSelect: (MainTab) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val shape = RoundedCornerShape(22.dp)

    GlassSurface(
        modifier = modifier
            .padding(8.dp)
            .width(232.dp)
            .fillMaxHeight(),
        shape = shape,
        shadowElevation = 18.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Brush.linearGradient(listOf(colors.accent, colors.indigo))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = PortalIcons.Academic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "UNICEP Portal",
                        color = colors.textPrimary,
                        style = appleType.headline,
                        maxLines = 1
                    )
                }

                val searchInteraction = remember { MutableInteractionSource() }
                val searchHovered by searchInteraction.collectIsHoveredAsState()
                val searchPressed by searchInteraction.collectIsPressedAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                searchPressed -> colors.systemFill
                                searchHovered -> colors.tertiaryFill.copy(alpha = 0.9f)
                                else -> colors.tertiaryFill
                            }
                        )
                        .pointerHoverIcon(PointerIcon.Hand)
                        .hoverable(searchInteraction)
                        .clickable(
                            interactionSource = searchInteraction,
                            indication = null,
                            onClick = onOpenSearch
                        )
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = PortalIcons.Search,
                            contentDescription = "Busca global",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Buscar...",
                            color = colors.textSecondary,
                            style = appleType.footnote
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.systemFill)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Ctrl K",
                            color = colors.textSecondary,
                            style = appleType.caption2,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                MainTab.entries.forEach { tab ->
                    val isSelected = tab == currentTab
                    val interactionSource = remember { MutableInteractionSource() }
                    val hovered by interactionSource.collectIsHoveredAsState()
                    val pressed by interactionSource.collectIsPressedAsState()
                    val rowBg = when {
                        isSelected -> colors.glassLens
                        pressed -> colors.systemFill
                        hovered -> colors.systemFill.copy(alpha = 0.45f)
                        else -> Color.Transparent
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(rowBg)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .hoverable(interactionSource)
                            .semantics {
                                role = Role.Tab
                                this.selected = isSelected
                            }
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) { onSelect(tab) }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.icon,
                                contentDescription = null,
                                tint = if (isSelected) colors.accent else colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = tab.label,
                                color = colors.textPrimary,
                                style = appleType.subheadline,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                        if (tab == MainTab.CENTRAL && unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 20.dp)
                                    .clip(CircleShape)
                                    .background(colors.red)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                                    color = Color.White,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            if (!studentName.isNullOrBlank() || !studentRa.isNullOrBlank()) {
                val shortName = studentName.orEmpty().toDisplayTitle().ifBlank { "Aluno" }
                val initials = remember(studentName) { initialsFromName(studentName) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { onSelect(MainTab.CENTRAL) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(colors.accent, colors.indigo))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            color = Color.White,
                            style = appleType.caption1,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = shortName,
                            color = colors.textPrimary,
                            style = appleType.footnote,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!studentRa.isNullOrBlank()) {
                            Text(
                                text = "RA $studentRa",
                                color = colors.textSecondary,
                                style = appleType.caption1,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun formatOfflineAge(lastSyncAtMillis: Long?): String {
    if (lastSyncAtMillis == null || lastSyncAtMillis <= 0L) return "Dados salvos"
    val diffMs = (PlatformBridge.currentTimeMillis() - lastSyncAtMillis).coerceAtLeast(0L)
    val mins = diffMs / 60_000L
    return when {
        mins < 1L -> "Atualizado agora há pouco"
        mins < 60L -> "Atualizado há ${mins} min"
        mins < 24L * 60L -> "Atualizado há ${mins / 60L}h"
        else -> "Atualizado há ${mins / (24L * 60L)}d"
    }
}
