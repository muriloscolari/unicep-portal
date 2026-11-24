package me.muriloscolari.unicep.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import me.muriloscolari.unicep.data.api.VirtualAnnouncement
import me.muriloscolari.unicep.data.api.VirtualAssignmentItem
import me.muriloscolari.unicep.data.api.VirtualCourseDetailResponse
import me.muriloscolari.unicep.data.api.VirtualCourseSummary
import me.muriloscolari.unicep.data.api.VirtualOverviewResponse
import me.muriloscolari.unicep.data.repo.VirtualDownloadRequest
import me.muriloscolari.unicep.platform.DesignStyle
import me.muriloscolari.unicep.platform.PlatformBridge
import me.muriloscolari.unicep.platform.PlatformVerticalScrollbar
import me.muriloscolari.unicep.platform.PortalHapticType
import me.muriloscolari.unicep.ui.designsystem.AppleEmptyState
import me.muriloscolari.unicep.ui.designsystem.AppleSkeletonBox
import me.muriloscolari.unicep.ui.designsystem.LocalAppleContentInsets
import me.muriloscolari.unicep.ui.designsystem.LocalAppleTypography
import me.muriloscolari.unicep.ui.designsystem.LocalDesignStyle
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.ui.designsystem.PortalBadge
import me.muriloscolari.unicep.ui.designsystem.PortalCard
import me.muriloscolari.unicep.ui.designsystem.PortalDivider
import me.muriloscolari.unicep.ui.designsystem.PortalIcons
import me.muriloscolari.unicep.ui.designsystem.PortalPrimaryButton
import me.muriloscolari.unicep.ui.designsystem.PortalSearchField
import me.muriloscolari.unicep.ui.designsystem.PortalSecondaryButton
import me.muriloscolari.unicep.ui.designsystem.PortalSectionHeader
import me.muriloscolari.unicep.ui.designsystem.PortalSegmentedControl
import me.muriloscolari.unicep.ui.designsystem.appleScrollBoundaryHaptics
import me.muriloscolari.unicep.ui.designsystem.tabularNumberStyle
import me.muriloscolari.unicep.ui.util.toDisplayTitle

@Composable
fun VirtualScreen(
    overview: VirtualOverviewResponse?,
    courseDetails: Map<Long, VirtualCourseDetailResponse>,
    selectedCourseId: Long?,
    courseSubTab: Int,
    overviewTab: Int = 0,
    onSelectOverviewTab: (Int) -> Unit = {},
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    selectedSemester: String = "",
    onSelectSemester: (String) -> Unit = {},
    assignmentFilterIndex: Int = 0,
    onSelectAssignmentFilter: (Int) -> Unit = {},
    loading: Boolean,
    error: String?,
    onRefreshOverview: () -> Unit,
    onSelectCourse: (Long) -> Unit,
    onBackToOverview: () -> Unit,
    onSelectCourseSubTab: (Int) -> Unit,
    onRefreshCourse: (Long) -> Unit,
    onSelectAssignment: (VirtualAssignmentItem) -> Unit,
    onSelectAnnouncement: (VirtualAnnouncement) -> Unit,
    onViewD2LFile: (path: String, title: String, courseName: String, fileName: String) -> Unit,
    onDownloadD2LFile: (path: String, fileName: String, courseName: String) -> Unit,
    onDownloadBatch: (List<VirtualDownloadRequest>) -> Unit,
    onOpenWeb: (String) -> Unit,
    downloadFolderLabel: String = PlatformBridge.getDownloadFolderLabel(),
    onPickDownloadFolder: () -> Unit = {},
    downloadingFileKey: String? = null,
    batchDownloadDone: Int = 0,
    batchDownloadTotal: Int = 0,
    listState: LazyListState = rememberLazyListState(),
    selectedAssignment: VirtualAssignmentItem? = null,
    selectedAnnouncement: VirtualAnnouncement? = null,
    onDismissAssignment: () -> Unit = {},
    onDismissAnnouncement: () -> Unit = {},
    renderSheetsInPlace: Boolean = true
) {
    val style = LocalDesignStyle.current
    val colors = LocalPortalColors.current
    val appleInsets = LocalAppleContentInsets.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(colors.background)) {
        val isWide = maxWidth >= 640.dp
        val isWideGrid = maxWidth >= 880.dp
        val horizontalPad = if (isWide) 24.dp else 16.dp
        val contentPadding = if (style == DesignStyle.APPLE) {
            PaddingValues(
                start = horizontalPad,
                end = horizontalPad,
                top = appleInsets.calculateTopPadding() + 8.dp,
                bottom = appleInsets.calculateBottomPadding() + 24.dp
            )
        } else {
            PaddingValues(start = horizontalPad, end = horizontalPad, top = 12.dp, bottom = 24.dp)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 1040.dp).fillMaxSize()) {
                if (selectedCourseId != null) {
                    val detail = courseDetails[selectedCourseId]
                    val fallbackCourse = overview?.courses?.firstOrNull { it.orgUnitId == selectedCourseId }
                    VirtualCourseDetailBody(
                        courseId = selectedCourseId,
                        fallbackCourse = fallbackCourse,
                        detail = detail,
                        overviewBaseUrl = overview?.baseUrl.orEmpty(),
                        courseSubTab = courseSubTab,
                        loading = loading,
                        error = error,
                        contentPadding = contentPadding,
                        listState = listState,
                        downloadingFileKey = downloadingFileKey,
                        batchDownloadDone = batchDownloadDone,
                        batchDownloadTotal = batchDownloadTotal,
                        downloadFolderLabel = downloadFolderLabel,
                        onPickDownloadFolder = onPickDownloadFolder,
                        onBack = onBackToOverview,
                        onSelectSubTab = onSelectCourseSubTab,
                        onRefreshCourse = { onRefreshCourse(selectedCourseId) },
                        onSelectAssignment = onSelectAssignment,
                        onSelectAnnouncement = onSelectAnnouncement,
                        onViewD2LFile = onViewD2LFile,
                        onDownloadD2LFile = onDownloadD2LFile,
                        onDownloadBatch = onDownloadBatch,
                        onOpenWeb = onOpenWeb
                    )
                } else {
                    VirtualOverviewBody(
                        overview = overview,
                        activeTab = overviewTab,
                        onSelectTab = onSelectOverviewTab,
                        searchQuery = searchQuery,
                        onSearchQueryChange = onSearchQueryChange,
                        selectedSemester = selectedSemester,
                        onSelectSemester = onSelectSemester,
                        assignmentFilterIndex = assignmentFilterIndex,
                        onSelectAssignmentFilter = onSelectAssignmentFilter,
                        loading = loading,
                        error = error,
                        contentPadding = contentPadding,
                        listState = listState,
                        isWideGrid = isWideGrid,
                        downloadFolderLabel = downloadFolderLabel,
                        onPickDownloadFolder = onPickDownloadFolder,
                        onRefresh = onRefreshOverview,
                        onSelectCourse = onSelectCourse,
                        onSelectAssignment = onSelectAssignment,
                        onSelectAnnouncement = onSelectAnnouncement,
                        onOpenWeb = onOpenWeb
                    )
                }
            }

            if (selectedCourseId == null) {
                PlatformVerticalScrollbar(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 8.dp, horizontal = 3.dp)
                )
            }

            if (renderSheetsInPlace) {
                selectedAssignment?.let { assignment ->
                    VirtualAssignmentSheet(
                        assignment = assignment,
                        onViewD2LFile = onViewD2LFile,
                        onDownloadD2LFile = onDownloadD2LFile,
                        onDownloadBatch = onDownloadBatch,
                        downloadingFileKey = downloadingFileKey,
                        onOpenWeb = onOpenWeb,
                        onDismiss = onDismissAssignment
                    )
                }
                selectedAnnouncement?.let { announcement ->
                    VirtualAnnouncementSheet(
                        announcement = announcement,
                        onViewD2LFile = onViewD2LFile,
                        onDownloadD2LFile = onDownloadD2LFile,
                        onDownloadBatch = onDownloadBatch,
                        downloadingFileKey = downloadingFileKey,
                        onDismiss = onDismissAnnouncement
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VirtualOverviewBody(
    overview: VirtualOverviewResponse?,
    activeTab: Int,
    onSelectTab: (Int) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedSemester: String,
    onSelectSemester: (String) -> Unit,
    assignmentFilterIndex: Int,
    onSelectAssignmentFilter: (Int) -> Unit,
    loading: Boolean,
    error: String?,
    contentPadding: PaddingValues,
    listState: LazyListState,
    isWideGrid: Boolean,
    downloadFolderLabel: String,
    onPickDownloadFolder: () -> Unit,
    onRefresh: () -> Unit,
    onSelectCourse: (Long) -> Unit,
    onSelectAssignment: (VirtualAssignmentItem) -> Unit,
    onSelectAnnouncement: (VirtualAnnouncement) -> Unit,
    onOpenWeb: (String) -> Unit
) {
    val style = LocalDesignStyle.current
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val haptics = LocalHapticFeedback.current
    val assignmentFilter = AssignmentFilter.entries.getOrElse(assignmentFilterIndex) { AssignmentFilter.ALL }
    val virtualHomeUrl = "${overview?.baseUrl?.ifBlank { null }?.trimEnd('/') ?: "https://salavirtual.unicep.edu.br"}/d2l/home"

    val semesters = remember(overview?.semesters, overview?.courses) {
        val fromApi = overview?.semesters.orEmpty().map { it.name }.filter { it.isNotBlank() }
        val fromCourses = overview?.courses.orEmpty().map { it.semesterName }.filter { it.isNotBlank() }
        (fromApi + fromCourses).distinct().sortedDescending()
    }

    val filteredCourses = remember(overview?.courses, searchQuery, selectedSemester) {
        overview?.courses.orEmpty().filter { c ->
            val matchesSem = selectedSemester.isBlank() || c.semesterName == selectedSemester
            val q = searchQuery.trim()
            val matchesQuery = q.isBlank() ||
                c.name.contains(q, ignoreCase = true) ||
                c.code.contains(q, ignoreCase = true)
            matchesSem && matchesQuery
        }
    }

    val filteredAssignments = remember(overview?.upcomingAssignments, assignmentFilter) {
        val all = overview?.upcomingAssignments.orEmpty()
        when (assignmentFilter) {
            AssignmentFilter.ALL -> all
            AssignmentFilter.PENDING -> all.filter { it.status == "not_submitted" }
            AssignmentFilter.GRADED -> all.filter { it.status == "graded" }
        }
    }

    val pendingTotal = remember(overview?.upcomingAssignments) {
        overview?.upcomingAssignments?.count { it.status == "not_submitted" } ?: 0
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .appleScrollBoundaryHaptics(listState),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sala Virtual",
                        color = colors.textPrimary,
                        style = if (style == DesignStyle.APPLE) appleType.largeTitle else MaterialTheme.typography.headlineLarge
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "UnicepVirtual · Consulta passiva (somente leitura)",
                        color = colors.textSecondary,
                        style = if (style == DesignStyle.APPLE) appleType.footnote else MaterialTheme.typography.bodySmall
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onRefresh()
                        }
                    ) {
                        Icon(
                            imageVector = PortalIcons.Refresh,
                            contentDescription = "Atualizar Sala Virtual",
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenWeb(overview?.baseUrl?.plus("/d2l/home") ?: "https://salavirtual.unicep.edu.br/d2l/home")
                        }
                    ) {
                        Icon(
                            imageVector = PortalIcons.ArrowUpRight,
                            contentDescription = "Abrir Sala Virtual no navegador",
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            if (PlatformBridge.supportsCustomDownloadFolder()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceSecondary)
                        .clickable {
                            PlatformBridge.performHaptic(PortalHapticType.SELECTION)
                            onPickDownloadFolder()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = PortalIcons.Folder,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Pasta: $downloadFolderLabel/<Disciplina>",
                        color = colors.textSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "Alterar",
                        color = colors.accent,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (!error.isNullOrBlank()) {
            item {
                PortalCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = PortalIcons.Warning,
                            contentDescription = null,
                            tint = colors.orange,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Não foi possível sincronizar a Sala Virtual",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = error,
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PortalPrimaryButton(
                            text = "Tentar novamente",
                            onClick = onRefresh,
                            modifier = Modifier.weight(1f)
                        )
                        PortalSecondaryButton(
                            text = "Abrir no site",
                            onClick = { onOpenWeb(virtualHomeUrl) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (loading && overview == null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(4) {
                        AppleSkeletonBox(
                            modifier = Modifier.fillMaxWidth().height(76.dp),
                            shape = RoundedCornerShape(18.dp)
                        )
                    }
                }
            }
            return@LazyColumn
        }

        if (overview != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryStatPill(
                        label = "Disciplinas",
                        value = "${overview.courses.size}",
                        tint = colors.accent,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryStatPill(
                        label = "Pendentes",
                        value = "$pendingTotal",
                        tint = if (pendingTotal > 0) colors.orange else colors.green,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryStatPill(
                        label = "Avisos",
                        value = "${overview.announcements.size}",
                        tint = colors.indigo,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                PortalSegmentedControl(
                    options = VirtualOverviewSubTab.entries.map { it.label },
                    selectedIndex = activeTab,
                    onSelect = onSelectTab
                )
            }

            when (VirtualOverviewSubTab.entries.getOrElse(activeTab) { VirtualOverviewSubTab.COURSES }) {
                VirtualOverviewSubTab.COURSES -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PortalSearchField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                placeholder = "Buscar disciplina na Sala Virtual..."
                            )
                            if (semesters.size > 1) {
                                val semChips: @Composable () -> Unit = {
                                    FilterChipPill(
                                        label = "Todos",
                                        selected = selectedSemester.isBlank(),
                                        onClick = { onSelectSemester("") }
                                    )
                                    semesters.forEach { sem ->
                                        FilterChipPill(
                                            label = sem,
                                            selected = selectedSemester == sem,
                                            onClick = { onSelectSemester(sem) }
                                        )
                                    }
                                }
                                if (isWideGrid) {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        semChips()
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        semChips()
                                    }
                                }
                            }
                        }
                    }

                    if (filteredCourses.isEmpty()) {
                        item {
                            AppleEmptyState(
                                icon = PortalIcons.Virtual,
                                title = "Nenhuma disciplina encontrada",
                                subtitle = "Tente limpar o filtro de busca ou semestre."
                            )
                        }
                    } else if (isWideGrid) {
                        val coursePairs = filteredCourses.chunked(2)
                        itemsIndexed(
                            items = coursePairs,
                            key = { _, pair -> pair.joinToString("-") { it.orgUnitId.toString() } }
                        ) { _, pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    VirtualCourseCard(
                                        course = pair[0],
                                        onClick = { onSelectCourse(pair[0].orgUnitId) }
                                    )
                                }
                                if (pair.size > 1) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        VirtualCourseCard(
                                            course = pair[1],
                                            onClick = { onSelectCourse(pair[1].orgUnitId) }
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        items(filteredCourses, key = { it.orgUnitId }) { course ->
                            VirtualCourseCard(
                                course = course,
                                onClick = { onSelectCourse(course.orgUnitId) }
                            )
                        }
                    }
                }

                VirtualOverviewSubTab.ASSIGNMENTS -> {
                    item {
                        val assignmentFilterChips: @Composable () -> Unit = {
                            AssignmentFilter.entries.forEach { f ->
                                FilterChipPill(
                                    label = f.label,
                                    selected = assignmentFilter == f,
                                    onClick = { onSelectAssignmentFilter(f.ordinal) }
                                )
                            }
                        }
                        if (isWideGrid) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                assignmentFilterChips()
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                assignmentFilterChips()
                            }
                        }
                    }

                    if (filteredAssignments.isEmpty()) {
                        item {
                            AppleEmptyState(
                                icon = PortalIcons.CheckCircle,
                                title = "Nenhuma atividade por aqui",
                                subtitle = "As tarefas das disciplinas ativas aparecerão aqui em modo somente leitura."
                            )
                        }
                    } else if (isWideGrid) {
                        val assignmentPairs = filteredAssignments.chunked(2)
                        itemsIndexed(
                            items = assignmentPairs,
                            key = { _, pair -> pair.joinToString(",") { "${it.orgUnitId}-${it.id}" } }
                        ) { _, pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    VirtualAssignmentCard(
                                        assignment = pair[0],
                                        showCourseName = true,
                                        onClick = { onSelectAssignment(pair[0]) }
                                    )
                                }
                                if (pair.size > 1) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        VirtualAssignmentCard(
                                            assignment = pair[1],
                                            showCourseName = true,
                                            onClick = { onSelectAssignment(pair[1]) }
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        items(filteredAssignments, key = { "${it.orgUnitId}-${it.id}" }) { assignment ->
                            VirtualAssignmentCard(
                                assignment = assignment,
                                showCourseName = true,
                                onClick = { onSelectAssignment(assignment) }
                            )
                        }
                    }
                }

                VirtualOverviewSubTab.UPDATES -> {
                    if (overview.calendarEvents.isNotEmpty()) {
                        item {
                            PortalSectionHeader(title = "Agenda e Prazos")
                        }
                        item {
                            PortalCard(contentPadding = PaddingValues(0.dp)) {
                                overview.calendarEvents.take(10).forEachIndexed { idx, ev ->
                                    if (idx > 0) PortalDivider()
                                    CalendarEventRow(ev = ev, onOpenWeb = onOpenWeb)
                                }
                            }
                        }
                    }

                    item {
                        PortalSectionHeader(title = "Avisos Recentes")
                    }
                    if (overview.announcements.isEmpty()) {
                        item {
                            AppleEmptyState(
                                icon = PortalIcons.Bell,
                                title = "Nenhum aviso recente",
                                subtitle = "Comunicados institucionais e das disciplinas aparecem aqui."
                            )
                        }
                    } else {
                        items(overview.announcements, key = { "${it.orgUnitId}-${it.id}" }) { ann ->
                            VirtualAnnouncementCard(
                                announcement = ann,
                                onClick = { onSelectAnnouncement(ann) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatPill(
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    PortalCard(modifier = modifier, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        Text(
            text = value,
            color = tint,
            style = tabularNumberStyle(appleType.title2),
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = colors.textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun FilterChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalPortalColors.current
    Surface(
        modifier = Modifier
            .clip(CircleShape)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) colors.accent else colors.surfaceSecondary,
        border = if (selected) null else BorderStroke(0.8.dp, colors.divider.copy(alpha = 0.7f))
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun VirtualCourseCard(
    course: VirtualCourseSummary,
    onClick: () -> Unit
) {
    val colors = LocalPortalColors.current
    PortalCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = PortalIcons.Book,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.name.toDisplayTitle(),
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val sub = listOfNotNull(
                    course.semesterName.takeIf { it.isNotBlank() },
                    if (course.assignmentCount > 0) "${course.assignmentCount} atividade(s)" else null
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = sub,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (course.pendingCount > 0) {
                PortalBadge(
                    text = "${course.pendingCount} pendente${if (course.pendingCount > 1) "s" else ""}",
                    color = colors.orange
                )
            }
            Icon(
                imageVector = PortalIcons.ChevronRight,
                contentDescription = null,
                tint = colors.tertiaryLabel,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
internal fun VirtualAssignmentCard(
    assignment: VirtualAssignmentItem,
    showCourseName: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalPortalColors.current
    val badgeColor = assignmentStatusColor(assignment.status)
    PortalCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (showCourseName && assignment.orgUnitName.isNotBlank()) {
                    Text(
                        text = assignment.orgUnitName.toDisplayTitle(),
                        color = colors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    text = assignment.name,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            PortalBadge(
                text = assignment.statusLabel,
                color = badgeColor
            )
        }

        val dueText = formatIsoDateTime(assignment.dueDate.ifBlank { assignment.endDate }, assignment.dueAtMillis)
        val metaParts = buildList {
            if (dueText.isNotBlank()) add("Prazo: $dueText")
            if (assignment.score != null) {
                val maxPart = assignment.outOf?.let { " / ${formatScore(it)}" }.orEmpty()
                add("Nota: ${formatScore(assignment.score)}$maxPart")
            } else if (assignment.outOf != null) {
                add("Valor: ${formatScore(assignment.outOf)} pts")
            }
            if (assignment.attachments.isNotEmpty()) {
                add("${assignment.attachments.size} anexo(s)")
            }
        }
        if (metaParts.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = metaParts.joinToString(" · "),
                color = colors.textSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
internal fun VirtualAnnouncementCard(
    announcement: VirtualAnnouncement,
    onClick: () -> Unit
) {
    val colors = LocalPortalColors.current
    PortalCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = announcement.orgUnitName.ifBlank { "UNICEP" }.toDisplayTitle(),
                color = colors.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val dt = formatIsoDateTime(announcement.startDate.ifBlank { announcement.createdDate })
            if (dt.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(text = dt, color = colors.tertiaryLabel, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = announcement.title,
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (announcement.body.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = announcement.body,
                color = colors.textSecondary,
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
