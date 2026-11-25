package me.muriloscolari.unicep.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.muriloscolari.unicep.data.api.CurriculumResponse
import me.muriloscolari.unicep.data.api.ScheduleResponse
import me.muriloscolari.unicep.data.api.UserCalendarEvent
import me.muriloscolari.unicep.data.api.VirtualAssignmentItem
import me.muriloscolari.unicep.data.api.VirtualCourseDetailResponse
import me.muriloscolari.unicep.data.api.VirtualOverviewResponse
import me.muriloscolari.unicep.data.repo.GlobalSearchFilter
import me.muriloscolari.unicep.data.repo.GlobalSearchResult
import me.muriloscolari.unicep.data.repo.GlobalSearchResultKind
import me.muriloscolari.unicep.data.repo.ScheduleClassEntry
import me.muriloscolari.unicep.data.repo.TodaySummary
import me.muriloscolari.unicep.data.repo.searchPortalData
import me.muriloscolari.unicep.platform.DesignStyle
import me.muriloscolari.unicep.platform.PlatformBridge
import me.muriloscolari.unicep.platform.PlatformVerticalScrollbar
import me.muriloscolari.unicep.ui.designsystem.AppleSheet
import me.muriloscolari.unicep.ui.designsystem.AppleSheetDetent
import me.muriloscolari.unicep.ui.designsystem.CardStyle
import me.muriloscolari.unicep.ui.designsystem.LocalAppleTypography
import me.muriloscolari.unicep.ui.designsystem.LocalDesignStyle
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.ui.designsystem.PortalBadge
import me.muriloscolari.unicep.ui.designsystem.PortalCard
import me.muriloscolari.unicep.ui.designsystem.PortalDivider
import me.muriloscolari.unicep.ui.designsystem.PortalIcons
import me.muriloscolari.unicep.ui.designsystem.PortalSegmentedControl
import me.muriloscolari.unicep.ui.designsystem.tabularNumberStyle
import me.muriloscolari.unicep.ui.util.toDisplayTitle

@Composable
fun TodayOverviewCard(
    summary: TodaySummary,
    scheduleLoaded: Boolean,
    virtualLoaded: Boolean,
    onOpenSubject: (String) -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenVirtualAssignment: (VirtualAssignmentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val haptics = LocalHapticFeedback.current

    PortalCard(
        modifier = modifier.fillMaxWidth(),
        cardStyle = CardStyle.Grouped,
        contentPadding = PaddingValues(0.dp)
    ) {
        // Cabeçalho do painel "Hoje na UNICEP"
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
                    imageVector = PortalIcons.Calendar,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hoje na UNICEP",
                        color = colors.textPrimary,
                        style = appleType.headline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (summary.currentDayLabel.isNotBlank()) {
                        Text(
                            text = summary.currentDayLabel,
                            color = colors.textSecondary,
                            style = appleType.caption1,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            when {
                summary.currentClass != null -> {
                    PortalBadge(text = "Em aula", color = colors.green)
                }
                summary.todayClasses.isNotEmpty() -> {
                    val count = summary.todayClasses.size
                    PortalBadge(
                        text = if (count == 1) "1 aula" else "$count aulas",
                        color = colors.accent
                    )
                }
                scheduleLoaded -> {
                    PortalBadge(text = "Sem aula hoje", color = colors.textSecondary)
                }
            }
        }

        PortalDivider()

        // Aulas de hoje ou próxima aula
        when {
            !scheduleLoaded -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = PortalIcons.Clock,
                        contentDescription = null,
                        tint = colors.tertiaryLabel,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Carregando grade horária...",
                        color = colors.textSecondary,
                        style = appleType.footnote
                    )
                }
            }
            summary.todayClasses.isNotEmpty() -> {
                summary.todayClasses.forEachIndexed { idx, entry ->
                    if (idx > 0) PortalDivider(Modifier.padding(start = 16.dp))
                    val isCurrent = summary.currentClass == entry
                    val isNextToday = !isCurrent && summary.nextTodayClass == entry
                    TodayClassRow(
                        entry = entry,
                        isCurrent = isCurrent,
                        isNextToday = isNextToday,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val code = entry.matchedSubjectCode
                            if (code != null) onOpenSubject(code) else onOpenSchedule()
                        }
                    )
                }
                if (summary.nextWeekClass != null) {
                    PortalDivider(Modifier.padding(start = 16.dp))
                    NextWeekClassRow(
                        entry = summary.nextWeekClass,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenSchedule()
                        }
                    )
                }
            }
            summary.nextWeekClass != null -> {
                NextWeekClassRow(
                    entry = summary.nextWeekClass,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenSchedule()
                    }
                )
            }
            else -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { onOpenSchedule() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Nenhuma aula programada para agora.",
                        color = colors.textSecondary,
                        style = appleType.footnote,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = PortalIcons.ChevronRight,
                        contentDescription = null,
                        tint = colors.tertiaryLabel,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        // Prazos dos próximos 7 dias (Sala Virtual)
        if (virtualLoaded) {
            PortalDivider()
            if (summary.upcomingDeadlines.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PRAZOS NOS PRÓXIMOS 7 DIAS",
                        color = colors.textSecondary,
                        style = appleType.caption2,
                        fontWeight = FontWeight.SemiBold
                    )
                    PortalBadge(
                        text = "${summary.upcomingDeadlines.size}",
                        color = colors.orange
                    )
                }
                summary.upcomingDeadlines.forEachIndexed { idx, item ->
                    if (idx > 0) PortalDivider(Modifier.padding(start = 16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onOpenVirtualAssignment(item)
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
                            val meta = listOfNotNull(
                                item.orgUnitName.takeIf { it.isNotBlank() }?.toDisplayTitle(),
                                item.dueDate.takeIf { it.isNotBlank() }
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
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = PortalIcons.CheckCircle,
                        contentDescription = null,
                        tint = colors.green,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Nenhum prazo pendente nos próximos 7 dias",
                        color = colors.textSecondary,
                        style = appleType.footnote
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayClassRow(
    entry: ScheduleClassEntry,
    isCurrent: Boolean,
    isNextToday: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.width(86.dp)) {
            Text(
                text = entry.timeLabel.ifBlank { "Horário" },
                color = if (isCurrent) colors.green else if (isNextToday) colors.accent else colors.textSecondary,
                style = tabularNumberStyle(appleType.footnote),
                fontWeight = if (isCurrent || isNextToday) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isCurrent) {
                Text(
                    text = "Agora",
                    color = colors.green,
                    style = appleType.caption2,
                    fontWeight = FontWeight.SemiBold
                )
            } else if (isNextToday) {
                Text(
                    text = "A seguir",
                    color = colors.accent,
                    style = appleType.caption2,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.subjectName,
                color = colors.textPrimary,
                style = appleType.subheadline,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.roomOrMeta.isNotBlank()) {
                Text(
                    text = entry.roomOrMeta,
                    color = colors.textSecondary,
                    style = appleType.caption1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        val rem = entry.remainingAbsences
        if (rem != null) {
            val badgeColor = when {
                rem <= 0 -> colors.red
                entry.isFrequencyAtLimit -> colors.orange
                else -> colors.green
            }
            val badgeText = when {
                rem <= 0 -> "Sem faltas"
                rem == 1 -> "1 falta rest."
                else -> "$rem faltas rest."
            }
            PortalBadge(text = badgeText, color = badgeColor)
        } else {
            Icon(
                imageVector = PortalIcons.ChevronRight,
                contentDescription = null,
                tint = colors.tertiaryLabel,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun NextWeekClassRow(
    entry: ScheduleClassEntry,
    onClick: () -> Unit
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = PortalIcons.Clock,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Próxima aula: ${entry.dayLabel}${if (entry.timeLabel.isNotBlank()) " · ${entry.timeLabel}" else ""}",
                color = colors.accent,
                style = appleType.caption1,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = entry.subjectName,
                color = colors.textPrimary,
                style = appleType.subheadline,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.roomOrMeta.isNotBlank()) {
                Text(
                    text = entry.roomOrMeta,
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

@Composable
fun GlobalSearchSheet(
    subjects: List<UnifiedSubject>,
    schedule: ScheduleResponse?,
    curriculum: CurriculumResponse?,
    virtualOverview: VirtualOverviewResponse?,
    virtualCourseDetails: Map<Long, VirtualCourseDetailResponse>,
    calendarEvents: List<UserCalendarEvent> = emptyList(),
    onSelectResult: (GlobalSearchResult) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val haptics = LocalHapticFeedback.current

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(GlobalSearchFilter.ALL) }
    val focusRequester = remember { FocusRequester() }
    val resultsListState = rememberLazyListState()
    val isWeb = remember { PlatformBridge.capabilities().isWeb }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    val results = remember(
        query,
        filter,
        subjects,
        schedule,
        curriculum,
        virtualOverview,
        virtualCourseDetails,
        calendarEvents
    ) {
        searchPortalData(
            query = query,
            filter = filter,
            subjects = subjects,
            schedule = schedule,
            curriculum = curriculum,
            virtualOverview = virtualOverview,
            virtualCourseDetails = virtualCourseDetails,
            calendarEvents = calendarEvents
        )
    }

    AppleSheet(
        title = "Busca Global",
        subtitle = if (isWeb) "Notas, horários, matriz e Sala Virtual · Esc para fechar" else "Notas, horários, matriz e Sala Virtual",
        onDismiss = onDismiss,
        initialDetent = AppleSheetDetent.Large,
        desktopMaxWidth = 680.dp
    ) {
        // Campo de busca
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.secondaryFill)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = PortalIcons.Search,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(17.dp)
            )
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Buscar disciplina, tarefa, aviso, sala...",
                        color = colors.tertiaryLabel,
                        style = appleType.body
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = appleType.body.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerHoverIcon(PointerIcon.Text)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown &&
                                (ev.key == Key.Enter || ev.key == Key.NumPadEnter) &&
                                results.isNotEmpty()
                            ) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectResult(results.first())
                                true
                            } else false
                        }
                )
            }
            if (isWeb && results.isNotEmpty()) {
                Text(
                    text = "Enter abre",
                    color = colors.tertiaryLabel,
                    style = appleType.caption2
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    imageVector = PortalIcons.Close,
                    contentDescription = "Limpar busca",
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .size(17.dp)
                        .clip(CircleShape)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { query = "" }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        PortalSegmentedControl(
            options = GlobalSearchFilter.entries.map { it.label },
            selectedIndex = GlobalSearchFilter.entries.indexOf(filter).coerceAtLeast(0),
            onSelect = { idx ->
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                filter = GlobalSearchFilter.entries.getOrElse(idx) { GlobalSearchFilter.ALL }
            }
        )

        Spacer(Modifier.height(12.dp))

        when {
            query.trim().length < 2 -> {
                PortalCard(
                    modifier = Modifier.fillMaxWidth(),
                    cardStyle = CardStyle.Grouped,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Text(
                        text = "Pesquisar no portal",
                        color = colors.textPrimary,
                        style = appleType.headline
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Digite pelo menos 2 caracteres para buscar disciplinas, horários, atividades ou avisos.",
                        color = colors.textSecondary,
                        style = appleType.footnote
                    )
                    Spacer(Modifier.height(10.dp))
                    val virtualCount = virtualOverview?.courses?.size ?: 0
                    val assignCount = virtualOverview?.upcomingAssignments?.size ?: 0
                    Text(
                        text = "${subjects.size} disciplinas · $virtualCount salas virtuais · $assignCount tarefas",
                        color = colors.accent,
                        style = appleType.caption1,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            results.isEmpty() -> {
                PortalCard(
                    modifier = Modifier.fillMaxWidth(),
                    cardStyle = CardStyle.Grouped,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Text(
                        text = "Nenhum resultado para \"${query.trim()}\"",
                        color = colors.textPrimary,
                        style = appleType.headline
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Tente outro termo ou mude o filtro de categoria acima.",
                        color = colors.textSecondary,
                        style = appleType.footnote
                    )
                }
            }

            else -> {
                PortalCard(
                    modifier = Modifier.fillMaxWidth(),
                    cardStyle = CardStyle.Grouped,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp)
                    ) {
                        LazyColumn(
                            state = resultsListState,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(results, key = { _, item -> item.id }) { idx, item ->
                                if (idx > 0) PortalDivider(Modifier.padding(start = 16.dp))
                                val badgeColor = when (item.kind) {
                                    GlobalSearchResultKind.SUBJECT -> colors.accent
                                    GlobalSearchResultKind.SCHEDULE -> colors.indigo
                                    GlobalSearchResultKind.CURRICULUM -> colors.teal
                                    GlobalSearchResultKind.VIRTUAL_COURSE -> colors.accent
                                    GlobalSearchResultKind.VIRTUAL_ASSIGNMENT -> colors.orange
                                    GlobalSearchResultKind.VIRTUAL_ANNOUNCEMENT -> colors.indigo
                                    GlobalSearchResultKind.VIRTUAL_MATERIAL -> colors.green
                                    GlobalSearchResultKind.CALENDAR_EVENT -> colors.accent
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSelectResult(item)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            PortalBadge(text = item.badge, color = badgeColor)
                                            if (item.isAlert) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(colors.orange)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = item.title,
                                            color = colors.textPrimary,
                                            style = appleType.subheadline,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (item.subtitle.isNotBlank()) {
                                            Text(
                                                text = item.subtitle,
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
                        }
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .padding(vertical = 6.dp, horizontal = 3.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            PlatformVerticalScrollbar(
                                listState = resultsListState,
                                modifier = Modifier.fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}
