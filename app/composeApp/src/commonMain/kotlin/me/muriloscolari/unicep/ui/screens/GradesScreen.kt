package me.muriloscolari.unicep.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.muriloscolari.unicep.data.api.DashboardResponse
import me.muriloscolari.unicep.data.api.NotificationItem
import me.muriloscolari.unicep.data.api.PartialGradeItem
import me.muriloscolari.unicep.data.api.PeriodOption
import me.muriloscolari.unicep.data.api.ReportCardItem
import me.muriloscolari.unicep.data.api.ReportCardResponse
import me.muriloscolari.unicep.data.api.StudentInfo
import me.muriloscolari.unicep.platform.DesignStyle
import me.muriloscolari.unicep.platform.PlatformBridge
import me.muriloscolari.unicep.platform.PortalHapticType
import me.muriloscolari.unicep.platform.buildGradesExportData
import me.muriloscolari.unicep.ui.designsystem.ExportActionModal
import me.muriloscolari.unicep.ui.designsystem.AppleEmptyState
import me.muriloscolari.unicep.ui.designsystem.AppleProgressBar
import me.muriloscolari.unicep.ui.designsystem.AppleSheet
import me.muriloscolari.unicep.ui.designsystem.AppleSkeletonBox
import me.muriloscolari.unicep.ui.designsystem.CardStyle
import me.muriloscolari.unicep.ui.designsystem.GlassMenu
import me.muriloscolari.unicep.ui.designsystem.GlassMenuItem
import me.muriloscolari.unicep.ui.designsystem.GlassSurface
import me.muriloscolari.unicep.ui.designsystem.LocalAppleContentInsets
import me.muriloscolari.unicep.ui.designsystem.LocalAppleTypography
import me.muriloscolari.unicep.ui.designsystem.LocalDesignStyle
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.ui.designsystem.PortalBadge
import me.muriloscolari.unicep.ui.designsystem.PortalCard
import me.muriloscolari.unicep.ui.designsystem.PortalDivider
import me.muriloscolari.unicep.ui.designsystem.PortalIcons
import me.muriloscolari.unicep.ui.designsystem.PortalListRow
import me.muriloscolari.unicep.ui.designsystem.PortalSectionHeader
import me.muriloscolari.unicep.ui.designsystem.PortalSegmentedControl
import me.muriloscolari.unicep.ui.designsystem.PortalStatusDot
import me.muriloscolari.unicep.ui.designsystem.ProgressRing
import me.muriloscolari.unicep.ui.designsystem.appleScrollBoundaryHaptics
import me.muriloscolari.unicep.ui.designsystem.predictiveBackScrimAlpha
import me.muriloscolari.unicep.ui.designsystem.predictiveBackSurfaceTransform
import me.muriloscolari.unicep.ui.designsystem.LocalPredictiveBackProgress
import me.muriloscolari.unicep.ui.designsystem.tabularNumberStyle
import me.muriloscolari.unicep.ui.util.toDisplayTitle
import kotlin.math.roundToInt

data class UnifiedSubject(
    val code: String,
    val name: String,
    val grade: PartialGradeItem?,
    val report: ReportCardItem?
)

fun mergeUnifiedSubjects(
    grades: List<PartialGradeItem>,
    report: ReportCardResponse
): List<UnifiedSubject> {
    val gradeMap = grades.associateBy { it.code }
    val reportMap = report.items.associateBy { it.code }
    val orderedCodes = (grades.map { it.code } + report.items.map { it.code }).distinct()
    return orderedCodes.map { code ->
        val g = gradeMap[code]
        val r = reportMap[code]
        UnifiedSubject(
            code = code,
            name = g?.name?.takeIf { it.isNotBlank() } ?: r?.name.orEmpty(),
            grade = g,
            report = r
        )
    }
}

fun ReportCardItem.isFrequencyAtLimit(): Boolean = hours > 0 && remainingAbsences <= 3

private fun UnifiedSubject.numericAverage(): Double? =
    grade?.average?.value ?: report?.finalAverageValue

private fun UnifiedSubject.isAtRisk(): Boolean {
    val freqRisk = report?.isFrequencyAtLimit() == true
    val avg = numericAverage()
    val gradeRisk = grade?.belowPassing == true || (avg != null && avg < 6.0)
    return freqRisk || gradeRisk
}

@Composable
fun SemesterScreen(
    dashboard: DashboardResponse?,
    periods: List<PeriodOption>,
    selectedPeriodId: String,
    grades: List<PartialGradeItem>,
    report: ReportCardResponse,
    unreadNotifications: List<NotificationItem>,
    selectedSubjectCode: String?,
    showRiskyAttendance: Boolean,
    onSelectPeriod: (String) -> Unit,
    onOpenSubject: (String) -> Unit,
    onCloseSubject: () -> Unit,
    onOpenRiskyAttendance: () -> Unit,
    onCloseRiskyAttendance: () -> Unit,
    onOpenNotifications: () -> Unit,
    freqAlertThreshold: Int = 80,
    loading: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    renderSheetsInPlace: Boolean = true
) {
    val style = LocalDesignStyle.current
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val appleInsets = LocalAppleContentInsets.current
    val haptics = LocalHapticFeedback.current
    var showExportSheet by remember { mutableStateOf(false) }

    val selectedIdx = periods.indexOfFirst { it.id == selectedPeriodId }.coerceAtLeast(0)

    val mergedSubjects = remember(grades, report.items) {
        mergeUnifiedSubjects(grades, report)
    }

    val riskySubjects = remember(mergedSubjects) {
        mergedSubjects
            .filter { it.report?.isFrequencyAtLimit() == true }
            .sortedBy { it.report?.remainingAbsences ?: Int.MAX_VALUE }
    }

    val studentFirstName = remember(dashboard?.student?.name) {
        dashboard?.student?.name
            ?.split(" ")
            ?.firstOrNull()
            ?.lowercase()
            ?.replaceFirstChar { it.uppercase() }
            ?: "Aluno"
    }

    if (style != DesignStyle.APPLE) {
        Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .appleScrollBoundaryHaptics(listState),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Olá, $studentFirstName",
                                color = colors.textPrimary,
                                style = MaterialTheme.typography.headlineLarge
                            )
                            if (dashboard != null) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = listOfNotNull(
                                        "RA ${dashboard.student.ra}",
                                        dashboard.student.campus.takeIf { it.isNotBlank() }
                                    ).joinToString(" • "),
                                    color = colors.textSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        if (mergedSubjects.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showExportSheet = true
                                }
                            ) {
                                Icon(
                                    imageVector = PortalIcons.Share,
                                    contentDescription = "Exportar resumo do semestre",
                                    tint = colors.accent
                                )
                            }
                        }
                    }
                }

                if (unreadNotifications.isNotEmpty()) {
                    item {
                        PortalCard(onClick = onOpenNotifications) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Novidades em Notas", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    val latest = unreadNotifications.first()
                                    Text(
                                        "${latest.disciplineName}: ${latest.field} → ${latest.newValue}",
                                        color = colors.textSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                PortalBadge("${unreadNotifications.size} nova(s)", colors.accent)
                            }
                        }
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PortalCard(modifier = Modifier.weight(1f), contentPadding = PaddingValues(14.dp)) {
                            Text("Matérias", color = colors.textSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${mergedSubjects.size}",
                                color = colors.textPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                style = tabularNumberStyle(MaterialTheme.typography.headlineSmall)
                            )
                        }
                        val hasRisky = riskySubjects.isNotEmpty()
                        val alertColor = when {
                            !hasRisky -> colors.green
                            riskySubjects.any { (it.report?.remainingAbsences ?: 1) <= 0 } -> colors.red
                            else -> colors.orange
                        }
                        PortalCard(
                            modifier = Modifier.weight(1f),
                            onClick = if (hasRisky) onOpenRiskyAttendance else null,
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Frequência", color = colors.textSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                                if (hasRisky) {
                                    Icon(
                                        imageVector = PortalIcons.ChevronRight,
                                        contentDescription = "Ver disciplinas no limite",
                                        tint = alertColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (!hasRisky) "Tranquila" else "${riskySubjects.size} no limite",
                                color = alertColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                if (periods.isNotEmpty()) {
                    item {
                        PortalSegmentedControl(
                            options = periods.take(4).map { shortenPeriod(it.label) },
                            selectedIndex = selectedIdx.coerceAtMost(3),
                            onSelect = { idx -> periods.getOrNull(idx)?.let { onSelectPeriod(it.id) } }
                        )
                    }
                }

                if (mergedSubjects.isEmpty()) {
                    item {
                        PortalCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Nenhuma disciplina encontrada para este período.",
                                color = colors.textSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    items(mergedSubjects, key = { it.code }) { subject ->
                        MaterialSubjectCard(
                            subject = subject,
                            onClick = { onOpenSubject(subject.code) }
                        )
                    }
                }

                if (report.semesterComplementaryHours.isNotBlank() || report.totalComplementaryHours.isNotBlank()) {
                    item {
                        PortalCard(modifier = Modifier.fillMaxWidth()) {
                            Text("Atividades Complementares", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            if (report.semesterComplementaryHours.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(report.semesterComplementaryHours, color = colors.textSecondary, fontSize = 12.sp)
                            }
                            if (report.totalComplementaryHours.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(report.totalComplementaryHours, color = colors.textSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }

                val notices = dashboard?.summary?.notices.orEmpty()
                if (notices.isNotEmpty()) {
                    item {
                        PortalSectionHeader("Avisos da Coordenação")
                        notices.forEach { notice ->
                            PortalCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                Text(notice, color = colors.textPrimary, fontSize = 13.sp, lineHeight = 19.sp)
                            }
                        }
                    }
                }
            }

            val activeSubject = mergedSubjects.firstOrNull { it.code == selectedSubjectCode }
            when {
                activeSubject != null -> MaterialSubjectDetailModal(
                    subject = activeSubject,
                    freqAlertThreshold = freqAlertThreshold,
                    onDismiss = onCloseSubject
                )
                showRiskyAttendance && riskySubjects.isNotEmpty() -> MaterialAttendanceLimitModal(
                    subjects = riskySubjects,
                    onSelectSubject = onOpenSubject,
                    onDismiss = onCloseRiskyAttendance
                )
                showExportSheet -> {
                    val periodLabel = periods.getOrNull(selectedIdx)?.label.orEmpty()
                    ExportActionModal(
                        fileName = "unicep_semestre_${selectedPeriodId.ifBlank { "atual" }}",
                        data = buildGradesExportData(
                            student = dashboard?.student ?: StudentInfo(),
                            periodLabel = periodLabel,
                            grades = grades,
                            reportItems = report.items
                        ),
                        onDismiss = { showExportSheet = false }
                    )
                }
            }
        }
        return
    }

    // Apple Liquid Glass SemesterScreen
    val orderedSubjects = remember(mergedSubjects) {
        mergedSubjects.sortedWith(
            compareByDescending<UnifiedSubject> { it.isAtRisk() }
                .thenBy { it.name.toDisplayTitle() }
        )
    }
    val overallAverage = remember(mergedSubjects) {
        val avgs = mergedSubjects.mapNotNull { it.numericAverage() }
        if (avgs.isEmpty()) null else avgs.average()
    }
    var periodMenuOpen by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(colors.background)) {
        val isWide = maxWidth >= 640.dp
        val horizontalPad = if (isWide) 24.dp else 16.dp
        val topPad = appleInsets.calculateTopPadding()
        val bottomPad = appleInsets.calculateBottomPadding()

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .appleScrollBoundaryHaptics(listState),
                contentPadding = PaddingValues(
                    start = horizontalPad,
                    end = horizontalPad,
                    top = topPad,
                    bottom = bottomPad
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Large Title + Seletor de período
                item(key = "header") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Semestre",
                                color = colors.textPrimary,
                                style = appleType.largeTitle
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (mergedSubjects.isNotEmpty()) {
                                    GlassSurface(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .pointerHoverIcon(PointerIcon.Hand)
                                            .clickable {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                showExportSheet = true
                                            },
                                        shape = CircleShape,
                                        shadowElevation = 8.dp
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = PortalIcons.Share,
                                                contentDescription = "Exportar resumo do semestre",
                                                tint = colors.accent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                if (periods.isNotEmpty()) {
                                    val currentLabel = periods.getOrNull(selectedIdx)?.label?.let { shortenPeriod(it) } ?: "Período"
                                    Box {
                                        GlassSurface(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .pointerHoverIcon(PointerIcon.Hand)
                                                .clickable { periodMenuOpen = true },
                                            shape = CircleShape,
                                            shadowElevation = 8.dp
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                Text(
                                                    text = currentLabel,
                                                    color = colors.accent,
                                                    style = appleType.subheadline,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Icon(
                                                    imageVector = PortalIcons.ChevronDown,
                                                    contentDescription = "Selecionar período",
                                                    tint = colors.accent,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        GlassMenu(
                                            expanded = periodMenuOpen,
                                            onDismiss = { periodMenuOpen = false },
                                            items = periods.map { p ->
                                                GlassMenuItem(
                                                    label = p.label,
                                                    selected = p.id == selectedPeriodId,
                                                    onClick = {
                                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        onSelectPeriod(p.id)
                                                    }
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Olá, $studentFirstName",
                            color = colors.textPrimary,
                            style = appleType.title3
                        )
                        if (dashboard != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = listOfNotNull(
                                    "RA ${dashboard.student.ra}",
                                    dashboard.student.campus.takeIf { it.isNotBlank() }
                                ).joinToString(" · "),
                                color = colors.textSecondary,
                                style = appleType.footnote
                            )
                        }
                    }
                }

                // Widgets de resumo
                item(key = "summary_widgets") {
                    val hasRisky = riskySubjects.isNotEmpty()
                    val alertColor = when {
                        !hasRisky -> colors.green
                        riskySubjects.any { (it.report?.remainingAbsences ?: 1) <= 0 } -> colors.red
                        else -> colors.orange
                    }
                    val avgColor = when {
                        overallAverage == null -> colors.textSecondary
                        overallAverage < 6.0 -> colors.red
                        else -> colors.green
                    }
                    val avgText = overallAverage?.let { formatOneDecimal(it) } ?: "—"
                    val pendingCount = mergedSubjects.count { it.numericAverage() == null }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SummaryWidgetCard(
                                modifier = Modifier.weight(1f),
                                icon = PortalIcons.Grades,
                                iconTint = colors.accent,
                                value = avgText,
                                valueColor = avgColor,
                                label = "Média geral"
                            )
                            SummaryWidgetCard(
                                modifier = Modifier.weight(1f),
                                icon = if (hasRisky) PortalIcons.Warning else PortalIcons.CheckCircle,
                                iconTint = alertColor,
                                value = if (!hasRisky) "Tranquila" else "${riskySubjects.size} no limite",
                                valueColor = alertColor,
                                label = "Frequência",
                                cardStyle = if (hasRisky) CardStyle.Tinted(alertColor) else CardStyle.Grouped,
                                showChevron = hasRisky,
                                onClick = if (hasRisky) onOpenRiskyAttendance else null
                            )
                            if (isWide) {
                                SummaryWidgetCard(
                                    modifier = Modifier.weight(1f),
                                    icon = PortalIcons.Book,
                                    iconTint = colors.indigo,
                                    value = "${mergedSubjects.size}",
                                    valueColor = colors.textPrimary,
                                    label = if (pendingCount > 0) "Disciplinas · $pendingCount sem nota" else "Disciplinas"
                                )
                            }
                        }
                        if (!isWide) {
                            PortalCard(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = PortalIcons.Book,
                                            contentDescription = null,
                                            tint = colors.indigo,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Disciplinas no período",
                                            color = colors.textPrimary,
                                            style = appleType.subheadline,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (pendingCount > 0) {
                                            Text(
                                                text = "$pendingCount sem nota",
                                                color = colors.textSecondary,
                                                style = appleType.footnote
                                            )
                                        }
                                        Text(
                                            text = "${mergedSubjects.size}",
                                            color = colors.textPrimary,
                                            style = tabularNumberStyle(appleType.headline)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Novidades em Notas
                if (unreadNotifications.isNotEmpty()) {
                    item(key = "unread_banner") {
                        val latest = unreadNotifications.first()
                        PortalCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onOpenNotifications,
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(colors.accent)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Novas notas",
                                        color = colors.textPrimary,
                                        style = appleType.headline
                                    )
                                    Text(
                                        text = "${latest.disciplineName.toDisplayTitle()} · ${latest.field} → ${latest.newValue}",
                                        color = colors.textSecondary,
                                        style = appleType.subheadline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                PortalBadge("${unreadNotifications.size}", colors.accent)
                                Spacer(Modifier.width(6.dp))
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

                // Seção Disciplinas
                item(key = "subjects_header") {
                    Spacer(Modifier.height(4.dp))
                    PortalSectionHeader("Disciplinas", prominent = true)
                }

                if (loading && mergedSubjects.isEmpty()) {
                    items(3) {
                        AppleSkeletonBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(116.dp)
                        )
                    }
                } else if (orderedSubjects.isEmpty()) {
                    item(key = "empty_subjects") {
                        AppleEmptyState(
                            icon = PortalIcons.Book,
                            title = "Nenhuma disciplina",
                            subtitle = "Não encontramos matérias lançadas para o período selecionado."
                        )
                    }
                } else {
                    items(orderedSubjects, key = { it.code }) { subject ->
                        AppleSubjectCard(
                            subject = subject,
                            onClick = { onOpenSubject(subject.code) }
                        )
                    }
                }

                // Atividades complementares
                if (report.semesterComplementaryHours.isNotBlank() || report.totalComplementaryHours.isNotBlank()) {
                    item(key = "complementary") {
                        Spacer(Modifier.height(6.dp))
                        PortalSectionHeader("Atividades complementares")
                        PortalCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            PortalListRow(
                                title = "Horas complementares",
                                subtitle = listOfNotNull(
                                    report.semesterComplementaryHours.takeIf { it.isNotBlank() },
                                    report.totalComplementaryHours.takeIf { it.isNotBlank() }
                                ).joinToString("\n"),
                                leadingIcon = PortalIcons.Sparkles,
                                leadingTint = colors.purple,
                                showChevron = false
                            )
                        }
                    }
                }

                // Avisos da Coordenação
                val notices = dashboard?.summary?.notices.orEmpty()
                if (notices.isNotEmpty()) {
                    item(key = "notices_header") {
                        Spacer(Modifier.height(6.dp))
                        PortalSectionHeader("Avisos da coordenação")
                    }
                    items(notices, key = { it.hashCode() }) { notice ->
                        ExpandableNoticeCard(notice = notice)
                    }
                }
            }
        }

        val activeSubject = mergedSubjects.firstOrNull { it.code == selectedSubjectCode }
        if (renderSheetsInPlace) {
            when {
                activeSubject != null -> AppleSubjectDetailSheet(
                    subject = activeSubject,
                    freqAlertThreshold = freqAlertThreshold,
                    onDismiss = onCloseSubject
                )
                showRiskyAttendance && riskySubjects.isNotEmpty() -> AppleAttendanceLimitSheet(
                    subjects = riskySubjects,
                    onSelectSubject = onOpenSubject,
                    onDismiss = onCloseRiskyAttendance
                )
            }
        }
        if (showExportSheet) {
            val periodLabel = periods.getOrNull(selectedIdx)?.label.orEmpty()
            ExportActionModal(
                fileName = "unicep_semestre_${selectedPeriodId.ifBlank { "atual" }}",
                data = buildGradesExportData(
                    student = dashboard?.student ?: StudentInfo(),
                    periodLabel = periodLabel,
                    grades = grades,
                    reportItems = report.items
                ),
                onDismiss = { showExportSheet = false }
            )
        }
    }
}

@Composable
private fun SummaryWidgetCard(
    icon: ImageVector,
    iconTint: Color,
    value: String,
    valueColor: Color,
    label: String,
    modifier: Modifier = Modifier,
    cardStyle: CardStyle = CardStyle.Grouped,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    PortalCard(
        modifier = modifier.heightIn(min = 98.dp),
        onClick = onClick,
        cardStyle = cardStyle,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            if (showChevron) {
                Icon(
                    imageVector = PortalIcons.ChevronRight,
                    contentDescription = "Ver detalhes",
                    tint = valueColor,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = value,
            color = valueColor,
            style = tabularNumberStyle(
                if (value.length <= 5) appleType.title1 else appleType.title3
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = colors.textSecondary,
            style = appleType.caption1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppleSubjectCard(
    subject: UnifiedSubject,
    onClick: () -> Unit
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val grade = subject.grade
    val report = subject.report

    val avgVal = subject.numericAverage()
    val avgText = grade?.average?.display?.takeIf { it.isNotBlank() }
        ?: report?.finalAverage?.takeIf { it.isNotBlank() }
        ?: "—"
    val belowPassing = grade?.belowPassing == true || (avgVal != null && avgVal < 6.0)
    val avgColor = when {
        avgText == "—" || avgVal == null -> colors.tertiaryLabel
        belowPassing -> colors.red
        else -> colors.green
    }
    val ringProgress = ((avgVal ?: 0.0) / 10.0).toFloat().coerceIn(0f, 1f)

    val allSlots = remember(grade) { buildEvalSlots(grade) }
    val filledSlots = remember(allSlots) { allSlots.filter { it.gradeDisplay.isNotBlank() } }

    val statusLabel = report?.statusLabel?.trim().orEmpty()
    val showStatus = statusLabel.isNotBlank() && !statusLabel.equals("Cursando", ignoreCase = true)
    val statusColor = when {
        statusLabel.contains("aprov", ignoreCase = true) -> colors.green
        statusLabel.contains("reprov", ignoreCase = true) -> colors.red
        else -> colors.orange
    }

    val freqRisk = report?.isFrequencyAtLimit() == true

    PortalCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        cardStyle = if (freqRisk) CardStyle.Tinted(if ((report?.remainingAbsences ?: 1) <= 0) colors.red else colors.orange) else CardStyle.Grouped,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = subject.name.toDisplayTitle(),
                    color = colors.textPrimary,
                    style = appleType.headline,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = listOfNotNull(
                            subject.code,
                            report?.classGroup?.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        color = colors.textSecondary,
                        style = appleType.footnote
                    )
                    if (showStatus) {
                        PortalStatusDot(text = statusLabel, color = statusColor, tintText = true)
                    }
                }
            }

            ProgressRing(
                progress = if (avgVal != null) ringProgress else 0f,
                color = avgColor,
                size = 50.dp,
                strokeWidth = 4.5.dp,
                trackColor = colors.systemFill
            ) {
                Text(
                    text = avgText,
                    color = if (avgVal == null) colors.textSecondary else colors.textPrimary,
                    style = tabularNumberStyle(appleType.subheadline),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        if (filledSlots.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filledSlots.forEach { slot ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.tertiaryGroupedBackground)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = slot.label,
                            color = colors.textSecondary,
                            style = appleType.caption1,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = slot.gradeDisplay,
                            color = colors.textPrimary,
                            style = tabularNumberStyle(appleType.subheadline),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            Text(
                text = "Sem notas lançadas",
                color = colors.tertiaryLabel,
                style = appleType.footnote
            )
        }

        if (report != null && report.maxAbsences > 0) {
            Spacer(Modifier.height(12.dp))
            AppleAttendanceBlock(report = report)
        }
    }
}

@Composable
private fun AppleAttendanceBlock(report: ReportCardItem) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val ratio = if (report.maxAbsences > 0) {
        (report.absences.toFloat() / report.maxAbsences.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val barColor = when {
        report.remainingAbsences <= 0 -> colors.red
        ratio >= 0.75f || report.remainingAbsences <= 3 -> colors.orange
        else -> colors.green
    }

    AppleProgressBar(progress = ratio, color = barColor, height = 4.dp)
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${report.absences} de ${report.maxAbsences} faltas" +
                (report.freqPercent?.let { " · ${it.toInt()}% freq." } ?: ""),
            color = colors.textSecondary,
            style = tabularNumberStyle(appleType.footnote)
        )
        Text(
            text = when {
                report.remainingAbsences < 0 -> "Excedeu ${-report.remainingAbsences}"
                report.remainingAbsences == 0 -> "Sem margem"
                report.remainingAbsences == 1 -> "Resta 1"
                else -> "Restam ${report.remainingAbsences}"
            },
            color = if (report.remainingAbsences <= 3) barColor else colors.textSecondary,
            style = tabularNumberStyle(appleType.footnote),
            fontWeight = if (report.remainingAbsences <= 3) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun ExpandableNoticeCard(notice: String) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    var expanded by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }

    PortalCard(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        onClick = if (overflow || expanded) ({ expanded = !expanded }) else null
    ) {
        Text(
            text = notice,
            color = colors.textPrimary,
            style = appleType.subheadline,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!expanded && result.hasVisualOverflow) {
                    overflow = true
                }
            }
        )
        if (overflow || expanded) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (expanded) "Mostrar menos" else "Mais",
                color = colors.accent,
                style = appleType.footnote,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

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
    freqAlertThreshold: Int = 80
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
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

    AppleSheet(
        title = subject.name.toDisplayTitle(),
        subtitle = listOfNotNull(
            subject.code,
            report?.classGroup?.takeIf { it.isNotBlank() }
        ).joinToString(" · "),
        onDismiss = onDismiss
    ) {
        // Hero com ProgressRing 88dp
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
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
                Text(
                    text = sim,
                    color = colors.textPrimary,
                    style = appleType.subheadline
                )
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
            if (report.hours > 0) {
                Spacer(Modifier.height(14.dp))
                AttendanceSimulatorSection(
                    report = report,
                    thresholdPercent = freqAlertThreshold,
                    isApple = true
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

// --- Material 3 Preserved Components ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaterialSubjectCard(
    subject: UnifiedSubject,
    onClick: () -> Unit
) {
    val colors = LocalPortalColors.current
    val grade = subject.grade
    val report = subject.report

    val avgText = grade?.average?.display?.takeIf { it.isNotBlank() }
        ?: report?.finalAverage?.takeIf { it.isNotBlank() }
        ?: "—"
    val belowPassing = grade?.belowPassing == true ||
        (report?.finalAverageValue != null && report.finalAverageValue < 6.0)
    val avgColor = when {
        avgText == "—" -> colors.textSecondary
        belowPassing -> colors.red
        else -> colors.green
    }

    val slots = remember(grade) { buildEvalSlots(grade) }

    PortalCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Text(
                    text = subject.name,
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = listOfNotNull(
                        subject.code,
                        report?.classGroup?.takeIf { it.isNotBlank() },
                        report?.statusLabel?.takeIf { it.isNotBlank() }
                    ).joinToString(" • "),
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }
            PortalBadge("Média $avgText", avgColor)
        }

        if (slots.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                slots.forEach { slot ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceSecondary.copy(alpha = 0.65f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = slot.label,
                            color = colors.textSecondary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = slot.gradeDisplay.ifBlank { "—" },
                            color = if (slot.gradeDisplay.isBlank()) colors.textSecondary else colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            style = tabularNumberStyle(MaterialTheme.typography.titleMedium)
                        )
                    }
                }
            }
        }

        if (report != null && report.maxAbsences > 0) {
            Spacer(Modifier.height(12.dp))
            MaterialAttendanceProgressBlock(report = report)
        }
    }
}

@Composable
private fun MaterialAttendanceProgressBlock(report: ReportCardItem) {
    val colors = LocalPortalColors.current
    val ratio = (report.absences.toFloat() / report.maxAbsences.toFloat()).coerceIn(0f, 1f)
    val barColor = when {
        report.remainingAbsences <= 0 -> colors.red
        ratio >= 0.75f || report.remainingAbsences <= 3 -> colors.orange
        else -> colors.green
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Faltas: ${report.absences}/${report.maxAbsences}" +
                (report.freqPercent?.let { " • ${it.toInt()}% freq." } ?: ""),
            color = colors.textSecondary,
            fontSize = 12.sp
        )
        Text(
            text = when {
                report.remainingAbsences < 0 -> "Excedeu ${-report.remainingAbsences}"
                report.remainingAbsences == 0 -> "Sem margem"
                else -> "Restam ${report.remainingAbsences}"
            },
            color = if (report.remainingAbsences <= 3) barColor else colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
    Spacer(Modifier.height(5.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(CircleShape)
            .background(colors.surfaceSecondary)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(ratio)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(barColor)
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
    Box(
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
        Surface(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .heightIn(max = 640.dp)
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
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(title, color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        if (!subtitle.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(subtitle, color = colors.textSecondary, fontSize = 12.sp)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(PortalIcons.Close, contentDescription = "Fechar", tint = colors.textSecondary)
                    }
                }
                Spacer(Modifier.height(14.dp))
                PortalDivider()
                Spacer(Modifier.height(14.dp))
                content()
            }
        }
    }
}

@Composable
private fun MaterialAttendanceLimitModal(
    subjects: List<UnifiedSubject>,
    onSelectSubject: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalPortalColors.current
    val countLabel = if (subjects.size == 1) "1 disciplina" else "${subjects.size} disciplinas"
    PortalOverlayModal(
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
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(subject.name, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = listOfNotNull(
                                    subject.code,
                                    report.classGroup.takeIf { it.isNotBlank() },
                                    "${report.hours}h"
                                ).joinToString(" • "),
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                        PortalBadge(badgeText, badgeColor)
                    }
                    if (report.maxAbsences > 0) {
                        Spacer(Modifier.height(10.dp))
                        MaterialAttendanceProgressBlock(report = report)
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Faltas: ${report.absences}" + (report.freqPercent?.let { " • ${it.toInt()}% freq." } ?: ""),
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                    if (report.updatedAt.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Atualizado em: ${report.updatedAt}", color = colors.textSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaterialSubjectDetailModal(
    subject: UnifiedSubject,
    onDismiss: () -> Unit,
    freqAlertThreshold: Int = 80
) {
    val colors = LocalPortalColors.current
    val grade = subject.grade
    val report = subject.report
    val slots = remember(grade) { buildEvalSlots(grade) }
    val sim = remember(slots) { computeNeededGrade(slots, targetAvg = 6.0) }

    PortalOverlayModal(
        title = subject.name,
        subtitle = listOfNotNull(
            subject.code,
            report?.classGroup?.takeIf { it.isNotBlank() },
            report?.statusLabel?.takeIf { it.isNotBlank() }
        ).joinToString(" • "),
        onDismiss = onDismiss
    ) {
        Text("Avaliações e Pesos", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (slots.isEmpty()) {
            Text("Aguardando lançamento de notas ou pesos nesta disciplina.", color = colors.textSecondary, fontSize = 13.sp)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                slots.forEach { slot ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceSecondary.copy(alpha = 0.72f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (slot.weightDisplay.isNotBlank()) "${slot.label} (${slot.weightDisplay}%)" else slot.label,
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = slot.gradeDisplay.ifBlank { "—" },
                            color = if (slot.gradeDisplay.isBlank()) colors.textSecondary else colors.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            style = tabularNumberStyle(MaterialTheme.typography.titleMedium)
                        )
                    }
                }
            }
        }

        if (sim != null) {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.accentSoft)
                    .padding(12.dp)
            ) {
                Column {
                    Text("Simulador Automático (Meta 6,0)", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(sim, color = colors.textPrimary, fontSize = 13.sp)
                }
            }
        }

        if (report != null) {
            Spacer(Modifier.height(16.dp))
            Text("Frequência e Boletim", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailMetric("Média Final", report.finalAverage.ifBlank { "—" })
                DetailMetric("Carga Horária", "${report.hours}h")
                DetailMetric("Faltas", "${report.absences}/${if (report.maxAbsences > 0) report.maxAbsences else "—"}")
                DetailMetric("Frequência", report.freqPercent?.let { "${it.toInt()}%" } ?: "—")
            }
            if (report.updatedAt.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Faltas atualizadas em: ${report.updatedAt}", color = colors.textSecondary, fontSize = 12.sp)
            }
            if (report.hours > 0) {
                Spacer(Modifier.height(14.dp))
                AttendanceSimulatorSection(
                    report = report,
                    thresholdPercent = freqAlertThreshold,
                    isApple = false
                )
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

@Composable
private fun DetailMetric(label: String, value: String) {
    val colors = LocalPortalColors.current
    Column {
        Text(label, color = colors.textSecondary, fontSize = 11.sp)
        Text(
            text = value,
            color = colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            style = tabularNumberStyle(MaterialTheme.typography.titleMedium)
        )
    }
}

private data class EvalSlot(
    val label: String,
    val gradeDisplay: String,
    val gradeValue: Double?,
    val weightDisplay: String,
    val weightValue: Double?
)

private fun buildEvalSlots(item: PartialGradeItem?): List<EvalSlot> {
    if (item == null) return emptyList()
    return buildList {
        for (i in 0 until 4) {
            val g = item.exams.getOrNull(i)
            val w = item.examWeights.getOrNull(i)
            if (!g?.display.isNullOrBlank() || !w?.display.isNullOrBlank()) {
                add(EvalSlot("P${i + 1}", g?.display.orEmpty(), g?.value, w?.display.orEmpty(), w?.value))
            }
        }
        for (i in 0 until 4) {
            val g = item.works.getOrNull(i)
            val w = item.workWeights.getOrNull(i)
            if (!g?.display.isNullOrBlank() || !w?.display.isNullOrBlank()) {
                add(EvalSlot("T${i + 1}", g?.display.orEmpty(), g?.value, w?.display.orEmpty(), w?.value))
            }
        }
    }
}

private fun computeNeededGrade(slots: List<EvalSlot>, targetAvg: Double): String? {
    val weightedSlots = slots.filter { (it.weightValue ?: 0.0) > 0.0 }
    if (weightedSlots.isEmpty()) return null

    val totalWeight = weightedSlots.sumOf { it.weightValue ?: 0.0 }
    val earnedPoints = weightedSlots.sumOf { (it.gradeValue ?: 0.0) * (it.weightValue ?: 0.0) }
    val missing = weightedSlots.filter { it.gradeValue == null }
    if (missing.isEmpty()) return null

    val remainingWeight = missing.sumOf { it.weightValue ?: 0.0 }
    val needed = ((targetAvg * totalWeight) - earnedPoints) / remainingWeight
    val rounded = ((needed * 100.0).roundToInt() / 100.0)
    val formatted = rounded.toString().replace('.', ',')
    val targetFmt = formatOneDecimal(targetAvg)
    val labels = missing.joinToString(", ") { it.label }

    return when {
        needed <= 0.0 -> "Você já garantiu média ≥ $targetFmt mesmo tirando 0 em $labels."
        needed > 10.0 -> "Precisaria de $formatted em $labels (acima de 10,0 com os pesos atuais)."
        else -> "Precisa tirar em média $formatted nas avaliações restantes ($labels) para fechar com $targetFmt."
    }
}

private fun formatOneDecimal(v: Double): String {
    val tenths = (v * 10.0).roundToInt()
    val whole = tenths / 10
    val frac = kotlin.math.abs(tenths % 10)
    return "$whole,$frac"
}

internal fun shortenPeriod(label: String): String {
    return label
        .replace("semestre de ", "")
        .replace("º ", "/")
        .trim()
}
