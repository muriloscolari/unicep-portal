package me.muriloscolari.unicep.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import me.muriloscolari.unicep.data.api.ScheduleResponse
import me.muriloscolari.unicep.data.api.StudentInfo
import me.muriloscolari.unicep.data.api.VirtualAssignmentItem
import me.muriloscolari.unicep.data.api.VirtualCourseDetailResponse
import me.muriloscolari.unicep.data.api.VirtualOverviewResponse
import me.muriloscolari.unicep.data.repo.SubjectVirtualLink
import me.muriloscolari.unicep.data.repo.buildSubjectVirtualLinks
import me.muriloscolari.unicep.data.repo.buildTodaySummary
import me.muriloscolari.unicep.data.repo.collectAllPendingVirtualAssignments
import me.muriloscolari.unicep.platform.DesignStyle
import me.muriloscolari.unicep.platform.PlatformBridge
import me.muriloscolari.unicep.platform.PlatformVerticalScrollbar
import me.muriloscolari.unicep.platform.buildGradesExportData
import me.muriloscolari.unicep.ui.designsystem.AppleEmptyState
import me.muriloscolari.unicep.ui.designsystem.AppleProgressBar
import me.muriloscolari.unicep.ui.designsystem.AppleSkeletonBox
import me.muriloscolari.unicep.ui.designsystem.CardStyle
import me.muriloscolari.unicep.ui.designsystem.CopyTextButton
import me.muriloscolari.unicep.ui.designsystem.ExportActionModal
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
import me.muriloscolari.unicep.ui.designsystem.PortalStatusDot
import me.muriloscolari.unicep.ui.designsystem.PortalTextButton
import me.muriloscolari.unicep.ui.designsystem.ProgressRing
import me.muriloscolari.unicep.ui.designsystem.SelectableRichText
import me.muriloscolari.unicep.ui.designsystem.appleScrollBoundaryHaptics
import me.muriloscolari.unicep.ui.designsystem.tabularNumberStyle
import me.muriloscolari.unicep.ui.util.toDisplayTitle

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
    schedule: ScheduleResponse? = null,
    virtualOverview: VirtualOverviewResponse? = null,
    virtualCourseDetails: Map<Long, VirtualCourseDetailResponse> = emptyMap(),
    onOpenSearch: () -> Unit = {},
    onOpenSchedule: () -> Unit = {},
    onOpenVirtualAssignment: (VirtualAssignmentItem) -> Unit = {},
    onOpenVirtualCourse: (Long) -> Unit = {},
    onOpenVirtualTab: () -> Unit = {},
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

    val subjectVirtualLinks = remember(mergedSubjects, virtualOverview, virtualCourseDetails) {
        buildSubjectVirtualLinks(mergedSubjects, virtualOverview, virtualCourseDetails)
    }

    val pendingVirtualAssignments = remember(virtualOverview, virtualCourseDetails) {
        collectAllPendingVirtualAssignments(virtualOverview, virtualCourseDetails)
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

    val isApple = style == DesignStyle.APPLE
    val orderedSubjects = remember(mergedSubjects, subjectVirtualLinks) {
        mergedSubjects.sortedWith(
            compareByDescending<UnifiedSubject> { it.isAtRisk() }
                .thenByDescending { subjectVirtualLinks[it.code]?.pendingAssignments?.isNotEmpty() == true }
                .thenBy { it.name.toDisplayTitle() }
        )
    }
    val overallAverage = remember(mergedSubjects) {
        val avgs = mergedSubjects.mapNotNull { it.numericAverage() }
        if (avgs.isEmpty()) null else avgs.average()
    }
    val todaySummary = remember(schedule, mergedSubjects, virtualOverview) {
        buildTodaySummary(
            schedule = schedule,
            subjects = mergedSubjects,
            virtualOverview = virtualOverview,
            nowLocal = PlatformBridge.currentLocalDateTime(),
            nowMillis = PlatformBridge.currentTimeMillis()
        )
    }
    var periodMenuOpen by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(colors.background)) {
        val isWide = maxWidth >= 640.dp
        val isWideGrid = maxWidth >= 880.dp
        val horizontalPad = if (isWide) 24.dp else 16.dp
        val topPad = if (isApple) appleInsets.calculateTopPadding() else 12.dp
        val bottomPad = if (isApple) appleInsets.calculateBottomPadding() else 24.dp

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 1040.dp)
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
                                style = if (isApple) appleType.largeTitle else MaterialTheme.typography.headlineLarge
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SemesterHeaderIconButton(
                                    isApple = isApple,
                                    icon = PortalIcons.Search,
                                    contentDescription = "Busca global",
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onOpenSearch()
                                    }
                                )
                                if (mergedSubjects.isNotEmpty()) {
                                    SemesterHeaderIconButton(
                                        isApple = isApple,
                                        icon = PortalIcons.Share,
                                        contentDescription = "Exportar resumo do semestre",
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            showExportSheet = true
                                        }
                                    )
                                }
                                if (periods.isNotEmpty()) {
                                    val currentLabel = periods.getOrNull(selectedIdx)?.label?.let { shortenPeriod(it) } ?: "Período"
                                    Box {
                                        if (isApple) {
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
                                                    Text(currentLabel, color = colors.accent, style = appleType.subheadline, fontWeight = FontWeight.SemiBold)
                                                    Icon(PortalIcons.ChevronDown, "Selecionar período", tint = colors.accent, modifier = Modifier.size(14.dp))
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
                                        } else {
                                            Surface(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .pointerHoverIcon(PointerIcon.Hand)
                                                    .clickable { periodMenuOpen = true },
                                                shape = CircleShape,
                                                color = colors.accentSoft
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(currentLabel, color = colors.accent, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                                    Icon(PortalIcons.ChevronDown, "Selecionar período", tint = colors.accent, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = periodMenuOpen,
                                                onDismissRequest = { periodMenuOpen = false },
                                                containerColor = colors.surface
                                            ) {
                                                periods.forEach { p ->
                                                    val isSel = p.id == selectedPeriodId
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                text = p.label,
                                                                color = if (isSel) colors.accent else colors.textPrimary,
                                                                fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal
                                                            )
                                                        },
                                                        onClick = {
                                                            periodMenuOpen = false
                                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            onSelectPeriod(p.id)
                                                        }
                                                    )
                                                }
                                            }
                                        }
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
                            SelectionContainer {
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
                }

                // Painel Hoje na UNICEP
                item(key = "today_overview") {
                    TodayOverviewCard(
                        summary = todaySummary,
                        scheduleLoaded = schedule != null,
                        virtualLoaded = virtualOverview != null,
                        onOpenSubject = onOpenSubject,
                        onOpenSchedule = onOpenSchedule,
                        onOpenVirtualAssignment = onOpenVirtualAssignment
                    )
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
                    val pendingGradesCount = mergedSubjects.count { it.numericAverage() == null }

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
                                    label = if (pendingGradesCount > 0) "Disciplinas · $pendingGradesCount sem nota" else "Disciplinas"
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
                                        if (pendingGradesCount > 0) {
                                            Text(
                                                text = "$pendingGradesCount sem nota",
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

                // Atividades pendentes da Sala Virtual integradas na tela principal
                if (pendingVirtualAssignments.isNotEmpty()) {
                    item(key = "pending_virtual_assignments") {
                        PendingVirtualAssignmentsCard(
                            assignments = pendingVirtualAssignments,
                            onOpenAssignment = onOpenVirtualAssignment,
                            onOpenVirtualTab = onOpenVirtualTab
                        )
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
                } else if (isWideGrid) {
                    val subjectPairs = orderedSubjects.chunked(2)
                    items(subjectPairs, key = { pair -> pair.joinToString("-") { it.code } }) { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                AppleSubjectCard(
                                    subject = pair[0],
                                    virtualLink = subjectVirtualLinks[pair[0].code],
                                    onClick = { onOpenSubject(pair[0].code) }
                                )
                            }
                            if (pair.size > 1) {
                                Box(modifier = Modifier.weight(1f)) {
                                    AppleSubjectCard(
                                        subject = pair[1],
                                        virtualLink = subjectVirtualLinks[pair[1].code],
                                        onClick = { onOpenSubject(pair[1].code) }
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    items(orderedSubjects, key = { it.code }) { subject ->
                        AppleSubjectCard(
                            subject = subject,
                            virtualLink = subjectVirtualLinks[subject.code],
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
                            SelectionContainer {
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
            PlatformVerticalScrollbar(
                listState = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp, horizontal = 3.dp)
            )
        }

        val activeSubject = mergedSubjects.firstOrNull { it.code == selectedSubjectCode }
        if (renderSheetsInPlace) {
            when {
                activeSubject != null -> AppleSubjectDetailSheet(
                    subject = activeSubject,
                    freqAlertThreshold = freqAlertThreshold,
                    virtualLink = subjectVirtualLinks[activeSubject.code],
                    onOpenVirtualCourse = { ou ->
                        onCloseSubject()
                        onOpenVirtualCourse(ou)
                    },
                    onOpenVirtualAssignment = { item ->
                        onCloseSubject()
                        onOpenVirtualAssignment(item)
                    },
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
private fun SemesterHeaderIconButton(
    isApple: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val colors = LocalPortalColors.current
    if (isApple) {
        GlassSurface(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onClick),
            shape = CircleShape,
            shadowElevation = 8.dp
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    } else {
        Surface(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onClick),
            shape = CircleShape,
            color = colors.surfaceSecondary
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = colors.accent,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun PendingVirtualAssignmentsCard(
    assignments: List<VirtualAssignmentItem>,
    onOpenAssignment: (VirtualAssignmentItem) -> Unit,
    onOpenVirtualTab: () -> Unit
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val haptics = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }
    val visibleItems = if (expanded) assignments else assignments.take(4)

    PortalCard(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        cardStyle = CardStyle.Grouped,
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    tint = colors.orange,
                    modifier = Modifier.size(17.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Atividades a entregar",
                        color = colors.textPrimary,
                        style = appleType.headline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Pendências da Sala Virtual",
                        color = colors.textSecondary,
                        style = appleType.caption1,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PortalBadge(
                    text = if (assignments.size == 1) "1 pendente" else "${assignments.size} pendentes",
                    color = colors.orange
                )
                Text(
                    text = "Virtual",
                    color = colors.accent,
                    style = appleType.footnote,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenVirtualTab()
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        PortalDivider()

        visibleItems.forEachIndexed { idx, item ->
            if (idx > 0) PortalDivider(Modifier.padding(start = 16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenAssignment(item)
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                    val meta = listOfNotNull(
                        item.orgUnitName.takeIf { it.isNotBlank() }?.toDisplayTitle(),
                        dueLabel.takeIf { it.isNotBlank() }?.let { "Até $it" }
                    ).joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            color = colors.textSecondary,
                            style = appleType.caption1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    imageVector = PortalIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.tertiaryLabel,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        if (assignments.size > 4) {
            PortalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        expanded = !expanded
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "Mostrar menos" else "Ver todas (${assignments.size})",
                    color = colors.accent,
                    style = appleType.footnote,
                    fontWeight = FontWeight.SemiBold
                )
            }
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
    virtualLink: SubjectVirtualLink? = null,
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
    val pendingAssignments = virtualLink?.pendingAssignments.orEmpty()

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

        if (pendingAssignments.isNotEmpty()) {
            val nextAssignment = pendingAssignments.first()
            val nextDue = formatIsoDateTime(
                nextAssignment.dueDate.ifBlank { nextAssignment.endDate },
                nextAssignment.dueAtMillis
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.orange.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(
                    imageVector = PortalIcons.Virtual,
                    contentDescription = null,
                    tint = colors.orange,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = buildString {
                        append(
                            if (pendingAssignments.size == 1) "1 atividade pendente"
                            else "${pendingAssignments.size} atividades pendentes"
                        )
                        if (nextDue.isNotBlank()) {
                            append(" · Próx. $nextDue")
                        }
                    },
                    color = colors.orange,
                    style = appleType.caption1,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun AppleAttendanceBlock(report: ReportCardItem) {
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
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        SelectableRichText(
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
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (overflow || expanded) {
                PortalTextButton(
                    text = if (expanded) "Mostrar menos" else "Mais",
                    onClick = { expanded = !expanded }
                )
            } else {
                Spacer(Modifier.width(1.dp))
            }
            CopyTextButton(textToCopy = notice)
        }
    }
}
