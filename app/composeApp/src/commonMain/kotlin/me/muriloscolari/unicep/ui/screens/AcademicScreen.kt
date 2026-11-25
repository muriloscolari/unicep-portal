package me.muriloscolari.unicep.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.muriloscolari.unicep.data.api.CurriculumResponse
import me.muriloscolari.unicep.data.api.ScheduleResponse
import me.muriloscolari.unicep.platform.DesignStyle
import me.muriloscolari.unicep.platform.PlatformVerticalScrollbar
import me.muriloscolari.unicep.ui.designsystem.AppleEmptyState
import me.muriloscolari.unicep.ui.designsystem.AppleSkeletonBox
import me.muriloscolari.unicep.ui.designsystem.LocalAppleContentInsets
import me.muriloscolari.unicep.ui.designsystem.LocalAppleTypography
import me.muriloscolari.unicep.ui.designsystem.LocalDesignStyle
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.ui.designsystem.PortalBadge
import me.muriloscolari.unicep.ui.designsystem.PortalCard
import me.muriloscolari.unicep.ui.designsystem.PortalDivider
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import me.muriloscolari.unicep.data.api.StudentInfo
import me.muriloscolari.unicep.data.repo.parseScheduleEntries
import me.muriloscolari.unicep.platform.buildScheduleExportData
import me.muriloscolari.unicep.ui.designsystem.ExportActionModal
import me.muriloscolari.unicep.ui.designsystem.GlassSurface
import me.muriloscolari.unicep.ui.designsystem.PortalIcons
import me.muriloscolari.unicep.ui.designsystem.PortalListRow
import me.muriloscolari.unicep.ui.designsystem.PortalSearchField
import me.muriloscolari.unicep.ui.designsystem.PortalSectionHeader
import me.muriloscolari.unicep.ui.designsystem.PortalSegmentedControl
import me.muriloscolari.unicep.ui.designsystem.PortalStatusDot
import me.muriloscolari.unicep.ui.designsystem.PortalTextField
import me.muriloscolari.unicep.ui.designsystem.ProgressRing
import me.muriloscolari.unicep.ui.designsystem.appleScrollBoundaryHaptics
import me.muriloscolari.unicep.ui.designsystem.tabularNumberStyle
import me.muriloscolari.unicep.ui.util.toDisplayTitle

private enum class CourseStatusFilter(val label: String) {
    ALL("Todas"),
    APPROVED("Aprovadas"),
    IN_PROGRESS("Cursando"),
    PENDING("Pendentes")
}

@Composable
fun AcademicScreen(
    curriculum: CurriculumResponse?,
    schedule: ScheduleResponse?,
    selectedSubTab: Int,
    onSelectSubTab: (Int) -> Unit,
    onOpenProxy: (String) -> Unit,
    student: StudentInfo = StudentInfo(),
    loading: Boolean = false,
    listState: LazyListState = rememberLazyListState()
) {
    val style = LocalDesignStyle.current
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val appleInsets = LocalAppleContentInsets.current
    val haptics = LocalHapticFeedback.current

    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf(CourseStatusFilter.ALL) }
    var showExportScheduleSheet by remember { mutableStateOf(false) }

    val transcript = curriculum?.transcript.orEmpty()
    val transcriptStatusByName = remember(transcript) {
        transcript.associate { it.name.trim().uppercase() to it.status }
    }
    val approvedCount = remember(transcript) { transcript.count { it.status == "A" } }
    val inProgressCount = remember(transcript) { transcript.count { it.status == "C" } }
    val totalCount = transcript.size
    val canExportSchedule = selectedSubTab == 2 && schedule != null && schedule.rows.isNotEmpty()
    val parsedScheduleEntries = remember(schedule) {
        parseScheduleEntries(schedule, emptyList())
    }

    fun matchesFilter(status: String?): Boolean = when (statusFilter) {
        CourseStatusFilter.ALL -> true
        CourseStatusFilter.APPROVED -> status == "A"
        CourseStatusFilter.IN_PROGRESS -> status == "C"
        CourseStatusFilter.PENDING -> status != "A" && status != "C"
    }

    val isApple = style == DesignStyle.APPLE
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
                    .widthIn(max = 980.dp)
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
                item(key = "course_title") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Curso",
                            color = colors.textPrimary,
                            style = if (isApple) appleType.largeTitle else MaterialTheme.typography.headlineLarge
                        )
                        if (canExportSchedule) {
                            if (isApple) {
                                GlassSurface(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            showExportScheduleSheet = true
                                        },
                                    shape = CircleShape,
                                    shadowElevation = 8.dp
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = PortalIcons.Share,
                                            contentDescription = "Exportar horário das aulas",
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
                                        .clickable {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            showExportScheduleSheet = true
                                        },
                                    shape = CircleShape,
                                    color = colors.surfaceSecondary
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = PortalIcons.Share,
                                            contentDescription = "Exportar horário das aulas",
                                            tint = colors.accent,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (loading && curriculum == null) {
                    item(key = "course_skeleton_hero") {
                        AppleSkeletonBox(Modifier.fillMaxWidth().height(126.dp))
                    }
                } else if (totalCount > 0) {
                    item(key = "course_progress_hero") {
                        val ratio = (approvedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
                        val pct = (ratio * 100f).toInt()
                        PortalCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                ProgressRing(
                                    progress = ratio,
                                    color = colors.green,
                                    size = 92.dp,
                                    strokeWidth = 9.dp,
                                    trackColor = colors.green.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = "$pct%",
                                        color = colors.textPrimary,
                                        style = tabularNumberStyle(appleType.title2)
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        text = "$approvedCount de $totalCount",
                                        color = colors.textPrimary,
                                        style = tabularNumberStyle(appleType.title2)
                                    )
                                    Text(
                                        text = "disciplinas concluídas",
                                        color = colors.textSecondary,
                                        style = appleType.subheadline
                                    )
                                    if (inProgressCount > 0) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "$inProgressCount em andamento neste semestre",
                                            color = colors.accent,
                                            style = appleType.footnote,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "course_segmented") {
                    PortalSegmentedControl(
                        options = listOf("Histórico", "Grade", "Horário"),
                        selectedIndex = selectedSubTab,
                        onSelect = { idx ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectSubTab(idx)
                        }
                    )
                }

                if (selectedSubTab < 2) {
                    item(key = "course_search") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PortalSearchField(
                                value = search,
                                onValueChange = { search = it },
                                placeholder = "Buscar disciplina"
                            )
                            CourseFilterChipsRow(
                                selected = statusFilter,
                                onSelect = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    statusFilter = it
                                },
                                isWide = isWide
                            )
                        }
                    }
                }

                if (loading && curriculum == null && selectedSubTab < 2) {
                    items(3) {
                        AppleSkeletonBox(Modifier.fillMaxWidth().height(72.dp))
                    }
                } else {
                    when (selectedSubTab) {
                        0 -> {
                            val filtered = transcript.filter {
                                (search.isBlank() || it.name.contains(search, ignoreCase = true)) &&
                                    matchesFilter(it.status)
                            }
                            if (filtered.isEmpty()) {
                                item(key = "empty_transcript") {
                                    AppleEmptyState(
                                        icon = PortalIcons.Book,
                                        title = "Nenhuma disciplina",
                                        subtitle = if (search.isBlank()) {
                                            "Seu histórico escolar ainda não possui registros disponíveis."
                                        } else {
                                            "Nenhuma matéria corresponde a \"$search\"."
                                        }
                                    )
                                }
                            } else {
                                val grouped = filtered
                                    .groupBy { formatYearSemHeader(it.yearSem) }
                                    .entries
                                    .sortedByDescending { parseYearSemSortKey(it.key) }

                                grouped.forEach { (periodTitle, itemsInPeriod) ->
                                    item(key = "hist_hdr_$periodTitle") {
                                        Spacer(Modifier.height(4.dp))
                                        PortalSectionHeader(periodTitle)
                                        PortalCard(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            itemsInPeriod.forEachIndexed { idx, item ->
                                                if (idx > 0) PortalDivider(Modifier.padding(start = 16.dp))
                                                val statusColor = when (item.status) {
                                                    "A" -> colors.green
                                                    "C" -> colors.accent
                                                    "R" -> colors.red
                                                    else -> colors.gray
                                                }
                                                val meta = listOfNotNull(
                                                    item.hours.takeIf { it.isNotBlank() }?.let { "${it}h" },
                                                    item.average.takeIf { it.isNotBlank() }?.let { "Média $it" },
                                                    item.freqPercent.takeIf { it.isNotBlank() }?.let { "Freq. $it%" }
                                                ).joinToString(" · ")
                                                val hasSyllabus = item.syllabusUrl.isNotBlank()

                                                PortalListRow(
                                                    title = item.name.toDisplayTitle(),
                                                    subtitle = meta.ifBlank { null },
                                                    onClick = if (hasSyllabus) ({ onOpenProxy(item.syllabusUrl) }) else null,
                                                    showChevron = false,
                                                    trailing = {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            if (item.status.isNotBlank()) {
                                                                PortalStatusDot(
                                                                    text = item.statusLabel.ifBlank { item.status },
                                                                    color = statusColor
                                                                )
                                                            }
                                                            if (hasSyllabus) {
                                                                Icon(
                                                                    imageVector = PortalIcons.ArrowUpRight,
                                                                    contentDescription = "Abrir ementa",
                                                                    tint = colors.accent,
                                                                    modifier = Modifier.size(15.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            val matrix = curriculum?.matrix.orEmpty().filter { row ->
                                val matchesQuery = search.isBlank() ||
                                    row.name.contains(search, ignoreCase = true) ||
                                    row.code.contains(search, ignoreCase = true)
                                val rowStatus = transcriptStatusByName[row.name.trim().uppercase()]
                                matchesQuery && matchesFilter(rowStatus)
                            }
                            if (matrix.isEmpty()) {
                                item(key = "empty_matrix") {
                                    AppleEmptyState(
                                        icon = PortalIcons.Document,
                                        title = "Matriz indisponível",
                                        subtitle = if (search.isBlank()) {
                                            "Não foi possível carregar a grade curricular do curso."
                                        } else {
                                            "Nenhuma disciplina corresponde a \"$search\"."
                                        }
                                    )
                                }
                            } else {
                                val grouped = matrix
                                    .groupBy { formatMatrixPeriodHeader(it.period) }
                                    .entries
                                    .sortedBy { parsePeriodOrder(it.key) }

                                grouped.forEach { (periodTitle, rows) ->
                                    item(key = "matrix_hdr_$periodTitle") {
                                        Spacer(Modifier.height(4.dp))
                                        PortalSectionHeader(periodTitle)
                                        PortalCard(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            SelectionContainer {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    rows.forEachIndexed { idx, row ->
                                                        if (idx > 0) PortalDivider(Modifier.padding(start = 16.dp))
                                                        val hasPrereq = row.prerequisite.isNotBlank() && row.prerequisite != "-"
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(
                                                                    text = row.name.toDisplayTitle(),
                                                                    color = colors.textPrimary,
                                                                    style = appleType.body,
                                                                    modifier = Modifier.weight(1f).padding(end = 10.dp)
                                                                )
                                                                if (row.hours.isNotBlank()) {
                                                                    Text(
                                                                        text = "${row.hours}h",
                                                                        color = colors.textSecondary,
                                                                        style = tabularNumberStyle(appleType.subheadline)
                                                                    )
                                                                }
                                                            }
                                                            if (row.code.isNotBlank()) {
                                                                Spacer(Modifier.height(2.dp))
                                                                Text(
                                                                    text = row.code,
                                                                    color = colors.textSecondary,
                                                                    style = appleType.footnote
                                                                )
                                                            }
                                                            if (hasPrereq) {
                                                                Spacer(Modifier.height(4.dp))
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = PortalIcons.Lock,
                                                                        contentDescription = null,
                                                                        tint = colors.tertiaryLabel,
                                                                        modifier = Modifier.size(12.dp)
                                                                    )
                                                                    Text(
                                                                        text = "Pré-req: ${row.prerequisite.toDisplayTitle()}",
                                                                        color = colors.textSecondary,
                                                                        style = appleType.caption1,
                                                                        maxLines = 2,
                                                                        overflow = TextOverflow.Ellipsis
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
                            }
                        }

                        else -> {
                            val rows = schedule?.rows.orEmpty()
                            val parsedEntries = parsedScheduleEntries
                            if (rows.isEmpty() && parsedEntries.isEmpty()) {
                                item(key = "empty_schedule") {
                                    AppleEmptyState(
                                        icon = PortalIcons.Calendar,
                                        title = "Sem aulas exibidas",
                                        subtitle = "Nenhum horário disponível no momento."
                                    )
                                }
                            } else if (parsedEntries.isNotEmpty() && parsedEntries.any { it.dayOfWeekIso > 0 }) {
                                val groupedByDay = parsedEntries.groupBy { it.dayLabel.ifBlank { "Horário" } }
                                if (isWideGrid) {
                                    val dayPairs = groupedByDay.entries.toList().chunked(2)
                                    dayPairs.forEachIndexed { rowIdx, pair ->
                                        item(key = "sched_row_$rowIdx") {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Box(modifier = Modifier.weight(1f)) {
                                                    ScheduleDayCard(
                                                        dayTitle = pair[0].key,
                                                        dayClasses = pair[0].value
                                                    )
                                                }
                                                if (pair.size > 1) {
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        ScheduleDayCard(
                                                            dayTitle = pair[1].key,
                                                            dayClasses = pair[1].value
                                                        )
                                                    }
                                                } else {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    groupedByDay.forEach { (dayTitle, dayClasses) ->
                                        item(key = "sched_day_$dayTitle") {
                                            ScheduleDayCard(dayTitle = dayTitle, dayClasses = dayClasses)
                                        }
                                    }
                                }
                            } else {
                                item(key = "schedule_card") {
                                    PortalCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        SelectionContainer {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                rows.forEachIndexed { idx, cols ->
                                                    if (idx > 0) PortalDivider(Modifier.padding(start = 16.dp))
                                                    val leftCol = cols.firstOrNull().orEmpty()
                                                    val rightMain = cols.getOrNull(1)?.takeIf { it.isNotBlank() }?.toDisplayTitle()
                                                    val rightMeta = cols.drop(2).filter { it.isNotBlank() }.joinToString(" · ")

                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .defaultMinSize(minHeight = 52.dp)
                                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = leftCol,
                                                            color = colors.textSecondary,
                                                            style = tabularNumberStyle(appleType.subheadline),
                                                            modifier = Modifier.width(84.dp)
                                                        )
                                                        Spacer(Modifier.width(10.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = rightMain ?: leftCol,
                                                                color = colors.textPrimary,
                                                                style = appleType.body
                                                            )
                                                            if (rightMeta.isNotBlank()) {
                                                                Spacer(Modifier.height(2.dp))
                                                                Text(
                                                                    text = rightMeta,
                                                                    color = colors.textSecondary,
                                                                    style = appleType.footnote
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
                        }
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
        if (showExportScheduleSheet && schedule != null) {
            ExportActionModal(
                fileName = "unicep_horario",
                data = buildScheduleExportData(student = student, schedule = schedule),
                onDismiss = { showExportScheduleSheet = false }
            )
        }
    }
}

@Composable
private fun ScheduleDayCard(
    dayTitle: String,
    dayClasses: List<me.muriloscolari.unicep.data.repo.ScheduleClassEntry>
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PortalSectionHeader(title = dayTitle)
        PortalCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(0.dp)
        ) {
            SelectionContainer {
                Column(modifier = Modifier.fillMaxWidth()) {
                    dayClasses.forEachIndexed { idx, entry ->
                        if (idx > 0) PortalDivider(Modifier.padding(start = 16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 50.dp)
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.timeLabel.ifBlank { "Horário" },
                                color = colors.textSecondary,
                                style = tabularNumberStyle(appleType.subheadline),
                                maxLines = 1,
                                modifier = Modifier.width(110.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.subjectName,
                                    color = colors.textPrimary,
                                    style = appleType.body
                                )
                                if (entry.roomOrMeta.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = entry.roomOrMeta,
                                        color = colors.textSecondary,
                                        style = appleType.footnote
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseFilterChipsRow(
    selected: CourseStatusFilter,
    onSelect: (CourseStatusFilter) -> Unit,
    isWide: Boolean
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val chipItems: @Composable () -> Unit = {
        CourseStatusFilter.entries.forEach { option ->
            val isSelected = option == selected
            Surface(
                shape = CircleShape,
                color = if (isSelected) colors.accent else colors.tertiaryFill,
                border = if (isSelected) null else BorderStroke(0.5.dp, colors.divider),
                modifier = Modifier
                    .clip(CircleShape)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { onSelect(option) }
            ) {
                Text(
                    text = option.label,
                    color = if (isSelected) androidx.compose.ui.graphics.Color.White else colors.textSecondary,
                    style = appleType.footnote,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
    if (isWide) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            chipItems()
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            chipItems()
        }
    }
}

private fun formatYearSemHeader(raw: String): String {
    val clean = raw.trim()
    if (clean.isBlank() || clean == "/") return "Outros períodos"
    val parts = clean.split('/').map { it.trim() }.filter { it.isNotBlank() }
    if (parts.size == 2) {
        val a = parts[0].toIntOrNull()
        val b = parts[1].toIntOrNull()
        if (a != null && b != null) {
            return if (a > 1000) "${b}º semestre de $a" else "${a}º semestre de $b"
        }
    }
    return clean
}

private fun parseYearSemSortKey(header: String): Int {
    val nums = Regex("\\d+").findAll(header).mapNotNull { it.value.toIntOrNull() }.toList()
    if (nums.size >= 2) {
        val year = nums.maxOrNull() ?: 0
        val sem = nums.minOrNull() ?: 0
        return year * 10 + sem
    }
    return nums.firstOrNull() ?: 0
}

private fun formatMatrixPeriodHeader(raw: String): String {
    val clean = raw.trim()
    if (clean.isBlank()) return "Disciplinas"
    val n = clean.toIntOrNull()
    return if (n != null) "${n}º período" else clean.toDisplayTitle()
}

private fun parsePeriodOrder(header: String): Int {
    return Regex("\\d+").find(header)?.value?.toIntOrNull() ?: 999
}
