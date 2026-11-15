package me.muriloscolari.unicep.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import me.muriloscolari.unicep.data.repo.parseIso8601ToEpochMillis
import me.muriloscolari.unicep.data.api.VirtualAnnouncement
import me.muriloscolari.unicep.data.api.VirtualAssignmentItem
import me.muriloscolari.unicep.data.api.VirtualCalendarEvent
import me.muriloscolari.unicep.data.api.VirtualContentModule
import me.muriloscolari.unicep.data.api.VirtualContentTopic
import me.muriloscolari.unicep.data.api.VirtualCourseDetailResponse
import me.muriloscolari.unicep.data.api.VirtualCourseSummary
import me.muriloscolari.unicep.data.api.VirtualDiscussionForum
import me.muriloscolari.unicep.data.api.VirtualFileAttachment
import me.muriloscolari.unicep.data.api.VirtualGradeItem
import me.muriloscolari.unicep.data.api.VirtualOverviewResponse
import me.muriloscolari.unicep.data.api.VirtualQuizItem
import me.muriloscolari.unicep.data.repo.VirtualDownloadRequest
import me.muriloscolari.unicep.platform.DesignStyle
import me.muriloscolari.unicep.platform.PlatformBridge
import me.muriloscolari.unicep.platform.PortalHapticType
import me.muriloscolari.unicep.ui.designsystem.AppleEmptyState
import me.muriloscolari.unicep.ui.designsystem.AppleSheet
import me.muriloscolari.unicep.ui.designsystem.AppleSheetDetent
import me.muriloscolari.unicep.ui.designsystem.AppleSkeletonBox
import me.muriloscolari.unicep.ui.designsystem.LocalAppleContentInsets
import me.muriloscolari.unicep.ui.designsystem.LocalAppleTypography
import me.muriloscolari.unicep.ui.designsystem.LocalDesignStyle
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.ui.designsystem.PortalBadge
import me.muriloscolari.unicep.ui.designsystem.PortalCard
import me.muriloscolari.unicep.ui.designsystem.PortalDivider
import me.muriloscolari.unicep.ui.designsystem.PortalIcons
import me.muriloscolari.unicep.ui.designsystem.PortalListRow
import me.muriloscolari.unicep.ui.designsystem.PortalPrimaryButton
import me.muriloscolari.unicep.ui.designsystem.PortalSearchField
import me.muriloscolari.unicep.ui.designsystem.PortalSecondaryButton
import me.muriloscolari.unicep.ui.designsystem.PortalSectionHeader
import me.muriloscolari.unicep.ui.designsystem.PortalSegmentedControl
import me.muriloscolari.unicep.ui.designsystem.appleScrollBoundaryHaptics
import me.muriloscolari.unicep.ui.designsystem.tabularNumberStyle
import me.muriloscolari.unicep.ui.util.toDisplayTitle

private enum class VirtualOverviewSubTab(val label: String) {
    COURSES("Disciplinas"),
    ASSIGNMENTS("Atividades"),
    UPDATES("Avisos")
}

private enum class VirtualCourseSubTab(val label: String) {
    ASSIGNMENTS("Atividades"),
    CONTENT("Conteúdo"),
    GRADES("Notas"),
    MORE("Mais")
}

private enum class AssignmentFilter(val label: String) {
    ALL("Todas"),
    PENDING("Pendentes"),
    GRADED("Avaliadas")
}

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
    val contentPadding = if (style == DesignStyle.APPLE) {
        PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = appleInsets.calculateTopPadding() + 8.dp,
            bottom = appleInsets.calculateBottomPadding() + 24.dp
        )
    } else {
        PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
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
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
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
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssignmentFilter.entries.forEach { f ->
                                FilterChipPill(
                                    label = f.label,
                                    selected = assignmentFilter == f,
                                    onClick = { onSelectAssignmentFilter(f.ordinal) }
                                )
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
private fun VirtualCourseDetailBody(
    courseId: Long,
    fallbackCourse: VirtualCourseSummary?,
    detail: VirtualCourseDetailResponse?,
    overviewBaseUrl: String,
    courseSubTab: Int,
    loading: Boolean,
    error: String?,
    contentPadding: PaddingValues,
    listState: LazyListState,
    downloadingFileKey: String?,
    batchDownloadDone: Int,
    batchDownloadTotal: Int,
    downloadFolderLabel: String,
    onPickDownloadFolder: () -> Unit,
    onBack: () -> Unit,
    onSelectSubTab: (Int) -> Unit,
    onRefreshCourse: () -> Unit,
    onSelectAssignment: (VirtualAssignmentItem) -> Unit,
    onSelectAnnouncement: (VirtualAnnouncement) -> Unit,
    onViewD2LFile: (path: String, title: String, courseName: String, fileName: String) -> Unit,
    onDownloadD2LFile: (path: String, fileName: String, courseName: String) -> Unit,
    onDownloadBatch: (List<VirtualDownloadRequest>) -> Unit,
    onOpenWeb: (String) -> Unit
) {
    val style = LocalDesignStyle.current
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val appleInsets = LocalAppleContentInsets.current
    val haptics = LocalHapticFeedback.current

    val course = detail?.course ?: fallbackCourse
    val courseTitle = (course?.name ?: "Disciplina $courseId").toDisplayTitle()
    val effectiveBaseUrl = detail?.baseUrl?.ifBlank { null }?.trimEnd('/')
        ?: overviewBaseUrl.ifBlank { null }?.trimEnd('/')
        ?: "https://salavirtual.unicep.edu.br"
    val courseWebUrl = course?.homeUrl?.ifBlank { null }
        ?: "$effectiveBaseUrl/d2l/home/$courseId"

    var showExpiredAssignments by remember(courseId) { mutableStateOf(false) }
    var selectionMode by remember(courseId) { mutableStateOf(false) }
    var selectedFiles by remember(courseId) { mutableStateOf<Map<String, VirtualDownloadRequest>>(emptyMap()) }

    LaunchedEffect(courseSubTab) {
        if (courseSubTab != VirtualCourseSubTab.CONTENT.ordinal) {
            selectionMode = false
            selectedFiles = emptyMap()
        }
    }

    val allDownloadables = remember(detail?.modules, courseTitle) {
        collectDownloadableRequests(detail?.modules.orEmpty(), courseTitle)
    }

    val toggleSelection: (VirtualDownloadRequest) -> Unit = { req ->
        val next = selectedFiles.toMutableMap()
        if (next.containsKey(req.path)) {
            next.remove(req.path)
        } else {
            next[req.path] = req
        }
        selectedFiles = next
        if (next.isEmpty()) {
            selectionMode = false
        }
    }

    val startSelectionWith: (VirtualDownloadRequest) -> Unit = { req ->
        PlatformBridge.performHaptic(PortalHapticType.IMPACT_MEDIUM)
        selectionMode = true
        selectedFiles = selectedFiles + (req.path to req)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val bottomExtra = if (selectionMode || batchDownloadTotal > 0) 92.dp else 0.dp
        val effectivePadding = PaddingValues(
            start = contentPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
            end = contentPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + bottomExtra
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .appleScrollBoundaryHaptics(listState),
            contentPadding = effectivePadding,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onBack()
                            }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = PortalIcons.ChevronLeft,
                            contentDescription = "Voltar",
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Sala Virtual",
                            color = colors.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(onClick = onRefreshCourse) {
                            Icon(
                                imageVector = PortalIcons.Refresh,
                                contentDescription = "Atualizar disciplina",
                                tint = colors.accent,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        IconButton(onClick = { onOpenWeb(courseWebUrl) }) {
                            Icon(
                                imageVector = PortalIcons.ArrowUpRight,
                                contentDescription = "Abrir disciplina no site",
                                tint = colors.accent,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                Text(
                    text = courseTitle,
                    color = colors.textPrimary,
                    style = if (style == DesignStyle.APPLE) appleType.title2 else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                val meta = listOfNotNull(
                    course?.semesterName?.takeIf { it.isNotBlank() },
                    course?.code?.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        item {
            PortalSegmentedControl(
                options = VirtualCourseSubTab.entries.map { it.label },
                selectedIndex = courseSubTab.coerceIn(0, VirtualCourseSubTab.entries.lastIndex),
                onSelect = onSelectSubTab
            )
        }

        if (!error.isNullOrBlank() && detail == null) {
            item {
                PortalCard {
                    Text(
                        text = error,
                        color = colors.red,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    PortalPrimaryButton(
                        text = "Tentar novamente",
                        onClick = onRefreshCourse
                    )
                }
            }
        }

        if (loading && detail == null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(4) {
                        AppleSkeletonBox(
                            modifier = Modifier.fillMaxWidth().height(70.dp),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }
            return@LazyColumn
        }

        if (detail != null) {
            when (VirtualCourseSubTab.entries.getOrElse(courseSubTab) { VirtualCourseSubTab.ASSIGNMENTS }) {
                VirtualCourseSubTab.ASSIGNMENTS -> {
                    val activeAssignments = detail.assignments.filter { it.status != "expired" }
                    val expiredAssignments = detail.assignments.filter { it.status == "expired" }
                    if (detail.assignments.isEmpty()) {
                        item {
                            AppleEmptyState(
                                icon = PortalIcons.CheckCircle,
                                title = "Nenhuma atividade cadastrada",
                                subtitle = "Esta disciplina ainda não possui pastas de entrega no AVA."
                            )
                        }
                    } else {
                        if (activeAssignments.isEmpty()) {
                            item {
                                PortalCard {
                                    Text(
                                        text = "Nenhuma atividade em aberto no momento.",
                                        color = colors.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "As atividades com prazo encerrado ficam recolhidas abaixo.",
                                        color = colors.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        } else {
                            items(activeAssignments, key = { it.id }) { assignment ->
                                VirtualAssignmentCard(
                                    assignment = assignment,
                                    showCourseName = false,
                                    onClick = { onSelectAssignment(assignment) }
                                )
                            }
                        }

                        if (expiredAssignments.isNotEmpty()) {
                            item(key = "expired_toggle") {
                                FilterChipPill(
                                    label = if (showExpiredAssignments) {
                                        "Ocultar encerradas (${expiredAssignments.size})"
                                    } else {
                                        "Mostrar encerradas (${expiredAssignments.size})"
                                    },
                                    selected = showExpiredAssignments,
                                    onClick = { showExpiredAssignments = !showExpiredAssignments }
                                )
                            }
                            if (showExpiredAssignments) {
                                items(expiredAssignments, key = { "exp-${it.id}" }) { assignment ->
                                    VirtualAssignmentCard(
                                        assignment = assignment,
                                        showCourseName = false,
                                        onClick = { onSelectAssignment(assignment) }
                                    )
                                }
                            }
                        }
                    }
                }

                VirtualCourseSubTab.CONTENT -> {
                    if (detail.modules.isEmpty()) {
                        item {
                            AppleEmptyState(
                                icon = PortalIcons.Book,
                                title = "Sem módulos de conteúdo",
                                subtitle = "Nenhum material publicado nesta disciplina."
                            )
                        }
                    } else {
                        if (allDownloadables.isNotEmpty()) {
                            item(key = "content_toolbar") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (selectionMode) {
                                            "Toque nos arquivos para marcar"
                                        } else {
                                            "Segure um arquivo para selecionar vários"
                                        },
                                        color = colors.textSecondary,
                                        fontSize = 11.5.sp,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (PlatformBridge.supportsCustomDownloadFolder() && !selectionMode) {
                                            FilterChipPill(
                                                label = "Pasta",
                                                selected = false,
                                                onClick = onPickDownloadFolder
                                            )
                                        }
                                        FilterChipPill(
                                            label = if (selectionMode) "Cancelar" else "Selecionar",
                                            selected = selectionMode,
                                            onClick = {
                                                PlatformBridge.performHaptic(PortalHapticType.SELECTION)
                                                selectionMode = !selectionMode
                                                if (!selectionMode) selectedFiles = emptyMap()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        items(detail.modules, key = { it.moduleId }) { module ->
                            VirtualModuleCard(
                                module = module,
                                courseName = courseTitle,
                                selectionMode = selectionMode,
                                selectedPaths = selectedFiles.keys,
                                downloadingFileKey = downloadingFileKey,
                                onToggleSelection = toggleSelection,
                                onLongPressTopic = startSelectionWith,
                                onViewD2LFile = onViewD2LFile,
                                onDownloadD2LFile = onDownloadD2LFile,
                                onOpenWeb = onOpenWeb
                            )
                        }
                    }
                }

                VirtualCourseSubTab.GRADES -> {
                    if (detail.grades.isEmpty()) {
                        item {
                            AppleEmptyState(
                                icon = PortalIcons.Grades,
                                title = "Sem notas no AVA",
                                subtitle = "Itens avaliativos da Sala Virtual aparecerão aqui."
                            )
                        }
                    } else {
                        item {
                            PortalCard(contentPadding = PaddingValues(0.dp)) {
                                detail.grades.forEachIndexed { idx, g ->
                                    if (idx > 0) PortalDivider()
                                    VirtualGradeRow(item = g)
                                }
                            }
                        }
                    }
                }

                VirtualCourseSubTab.MORE -> {
                    if (detail.announcements.isNotEmpty()) {
                        item { PortalSectionHeader(title = "Avisos da Disciplina") }
                        items(detail.announcements, key = { "ann-${it.id}" }) { ann ->
                            VirtualAnnouncementCard(
                                announcement = ann,
                                onClick = { onSelectAnnouncement(ann) }
                            )
                        }
                    }

                    item { PortalSectionHeader(title = "Questionários (Somente Leitura)") }
                    if (detail.quizzes.isEmpty()) {
                        item {
                            PortalCard {
                                Text(
                                    text = "Nenhum questionário disponível nesta disciplina.",
                                    color = colors.textSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        items(detail.quizzes, key = { "quiz-${it.quizId}" }) { quiz ->
                            VirtualQuizCard(quiz = quiz, onOpenWeb = onOpenWeb)
                        }
                    }

                    item { PortalSectionHeader(title = "Fóruns de Discussão") }
                    if (detail.discussions.isEmpty()) {
                        item {
                            PortalCard {
                                Text(
                                    text = "Nenhum fórum cadastrado nesta disciplina.",
                                    color = colors.textSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        items(detail.discussions, key = { "forum-${it.forumId}" }) { forum ->
                            VirtualForumCard(forum = forum, onOpenWeb = onOpenWeb)
                        }
                    }

                    if (detail.calendar.isNotEmpty()) {
                        item { PortalSectionHeader(title = "Calendário da Disciplina") }
                        item {
                            PortalCard(contentPadding = PaddingValues(0.dp)) {
                                detail.calendar.forEachIndexed { idx, ev ->
                                    if (idx > 0) PortalDivider()
                                    CalendarEventRow(ev = ev, onOpenWeb = onOpenWeb)
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        // Floating batch selection / progress bar
        AnimatedVisibility(
            visible = selectionMode || batchDownloadTotal > 0,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = if (style == DesignStyle.APPLE) appleInsets.calculateBottomPadding() + 12.dp else 12.dp
                )
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colors.surface.copy(alpha = 0.96f),
                shadowElevation = 10.dp,
                border = BorderStroke(0.8.dp, colors.divider)
            ) {
                if (batchDownloadTotal > 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Baixando $batchDownloadDone de $batchDownloadTotal arquivo(s)...",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "$downloadFolderLabel/",
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (batchDownloadDone.toFloat() / batchDownloadTotal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                            color = colors.accent
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${selectedFiles.size} selecionado(s)",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Salva em $downloadFolderLabel/<Disciplina>",
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        val allSelected = allDownloadables.isNotEmpty() && selectedFiles.size == allDownloadables.size
                        TextButton(
                            onClick = {
                                PlatformBridge.performHaptic(PortalHapticType.SELECTION)
                                selectedFiles = if (allSelected) {
                                    emptyMap()
                                } else {
                                    allDownloadables.associateBy { it.path }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (allSelected) "Limpar" else "Todos",
                                color = colors.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = {
                                PlatformBridge.performHaptic(PortalHapticType.IMPACT_MEDIUM)
                                val toDownload = selectedFiles.values.toList()
                                selectionMode = false
                                selectedFiles = emptyMap()
                                onDownloadBatch(toDownload)
                            },
                            enabled = selectedFiles.isNotEmpty(),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(PortalIcons.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "Baixar (${selectedFiles.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
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
private fun FilterChipPill(
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
        modifier = Modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
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
private fun VirtualAssignmentCard(
    assignment: VirtualAssignmentItem,
    showCourseName: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalPortalColors.current
    val badgeColor = assignmentStatusColor(assignment.status)
    PortalCard(
        modifier = Modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
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
private fun VirtualAnnouncementCard(
    announcement: VirtualAnnouncement,
    onClick: () -> Unit
) {
    val colors = LocalPortalColors.current
    PortalCard(
        modifier = Modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
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

@Composable
private fun CalendarEventRow(
    ev: VirtualCalendarEvent,
    onOpenWeb: (String) -> Unit
) {
    val colors = LocalPortalColors.current
    PortalListRow(
        title = ev.title,
        subtitle = listOfNotNull(
            ev.orgUnitName.takeIf { it.isNotBlank() }?.toDisplayTitle(),
            formatIsoDateTime(ev.startDateTime).takeIf { it.isNotBlank() }
        ).joinToString(" · "),
        leadingIcon = PortalIcons.Calendar,
        leadingTint = colors.accent,
        trailing = if (ev.webUrl.isNotBlank()) {
            {
                Icon(
                    imageVector = PortalIcons.ArrowUpRight,
                    contentDescription = "Abrir evento",
                    tint = colors.tertiaryLabel,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else null,
        onClick = if (ev.webUrl.isNotBlank()) ({ onOpenWeb(ev.webUrl) }) else null
    )
}

private fun collectDownloadableRequests(
    modules: List<VirtualContentModule>,
    courseName: String
): List<VirtualDownloadRequest> {
    val out = mutableListOf<VirtualDownloadRequest>()
    fun walk(list: List<VirtualContentModule>) {
        list.forEach { mod ->
            mod.topics.forEach { t ->
                if (t.downloadPath.isNotBlank()) {
                    out.add(
                        VirtualDownloadRequest(
                            path = t.downloadPath,
                            fileName = inferTopicFileName(t),
                            courseName = courseName
                        )
                    )
                }
            }
            if (mod.modules.isNotEmpty()) walk(mod.modules)
        }
    }
    walk(modules)
    return out.distinctBy { it.path }
}

private fun inferTopicFileName(topic: VirtualContentTopic): String {
    val fromUrl = topic.url
        .substringBefore('?')
        .substringAfterLast('/')
        .trim()
    if (fromUrl.contains('.') && fromUrl.length in 3..120) {
        return fromUrl
    }
    return topic.title.ifBlank { "material_${topic.topicId}" }
}

@Composable
private fun VirtualModuleCard(
    module: VirtualContentModule,
    courseName: String,
    selectionMode: Boolean,
    selectedPaths: Set<String>,
    downloadingFileKey: String?,
    onToggleSelection: (VirtualDownloadRequest) -> Unit,
    onLongPressTopic: (VirtualDownloadRequest) -> Unit,
    onViewD2LFile: (path: String, title: String, courseName: String, fileName: String) -> Unit,
    onDownloadD2LFile: (path: String, fileName: String, courseName: String) -> Unit,
    onOpenWeb: (String) -> Unit,
    depth: Int = 0
) {
    val colors = LocalPortalColors.current
    var expanded by remember { mutableStateOf(depth == 0) }
    val itemCount = module.topics.size + module.modules.size

    PortalCard(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.title,
                    color = colors.textPrimary,
                    fontSize = if (depth == 0) 15.sp else 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (itemCount > 0) {
                    Text(
                        text = "$itemCount item(ns)",
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }
            Icon(
                imageVector = if (expanded) PortalIcons.ChevronDown else PortalIcons.ChevronRight,
                contentDescription = null,
                tint = colors.tertiaryLabel,
                modifier = Modifier.size(16.dp)
            )
        }

        if (expanded) {
            if (module.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = module.description,
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }
            if (module.topics.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                module.topics.forEachIndexed { idx, topic ->
                    if (idx > 0) PortalDivider()
                    VirtualTopicRow(
                        topic = topic,
                        courseName = courseName,
                        selectionMode = selectionMode,
                        selected = topic.downloadPath.isNotBlank() && selectedPaths.contains(topic.downloadPath),
                        isDownloading = topic.downloadPath.isNotBlank() && downloadingFileKey == topic.downloadPath,
                        onToggleSelection = onToggleSelection,
                        onLongPressTopic = onLongPressTopic,
                        onViewD2LFile = onViewD2LFile,
                        onDownloadD2LFile = onDownloadD2LFile,
                        onOpenWeb = onOpenWeb
                    )
                }
            }
            if (module.modules.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    module.modules.forEach { sub ->
                        VirtualModuleCard(
                            module = sub,
                            courseName = courseName,
                            selectionMode = selectionMode,
                            selectedPaths = selectedPaths,
                            downloadingFileKey = downloadingFileKey,
                            onToggleSelection = onToggleSelection,
                            onLongPressTopic = onLongPressTopic,
                            onViewD2LFile = onViewD2LFile,
                            onDownloadD2LFile = onDownloadD2LFile,
                            onOpenWeb = onOpenWeb,
                            depth = depth + 1
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VirtualTopicRow(
    topic: VirtualContentTopic,
    courseName: String,
    selectionMode: Boolean,
    selected: Boolean,
    isDownloading: Boolean,
    onToggleSelection: (VirtualDownloadRequest) -> Unit,
    onLongPressTopic: (VirtualDownloadRequest) -> Unit,
    onViewD2LFile: (path: String, title: String, courseName: String, fileName: String) -> Unit,
    onDownloadD2LFile: (path: String, fileName: String, courseName: String) -> Unit,
    onOpenWeb: (String) -> Unit
) {
    val colors = LocalPortalColors.current
    val hasDownload = topic.downloadPath.isNotBlank()
    val inferredName = remember(topic) { inferTopicFileName(topic) }
    val request = remember(topic, courseName, inferredName) {
        VirtualDownloadRequest(
            path = topic.downloadPath,
            fileName = inferredName,
            courseName = courseName
        )
    }

    val clickableModifier = when {
        hasDownload -> Modifier.combinedClickable(
            onClick = {
                if (selectionMode) {
                    PlatformBridge.performHaptic(PortalHapticType.SELECTION)
                    onToggleSelection(request)
                } else {
                    onViewD2LFile(topic.downloadPath, topic.title, courseName, inferredName)
                }
            },
            onLongClick = { onLongPressTopic(request) }
        )
        topic.externalUrl.isNotBlank() -> Modifier.clickable { onOpenWeb(topic.externalUrl) }
        else -> Modifier
    }

    val typeLabel = when (topic.typeIdentifier.lowercase()) {
        "file" -> "Arquivo"
        "link" -> "Link"
        "dropbox" -> "Atividade"
        "quiz" -> "Questionário"
        "discussion" -> "Fórum"
        else -> topic.typeIdentifier.ifBlank { "Recurso" }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (hasDownload || topic.externalUrl.isNotBlank()) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .then(clickableModifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (selectionMode && hasDownload) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.accent else Color.Transparent)
                    .border(
                        width = 1.6.dp,
                        color = if (selected) colors.accent else colors.textSecondary.copy(alpha = 0.5f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        imageVector = PortalIcons.Check,
                        contentDescription = "Selecionado",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        } else {
            Icon(
                imageVector = when {
                    hasDownload -> PortalIcons.Document
                    topic.externalUrl.isNotBlank() -> PortalIcons.Globe
                    else -> PortalIcons.Book
                },
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (topic.unread) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(colors.accent)
                    )
                }
                Text(
                    text = topic.title,
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = if (hasDownload) "$typeLabel · toque para abrir" else typeLabel,
                color = colors.textSecondary,
                fontSize = 11.sp
            )
        }
        if (hasDownload && !selectionMode) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceSecondary)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(enabled = !isDownloading) {
                        PlatformBridge.performHaptic(PortalHapticType.SELECTION)
                        onDownloadD2LFile(topic.downloadPath, inferredName, courseName)
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = colors.accent,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = PortalIcons.Download,
                        contentDescription = "Baixar arquivo",
                        tint = colors.accent,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        } else if (topic.externalUrl.isNotBlank()) {
            Icon(
                imageVector = PortalIcons.ArrowUpRight,
                contentDescription = "Abrir link",
                tint = colors.accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun VirtualGradeRow(item: VirtualGradeItem) {
    val colors = LocalPortalColors.current
    val scoreText = when {
        item.displayedGrade.isNotBlank() -> item.displayedGrade
        item.pointsNumerator != null && item.pointsDenominator != null ->
            "${formatScore(item.pointsNumerator)} / ${formatScore(item.pointsDenominator)}"
        item.pointsNumerator != null -> formatScore(item.pointsNumerator)
        else -> "—"
    }
    PortalListRow(
        title = item.name,
        subtitle = item.comments.takeIf { it.isNotBlank() },
        trailing = {
            Text(
                text = scoreText,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }
    )
}

@Composable
private fun VirtualQuizCard(
    quiz: VirtualQuizItem,
    onOpenWeb: (String) -> Unit
) {
    val colors = LocalPortalColors.current
    PortalCard(
        modifier = Modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { onOpenWeb(quiz.webUrl) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = quiz.name,
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            PortalBadge(
                text = if (quiz.isActive) "Ativo" else "Inativo",
                color = if (quiz.isActive) colors.green else colors.textSecondary
            )
        }
        val meta = buildList {
            val due = formatIsoDateTime(quiz.dueDate.ifBlank { quiz.endDate })
            if (due.isNotBlank()) add("Prazo: $due")
            quiz.timeLimitMins?.let { add("${it} min") }
            quiz.attemptsAllowed?.let { add("$it tentativa(s)") }
        }
        if (meta.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = meta.joinToString(" · "),
                color = colors.textSecondary,
                fontSize = 12.sp
            )
        }
        if (quiz.description.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = quiz.description,
                color = colors.textSecondary,
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun VirtualForumCard(
    forum: VirtualDiscussionForum,
    onOpenWeb: (String) -> Unit
) {
    val colors = LocalPortalColors.current
    PortalCard(
        modifier = Modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { onOpenWeb(forum.webUrl) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = forum.name,
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = PortalIcons.ArrowUpRight,
                contentDescription = "Abrir fórum no navegador",
                tint = colors.accent,
                modifier = Modifier.size(16.dp)
            )
        }
        if (forum.description.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = forum.description,
                color = colors.textSecondary,
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirtualAssignmentSheet(
    assignment: VirtualAssignmentItem,
    onViewD2LFile: (path: String, title: String, courseName: String, fileName: String) -> Unit,
    onDownloadD2LFile: (path: String, fileName: String, courseName: String) -> Unit,
    onDownloadBatch: (List<VirtualDownloadRequest>) -> Unit,
    downloadingFileKey: String?,
    onOpenWeb: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val style = LocalDesignStyle.current
    val subtitle = assignment.orgUnitName.takeIf { it.isNotBlank() }?.toDisplayTitle()
    if (style == DesignStyle.APPLE) {
        AppleSheet(
            title = assignment.name,
            subtitle = subtitle,
            onDismiss = onDismiss,
            initialDetent = AppleSheetDetent.Large
        ) {
            VirtualAssignmentSheetContent(
                assignment = assignment,
                onViewD2LFile = onViewD2LFile,
                onDownloadD2LFile = onDownloadD2LFile,
                onDownloadBatch = onDownloadBatch,
                downloadingFileKey = downloadingFileKey,
                onOpenWeb = onOpenWeb
            )
        }
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = assignment.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = LocalPortalColors.current.textSecondary,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
                VirtualAssignmentSheetContent(
                    assignment = assignment,
                    onViewD2LFile = onViewD2LFile,
                    onDownloadD2LFile = onDownloadD2LFile,
                    onDownloadBatch = onDownloadBatch,
                    downloadingFileKey = downloadingFileKey,
                    onOpenWeb = onOpenWeb
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun VirtualAssignmentSheetContent(
    assignment: VirtualAssignmentItem,
    onViewD2LFile: (path: String, title: String, courseName: String, fileName: String) -> Unit,
    onDownloadD2LFile: (path: String, fileName: String, courseName: String) -> Unit,
    onDownloadBatch: (List<VirtualDownloadRequest>) -> Unit,
    downloadingFileKey: String?,
    onOpenWeb: (String) -> Unit
) {
    val colors = LocalPortalColors.current
    val badgeColor = assignmentStatusColor(assignment.status)
    val courseName = assignment.orgUnitName.ifBlank { "Sala Virtual" }.toDisplayTitle()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PortalBadge(text = assignment.statusLabel, color = badgeColor)
        if (assignment.score != null) {
            val maxPart = assignment.outOf?.let { " / ${formatScore(it)}" }.orEmpty()
            PortalBadge(
                text = "Nota: ${formatScore(assignment.score)}$maxPart",
                color = colors.green
            )
        } else if (assignment.outOf != null) {
            PortalBadge(
                text = "Vale ${formatScore(assignment.outOf)} pts",
                color = colors.accent
            )
        }
    }

    val dueText = formatIsoDateTime(assignment.dueDate.ifBlank { assignment.endDate }, assignment.dueAtMillis)
    if (dueText.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Prazo de entrega: $dueText",
            color = colors.textSecondary,
            fontSize = 13.sp
        )
    }

    if (assignment.instructions.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        PortalSectionHeader(title = "Instruções")
        Spacer(Modifier.height(4.dp))
        PortalCard {
            Text(
                text = assignment.instructions,
                color = colors.textPrimary,
                fontSize = 13.sp
            )
        }
    }

    if (assignment.attachments.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PortalSectionHeader(title = "Anexos da Atividade")
            val downloadable = assignment.attachments.filter { it.downloadPath.isNotBlank() }
            if (downloadable.size > 1) {
                TextButton(
                    onClick = {
                        onDownloadBatch(
                            downloadable.map {
                                VirtualDownloadRequest(
                                    path = it.downloadPath,
                                    fileName = it.fileName,
                                    courseName = courseName
                                )
                            }
                        )
                    }
                ) {
                    Text("Baixar todos (${downloadable.size})", color = colors.accent, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        PortalCard(contentPadding = PaddingValues(0.dp)) {
            assignment.attachments.forEachIndexed { idx, att ->
                if (idx > 0) PortalDivider()
                AttachmentRow(
                    attachment = att,
                    isDownloading = att.downloadPath.isNotBlank() && downloadingFileKey == att.downloadPath,
                    onView = {
                        if (att.downloadPath.isNotBlank()) {
                            onViewD2LFile(att.downloadPath, att.fileName, courseName, att.fileName)
                        }
                    },
                    onDownload = {
                        if (att.downloadPath.isNotBlank()) {
                            onDownloadD2LFile(att.downloadPath, att.fileName, courseName)
                        }
                    }
                )
            }
        }
    }

    if (assignment.submittedFiles.isNotEmpty() || assignment.submissionDate.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        PortalSectionHeader(title = "Seu Envio")
        Spacer(Modifier.height(4.dp))
        PortalCard {
            if (assignment.submissionDate.isNotBlank()) {
                Text(
                    text = "Enviado em ${formatIsoDateTime(assignment.submissionDate)}",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }
            assignment.submittedFiles.forEach { sf ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "• ${sf.fileName}",
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    if (assignment.feedbackText.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        PortalSectionHeader(title = "Feedback do Professor")
        Spacer(Modifier.height(4.dp))
        PortalCard {
            Text(
                text = assignment.feedbackText,
                color = colors.textPrimary,
                fontSize = 13.sp
            )
        }
    }

    Spacer(Modifier.height(14.dp))
    PortalSecondaryButton(
        text = "Abrir entrega no navegador",
        onClick = { onOpenWeb(assignment.webUrl) },
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirtualAnnouncementSheet(
    announcement: VirtualAnnouncement,
    onViewD2LFile: (path: String, title: String, courseName: String, fileName: String) -> Unit,
    onDownloadD2LFile: (path: String, fileName: String, courseName: String) -> Unit,
    onDownloadBatch: (List<VirtualDownloadRequest>) -> Unit,
    downloadingFileKey: String?,
    onDismiss: () -> Unit
) {
    val style = LocalDesignStyle.current
    val subtitle = listOfNotNull(
        announcement.orgUnitName.takeIf { it.isNotBlank() }?.toDisplayTitle(),
        formatIsoDateTime(announcement.startDate.ifBlank { announcement.createdDate }).takeIf { it.isNotBlank() }
    ).joinToString(" · ").ifBlank { null }

    if (style == DesignStyle.APPLE) {
        AppleSheet(
            title = announcement.title,
            subtitle = subtitle,
            onDismiss = onDismiss,
            initialDetent = AppleSheetDetent.Medium
        ) {
            VirtualAnnouncementSheetContent(
                announcement = announcement,
                onViewD2LFile = onViewD2LFile,
                onDownloadD2LFile = onDownloadD2LFile,
                onDownloadBatch = onDownloadBatch,
                downloadingFileKey = downloadingFileKey
            )
        }
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = announcement.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = LocalPortalColors.current.textSecondary,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
                VirtualAnnouncementSheetContent(
                    announcement = announcement,
                    onViewD2LFile = onViewD2LFile,
                    onDownloadD2LFile = onDownloadD2LFile,
                    onDownloadBatch = onDownloadBatch,
                    downloadingFileKey = downloadingFileKey
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun VirtualAnnouncementSheetContent(
    announcement: VirtualAnnouncement,
    onViewD2LFile: (path: String, title: String, courseName: String, fileName: String) -> Unit,
    onDownloadD2LFile: (path: String, fileName: String, courseName: String) -> Unit,
    onDownloadBatch: (List<VirtualDownloadRequest>) -> Unit,
    downloadingFileKey: String?
) {
    val colors = LocalPortalColors.current
    val courseName = announcement.orgUnitName.ifBlank { "Avisos Sala Virtual" }.toDisplayTitle()
    if (announcement.body.isNotBlank()) {
        PortalCard {
            Text(
                text = announcement.body,
                color = colors.textPrimary,
                fontSize = 14.sp
            )
        }
    }
    if (announcement.attachments.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PortalSectionHeader(title = "Anexos")
            val downloadable = announcement.attachments.filter { it.downloadPath.isNotBlank() }
            if (downloadable.size > 1) {
                TextButton(
                    onClick = {
                        onDownloadBatch(
                            downloadable.map {
                                VirtualDownloadRequest(
                                    path = it.downloadPath,
                                    fileName = it.fileName,
                                    courseName = courseName
                                )
                            }
                        )
                    }
                ) {
                    Text("Baixar todos (${downloadable.size})", color = colors.accent, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        PortalCard(contentPadding = PaddingValues(0.dp)) {
            announcement.attachments.forEachIndexed { idx, att ->
                if (idx > 0) PortalDivider()
                AttachmentRow(
                    attachment = att,
                    isDownloading = att.downloadPath.isNotBlank() && downloadingFileKey == att.downloadPath,
                    onView = {
                        if (att.downloadPath.isNotBlank()) {
                            onViewD2LFile(att.downloadPath, att.fileName, courseName, att.fileName)
                        }
                    },
                    onDownload = {
                        if (att.downloadPath.isNotBlank()) {
                            onDownloadD2LFile(att.downloadPath, att.fileName, courseName)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AttachmentRow(
    attachment: VirtualFileAttachment,
    isDownloading: Boolean,
    onView: () -> Unit,
    onDownload: () -> Unit
) {
    val colors = LocalPortalColors.current
    val sizeLabel = formatBytes(attachment.size)
    val subtitle = if (sizeLabel.isNotBlank()) "$sizeLabel · Toque para abrir" else "Toque para abrir"
    PortalListRow(
        title = attachment.fileName,
        subtitle = subtitle,
        leadingIcon = PortalIcons.Document,
        leadingTint = colors.accent,
        trailing = {
            if (attachment.downloadPath.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceSecondary)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(enabled = !isDownloading) {
                            PlatformBridge.performHaptic(PortalHapticType.SELECTION)
                            onDownload()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = colors.accent,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = PortalIcons.Download,
                            contentDescription = "Baixar anexo",
                            tint = colors.accent,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        },
        onClick = onView
    )
}

@Composable
private fun assignmentStatusColor(status: String): Color {
    val colors = LocalPortalColors.current
    return when (status) {
        "graded" -> colors.green
        "submitted" -> colors.accent
        "expired" -> colors.textSecondary
        else -> colors.orange
    }
}

private fun formatIsoDateTime(raw: String, millisHint: Long = 0L): String {
    if (millisHint > 0L) {
        val formatted = PlatformBridge.formatEpochMillisLocal(millisHint)
        if (formatted.isNotBlank()) return formatted
    }
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val parsedMillis = parseIso8601ToEpochMillis(trimmed)
    if (parsedMillis != null && parsedMillis > 0L) {
        val local = PlatformBridge.formatEpochMillisLocal(parsedMillis)
        if (local.isNotBlank()) return local
    }
    if (trimmed.length < 10) return ""
    val datePart = trimmed.substring(0, 10)
    val parts = datePart.split("-")
    if (parts.size != 3) return trimmed
    return "${parts[2]}/${parts[1]}/${parts[0]}"
}

private fun formatScore(value: Double): String {
    val rounded = ((value * 100.0).toInt()) / 100.0
    val asInt = rounded.toInt()
    return if (rounded == asInt.toDouble()) asInt.toString() else rounded.toString().replace('.', ',')
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return ""
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        else -> "${((bytes * 10L) / (1024L * 1024L)) / 10.0} MB"
    }
}
