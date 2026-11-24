package me.muriloscolari.unicep.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.muriloscolari.unicep.data.api.ReportCardItem
import me.muriloscolari.unicep.data.api.VirtualAssignmentItem
import me.muriloscolari.unicep.data.repo.SubjectVirtualLink
import me.muriloscolari.unicep.platform.DesignStyle
import me.muriloscolari.unicep.platform.PlatformBridge
import me.muriloscolari.unicep.platform.PlatformVerticalScrollbar
import me.muriloscolari.unicep.platform.PortalHapticType
import me.muriloscolari.unicep.ui.designsystem.AppleSheet
import me.muriloscolari.unicep.ui.designsystem.CardStyle
import me.muriloscolari.unicep.ui.designsystem.LocalAppleTypography
import me.muriloscolari.unicep.ui.designsystem.LocalDesignStyle
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.ui.designsystem.LocalPredictiveBackProgress
import me.muriloscolari.unicep.ui.designsystem.PortalBadge
import me.muriloscolari.unicep.ui.designsystem.PortalCard
import me.muriloscolari.unicep.ui.designsystem.PortalDivider
import me.muriloscolari.unicep.ui.designsystem.PortalIcons
import me.muriloscolari.unicep.ui.designsystem.PortalPrimaryButton
import me.muriloscolari.unicep.ui.designsystem.PortalSecondaryButton
import me.muriloscolari.unicep.ui.designsystem.PortalSectionHeader
import me.muriloscolari.unicep.ui.designsystem.PortalStatusDot
import me.muriloscolari.unicep.ui.designsystem.ProgressRing
import me.muriloscolari.unicep.ui.designsystem.predictiveBackScrimAlpha
import me.muriloscolari.unicep.ui.designsystem.predictiveBackSurfaceTransform
import me.muriloscolari.unicep.ui.designsystem.tabularNumberStyle
import me.muriloscolari.unicep.ui.util.toDisplayTitle
import kotlin.math.roundToInt

@Composable
fun AppleAttendanceLimitSheet(
    subjects: List<UnifiedSubject>,
    onSelectSubject: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val countLabel = if (subjects.size == 1) "1 disciplina" else "${subjects.size} disciplinas"

    AppleSheet(
        title = "Frequência no limite",
        subtitle = "$countLabel com até 3 faltas restantes",
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            subjects.forEach { subject ->
                val report = subject.report ?: return@forEach
                val badgeColor = if (report.remainingAbsences <= 0) colors.red else colors.orange
                val badgeText = when {
                    report.remainingAbsences < 0 -> "Excedeu ${-report.remainingAbsences}"
                    report.remainingAbsences == 0 -> "Sem margem"
                    report.remainingAbsences == 1 -> "Resta 1 falta"
                    else -> "Restam ${report.remainingAbsences} faltas"
                }

                PortalCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelectSubject(subject.code) },
                    cardStyle = CardStyle.Plain,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = subject.name.toDisplayTitle(),
                                color = colors.textPrimary,
                                style = appleType.headline
                            )
                            Text(
                                text = listOfNotNull(
                                    subject.code,
                                    report.classGroup.takeIf { it.isNotBlank() },
                                    "${report.hours}h"
                                ).joinToString(" · "),
                                color = colors.textSecondary,
                                style = appleType.footnote
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PortalBadge(badgeText, badgeColor)
                            Icon(
                                imageVector = PortalIcons.ChevronRight,
                                contentDescription = null,
                                tint = colors.tertiaryLabel,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    if (report.maxAbsences > 0) {
                        Spacer(Modifier.height(10.dp))
                        AppleAttendanceBlock(report = report)
                    }
                    if (report.updatedAt.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Atualizado em ${report.updatedAt}",
                            color = colors.tertiaryLabel,
                            style = appleType.caption1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppleSubjectDetailSheet(
    subject: UnifiedSubject,
    onDismiss: () -> Unit,
    freqAlertThreshold: Int = 80,
    virtualLink: SubjectVirtualLink? = null,
    onOpenVirtualCourse: ((Long) -> Unit)? = null,
    onOpenVirtualAssignment: ((VirtualAssignmentItem) -> Unit)? = null,
    onOpenVirtualTab: (() -> Unit)? = null
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val style = LocalDesignStyle.current
    val haptics = LocalHapticFeedback.current
    val grade = subject.grade
    val report = subject.report
    val slots = remember(grade) { buildEvalSlots(grade) }

    var targetAvg by remember { mutableFloatStateOf(6.0f) }
    val sim = remember(slots, targetAvg) { computeNeededGrade(slots, targetAvg = targetAvg.toDouble()) }

    val avgVal = subject.numericAverage()
    val avgText = grade?.average?.display?.takeIf { it.isNotBlank() }
        ?: report?.finalAverage?.takeIf { it.isNotBlank() }
        ?: "—"
    val belowPassing = grade?.belowPassing == true || (avgVal != null && avgVal < 6.0)
    val avgColor = when {
        avgVal == null -> colors.tertiaryLabel
        belowPassing -> colors.red
        else -> colors.green
    }
    val isFinal = !report?.finalAverage.isNullOrBlank() && grade?.average?.display.isNullOrBlank()
    val matchedCourse = virtualLink?.course
    val pendingAssignments = virtualLink?.pendingAssignments.orEmpty()

    AppleSheet(
        title = subject.name.toDisplayTitle(),
        subtitle = listOfNotNull(
            subject.code,
            report?.classGroup?.takeIf { it.isNotBlank() }
        ).joinToString(" · "),
        onDismiss = onDismiss,
        desktopMaxWidth = 640.dp
    ) {
        // Hero com ProgressRing 88dp
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            ProgressRing(
                progress = ((avgVal ?: 0.0) / 10.0).toFloat().coerceIn(0f, 1f),
                color = avgColor,
                size = 88.dp,
                strokeWidth = 7.dp,
                trackColor = colors.systemFill
            ) {
                Text(
                    text = avgText,
                    color = if (avgVal == null) colors.textSecondary else colors.textPrimary,
                    style = tabularNumberStyle(appleType.title1)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isFinal) "Média final" else "Média parcial",
                    color = colors.textPrimary,
                    style = appleType.headline
                )
                val statusLabel = report?.statusLabel?.takeIf { it.isNotBlank() } ?: "Cursando"
                val statusColor = when {
                    statusLabel.contains("aprov", ignoreCase = true) -> colors.green
                    statusLabel.contains("reprov", ignoreCase = true) -> colors.red
                    belowPassing -> colors.orange
                    else -> colors.accent
                }
                PortalStatusDot(text = statusLabel, color = statusColor, tintText = true)
                if (report != null && report.hours > 0) {
                    Text(
                        text = "${report.hours}h de carga horária",
                        color = colors.textSecondary,
                        style = appleType.footnote
                    )
                }
            }
        }

        // Integração direta com a Sala Virtual (botão + tarefas pendentes desta disciplina)
        if (matchedCourse != null && onOpenVirtualCourse != null) {
            PortalCard(
                modifier = Modifier.fillMaxWidth(),
                cardStyle = if (pendingAssignments.isNotEmpty()) CardStyle.Tinted(colors.orange) else CardStyle.Tinted(colors.accent),
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = PortalIcons.Virtual,
                            contentDescription = null,
                            tint = if (pendingAssignments.isNotEmpty()) colors.orange else colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sala Virtual",
                                color = colors.textPrimary,
                                style = appleType.headline
                            )
                            Text(
                                text = when (pendingAssignments.size) {
                                    0 -> "Nenhuma atividade pendente nesta disciplina"
                                    1 -> "1 atividade pendente para entregar"
                                    else -> "${pendingAssignments.size} atividades pendentes para entregar"
                                },
                                color = colors.textSecondary,
                                style = appleType.caption1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (pendingAssignments.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        PortalBadge(
                            text = "${pendingAssignments.size}",
                            color = colors.orange
                        )
                    }
                }

                if (pendingAssignments.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    PortalDivider()
                    Spacer(Modifier.height(6.dp))
                    pendingAssignments.take(4).forEachIndexed { idx, item ->
                        if (idx > 0) PortalDivider(Modifier.padding(vertical = 2.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onOpenVirtualAssignment?.invoke(item)
                                }
                                .padding(vertical = 7.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(colors.orange)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    color = colors.textPrimary,
                                    style = appleType.subheadline,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val dueLabel = formatIsoDateTime(item.dueDate.ifBlank { item.endDate }, item.dueAtMillis)
                                if (dueLabel.isNotBlank()) {
                                    Text(
                                        text = "Entrega até $dueLabel",
                                        color = colors.textSecondary,
                                        style = appleType.caption1,
                                        maxLines = 1
                                    )
                                }
                            }
                            Icon(
                                imageVector = PortalIcons.ChevronRight,
                                contentDescription = null,
                                tint = colors.tertiaryLabel,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    if (pendingAssignments.size > 4) {
                        Text(
                            text = "+ ${pendingAssignments.size - 4} outra(s) atividade(s) na Sala Virtual",
                            color = colors.textSecondary,
                            style = appleType.caption1,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                PortalPrimaryButton(
                    text = "Abrir disciplina na Sala Virtual",
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenVirtualCourse(matchedCourse.orgUnitId)
                    }
                )
            }
            Spacer(Modifier.height(16.dp))
        } else if (onOpenVirtualTab != null) {
            PortalSecondaryButton(
                text = "Abrir Sala Virtual",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onOpenVirtualTab()
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
        }

        // Avaliações em lista agrupada
        PortalSectionHeader("Avaliações")
        if (slots.isEmpty()) {
            PortalCard(
                modifier = Modifier.fillMaxWidth(),
                cardStyle = CardStyle.Plain
            ) {
                Text(
                    text = "Aguardando lançamento de notas ou pesos nesta disciplina.",
                    color = colors.textSecondary,
                    style = appleType.subheadline
                )
            }
        } else {
            PortalCard(
                modifier = Modifier.fillMaxWidth(),
                cardStyle = CardStyle.Plain,
                contentPadding = PaddingValues(0.dp)
            ) {
                SelectionContainer {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        slots.forEachIndexed { idx, slot ->
                            if (idx > 0) PortalDivider(Modifier.padding(start = 16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = slot.label,
                                        color = colors.textPrimary,
                                        style = appleType.body
                                    )
                                    if (slot.weightDisplay.isNotBlank()) {
                                        Text(
                                            text = "Peso ${slot.weightDisplay}%",
                                            color = colors.textSecondary,
                                            style = appleType.footnote
                                        )
                                    }
                                }
                                Text(
                                    text = slot.gradeDisplay.ifBlank { "—" },
                                    color = if (slot.gradeDisplay.isBlank()) colors.tertiaryLabel else colors.textPrimary,
                                    style = tabularNumberStyle(appleType.headline)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Simulador interativo de meta
        if (sim != null) {
            Spacer(Modifier.height(16.dp))
            PortalCard(
                modifier = Modifier.fillMaxWidth(),
                cardStyle = CardStyle.Tinted(colors.accent),
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = PortalIcons.Sparkles,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Simulador · Meta ${formatOneDecimal(targetAvg.toDouble())}",
                            color = colors.accent,
                            style = tabularNumberStyle(appleType.footnote),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                SelectionContainer {
                    Text(
                        text = sim,
                        color = colors.textPrimary,
                        style = appleType.subheadline
                    )
                }
                Spacer(Modifier.height(6.dp))
                val sliderHaptics = LocalHapticFeedback.current
                Slider(
                    value = targetAvg,
                    onValueChange = { raw ->
                        val snapped = ((raw * 2f).roundToInt() / 2f).coerceIn(5.0f, 10.0f)
                        if (snapped != targetAvg) {
                            sliderHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            targetAvg = snapped
                        }
                    },
                    valueRange = 5.0f..10.0f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = colors.accent,
                        inactiveTrackColor = colors.systemFill
                    )
                )
            }
        }

        // Frequência e Boletim
        if (report != null) {
            Spacer(Modifier.height(16.dp))
            PortalSectionHeader("Frequência e boletim")
            PortalCard(
                modifier = Modifier.fillMaxWidth(),
                cardStyle = CardStyle.Plain,
                contentPadding = PaddingValues(16.dp)
            ) {
                SelectionContainer {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (report.maxAbsences > 0) {
                            AppleAttendanceBlock(report = report)
                            Spacer(Modifier.height(14.dp))
                            PortalDivider()
                            Spacer(Modifier.height(14.dp))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppleDetailMetric(
                                label = "Faltas",
                                value = "${report.absences}/${if (report.maxAbsences > 0) report.maxAbsences else "—"}",
                                modifier = Modifier.weight(1f)
                            )
                            AppleDetailMetric(
                                label = "Restam",
                                value = if (report.maxAbsences > 0) "${report.remainingAbsences.coerceAtLeast(0)}" else "—",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppleDetailMetric(
                                label = "Frequência",
                                value = report.freqPercent?.let { "${it.toInt()}%" } ?: "—",
                                modifier = Modifier.weight(1f)
                            )
                            AppleDetailMetric(
                                label = "Carga horária",
                                value = "${report.hours}h",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (report.updatedAt.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Faltas atualizadas em ${report.updatedAt}",
                                color = colors.tertiaryLabel,
                                style = appleType.caption1
                            )
                        }
                    }
                }
            }
            if (report.hours > 0) {
                Spacer(Modifier.height(14.dp))
                AttendanceSimulatorSection(
                    report = report,
                    thresholdPercent = freqAlertThreshold,
                    isApple = style == DesignStyle.APPLE
                )
            }
        }
    }
}

@Composable
private fun AppleDetailMetric(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    Column(modifier = modifier) {
        Text(label, color = colors.textSecondary, style = appleType.caption1)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = colors.textPrimary,
            style = tabularNumberStyle(appleType.headline)
        )
    }
}

@Composable
internal fun PortalOverlayModal(
    title: String,
    subtitle: String?,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalPortalColors.current
    val readBack = LocalPredictiveBackProgress.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .predictiveBackScrimAlpha(readBack)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val maxModalHeight = (maxHeight - 48.dp).coerceIn(360.dp, 760.dp)
        val scrollState = rememberScrollState()
        Surface(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .heightIn(max = maxModalHeight)
                .predictiveBackSurfaceTransform(
                    readBack = readBack,
                    slideDp = 0f,
                    verticalSlideDp = 36f,
                    scaleDrop = 0.08f,
                    pivotY = 0.5f
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(20.dp),
            color = colors.surface,
            border = BorderStroke(0.5.dp, colors.divider),
            tonalElevation = 6.dp
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        SelectionContainer(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Column {
                                Text(title, color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                if (!subtitle.isNullOrBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(subtitle, color = colors.textSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(PortalIcons.Close, contentDescription = "Fechar", tint = colors.textSecondary)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    PortalDivider()
                    Spacer(Modifier.height(14.dp))
                    content()
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(vertical = 8.dp, horizontal = 3.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    PlatformVerticalScrollbar(
                        scrollState = scrollState,
                        modifier = Modifier.fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun AttendanceSimulatorSection(
    report: ReportCardItem,
    thresholdPercent: Int,
    isApple: Boolean
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    var extraAbsences by remember(report.code, report.absences) { mutableIntStateOf(0) }

    val maxAbsences = if (report.maxAbsences > 0) report.maxAbsences else (report.hours * 0.25).toInt()
    val simAbsences = report.absences + extraAbsences
    val simRemaining = maxAbsences - simAbsences
    val simFreq = if (extraAbsences == 0 && report.freqPercent != null) {
        report.freqPercent.coerceIn(0.0, 100.0)
    } else {
        ((1.0 - (simAbsences.toDouble() / report.hours.toDouble())) * 100.0).coerceIn(0.0, 100.0)
    }
    val simFreqInt = simFreq.roundToInt()

    val statusColor = when {
        simRemaining < 0 || simFreq < 75.0 -> colors.red
        (thresholdPercent > 0 && simFreq <= thresholdPercent.toDouble()) || simRemaining <= 3 -> colors.orange
        else -> colors.green
    }

    fun applyDelta(delta: Int) {
        val nextExtra = (extraAbsences + delta).coerceAtLeast(0)
        if (nextExtra == extraAbsences) return
        val prevRemaining = maxAbsences - (report.absences + extraAbsences)
        val nextRemaining = maxAbsences - (report.absences + nextExtra)
        extraAbsences = nextExtra
        if (maxAbsences > 0 && prevRemaining >= 0 && nextRemaining < 0) {
            PlatformBridge.performHaptic(PortalHapticType.BOUNDARY_THUD, 1f)
        } else {
            PlatformBridge.performHaptic(PortalHapticType.SELECTION)
        }
    }

    PortalCard(
        modifier = Modifier.fillMaxWidth(),
        cardStyle = if (isApple) CardStyle.Tinted(statusColor) else CardStyle.Plain,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "E se eu faltar?",
                    color = colors.textPrimary,
                    style = if (isApple) appleType.headline else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (extraAbsences == 0) {
                        "Simule faltas futuras antes de acontecer"
                    } else {
                        "+$extraAbsences ${if (extraAbsences == 1) "falta simulada" else "faltas simuladas"}"
                    },
                    color = colors.textSecondary,
                    style = appleType.caption1
                )
            }
            if (extraAbsences > 0) {
                Text(
                    text = "Zerar",
                    color = colors.accent,
                    style = appleType.footnote,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            PlatformBridge.performHaptic(PortalHapticType.SELECTION)
                            extraAbsences = 0
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Faltas sim.", color = colors.textSecondary, style = appleType.caption2)
                Text(
                    text = "$simAbsences/$maxAbsences",
                    color = colors.textPrimary,
                    style = tabularNumberStyle(appleType.headline),
                    fontWeight = FontWeight.Bold
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Restam", color = colors.textSecondary, style = appleType.caption2)
                Text(
                    text = if (simRemaining >= 0) "$simRemaining" else "Excedeu ${-simRemaining}",
                    color = statusColor,
                    style = tabularNumberStyle(appleType.headline),
                    fontWeight = FontWeight.Bold
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Frequência", color = colors.textSecondary, style = appleType.caption2)
                Text(
                    text = "$simFreqInt%",
                    color = statusColor,
                    style = tabularNumberStyle(appleType.headline),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        val statusSummary = when {
            simRemaining < 0 -> "Acima do limite de 25% — reprovação por falta."
            simRemaining == 0 -> "No limite máximo (0 faltas restantes)."
            thresholdPercent > 0 && simFreq <= thresholdPercent.toDouble() ->
                "Abaixo/no alerta configurado ($thresholdPercent%)."
            else -> "Dentro do limite seguro ($simRemaining faltas de folga)."
        }
        Text(
            text = statusSummary,
            color = statusColor,
            style = appleType.caption1,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SimulatorStepButton(
                label = "−1",
                enabled = extraAbsences > 0,
                modifier = Modifier.weight(1f),
                onClick = { applyDelta(-1) }
            )
            SimulatorStepButton(
                label = "+1 falta",
                enabled = true,
                modifier = Modifier.weight(1.3f),
                onClick = { applyDelta(1) }
            )
            SimulatorStepButton(
                label = "+2 faltas",
                enabled = true,
                modifier = Modifier.weight(1.3f),
                onClick = { applyDelta(2) }
            )
        }
    }
}

@Composable
private fun SimulatorStepButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) colors.surfaceSecondary else colors.surfaceSecondary.copy(alpha = 0.45f))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) colors.textPrimary else colors.tertiaryLabel,
            style = appleType.footnote,
            fontWeight = FontWeight.SemiBold
        )
    }
}
