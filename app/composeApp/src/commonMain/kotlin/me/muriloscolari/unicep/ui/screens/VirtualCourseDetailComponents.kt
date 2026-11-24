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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.muriloscolari.unicep.data.api.VirtualAnnouncement
import me.muriloscolari.unicep.data.api.VirtualAssignmentItem
import me.muriloscolari.unicep.data.api.VirtualCalendarEvent
import me.muriloscolari.unicep.data.api.VirtualContentModule
import me.muriloscolari.unicep.data.api.VirtualContentTopic
import me.muriloscolari.unicep.data.api.VirtualCourseDetailResponse
import me.muriloscolari.unicep.data.api.VirtualCourseSummary
import me.muriloscolari.unicep.data.api.VirtualDiscussionForum
import me.muriloscolari.unicep.data.api.VirtualGradeItem
import me.muriloscolari.unicep.data.api.VirtualQuizItem
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
import me.muriloscolari.unicep.ui.designsystem.PortalListRow
import me.muriloscolari.unicep.ui.designsystem.PortalPrimaryButton
import me.muriloscolari.unicep.ui.designsystem.PortalSectionHeader
import me.muriloscolari.unicep.ui.designsystem.PortalSegmentedControl
import me.muriloscolari.unicep.ui.designsystem.SelectableRichText
import me.muriloscolari.unicep.ui.designsystem.appleScrollBoundaryHaptics
import me.muriloscolari.unicep.ui.util.shouldDirectDownloadOnWeb
import me.muriloscolari.unicep.ui.util.toDisplayTitle

@Composable
internal fun VirtualCourseDetailBody(
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
    val scope = rememberCoroutineScope()

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

    val safeInitialTab = courseSubTab.coerceIn(0, VirtualCourseSubTab.entries.lastIndex)
    val subTabPagerState = rememberPagerState(
        initialPage = safeInitialTab,
        pageCount = { VirtualCourseSubTab.entries.size }
    )

    // External sub-tab changes (e.g. segmented control click, deep link, back press) animate pager
    LaunchedEffect(courseId, courseSubTab) {
        val target = courseSubTab.coerceIn(0, VirtualCourseSubTab.entries.lastIndex)
        if (subTabPagerState.currentPage != target) {
            subTabPagerState.animateScrollToPage(target)
        }
    }

    // Swiping the pager horizontally updates courseSubTab bidirectionally
    LaunchedEffect(subTabPagerState) {
        snapshotFlow { subTabPagerState.settledPage }
            .distinctUntilChanged()
            .collect { settled ->
                if (settled != courseSubTab) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectSubTab(settled)
                }
            }
    }

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

    val startPad = contentPadding.calculateLeftPadding(LayoutDirection.Ltr)
    val endPad = contentPadding.calculateRightPadding(LayoutDirection.Ltr)
    val topPad = contentPadding.calculateTopPadding()
    val bottomExtra = if (selectionMode || batchDownloadTotal > 0) 92.dp else 0.dp
    val pageBottomPad = contentPadding.calculateBottomPadding() + bottomExtra

    val assignmentsListState = remember(courseId) { listState }
    val contentListState = rememberLazyListState()
    val gradesListState = rememberLazyListState()
    val moreListState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Cabeçalho fixo da disciplina + seletor de sub-abas sincronizado com o HorizontalPager
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = startPad, end = endPad, top = topPad, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
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

                PortalSegmentedControl(
                    options = VirtualCourseSubTab.entries.map { it.label },
                    selectedIndex = subTabPagerState.currentPage.coerceIn(0, VirtualCourseSubTab.entries.lastIndex),
                    onSelect = { idx ->
                        onSelectSubTab(idx)
                        scope.launch { subTabPagerState.animateScrollToPage(idx) }
                    }
                )
            }

            if (!error.isNullOrBlank() && detail == null) {
                Box(modifier = Modifier.padding(horizontal = startPad, vertical = 4.dp)) {
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = startPad, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(4) {
                        AppleSkeletonBox(
                            modifier = Modifier.fillMaxWidth().height(70.dp),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            } else if (detail != null) {
                HorizontalPager(
                    state = subTabPagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) { pageIndex ->
                    val pageTab = VirtualCourseSubTab.entries.getOrElse(pageIndex) { VirtualCourseSubTab.ASSIGNMENTS }
                    val pageListState = when (pageTab) {
                        VirtualCourseSubTab.ASSIGNMENTS -> assignmentsListState
                        VirtualCourseSubTab.CONTENT -> contentListState
                        VirtualCourseSubTab.GRADES -> gradesListState
                        VirtualCourseSubTab.MORE -> moreListState
                    }
                    val pagePadding = PaddingValues(
                        start = startPad,
                        end = endPad,
                        top = 2.dp,
                        bottom = pageBottomPad
                    )

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = pageListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .appleScrollBoundaryHaptics(pageListState),
                            contentPadding = pagePadding,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            when (pageTab) {
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
                                                val isWeb = remember { PlatformBridge.capabilities().isWeb }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = when {
                                                            selectionMode && isWeb -> "Clique nos arquivos para marcar"
                                                            selectionMode -> "Toque nos arquivos para marcar"
                                                            isWeb -> "Use 'Selecionar' para baixar vários arquivos"
                                                            else -> "Segure um arquivo para selecionar vários"
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

                        PlatformVerticalScrollbar(
                            listState = pageListState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(vertical = 8.dp, horizontal = 3.dp)
                        )
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
internal fun CalendarEventRow(
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
    val appleType = LocalAppleTypography.current
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
                SelectableRichText(
                    text = module.description,
                    color = colors.textSecondary,
                    style = appleType.caption1
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
    val isWeb = remember { PlatformBridge.capabilities().isWeb }
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
    val actionHint = when {
        hasDownload && isWeb -> if (shouldDirectDownloadOnWeb(inferredName)) "clique para baixar" else "clique para abrir"
        hasDownload -> "toque para abrir"
        topic.externalUrl.isNotBlank() && isWeb -> "clique para abrir"
        topic.externalUrl.isNotBlank() -> "toque para abrir"
        else -> null
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
                text = if (actionHint != null) "$typeLabel · $actionHint" else typeLabel,
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
    SelectionContainer {
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
}

@Composable
private fun VirtualQuizCard(
    quiz: VirtualQuizItem,
    onOpenWeb: (String) -> Unit
) {
    val colors = LocalPortalColors.current
    PortalCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpenWeb(quiz.webUrl) }
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
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpenWeb(forum.webUrl) }
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
