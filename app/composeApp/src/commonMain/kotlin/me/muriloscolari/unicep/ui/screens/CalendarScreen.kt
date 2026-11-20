package me.muriloscolari.unicep.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.muriloscolari.unicep.data.api.CalendarEventInput
import me.muriloscolari.unicep.data.api.UserCalendarEvent
import me.muriloscolari.unicep.data.api.VirtualAssignmentItem
import me.muriloscolari.unicep.data.api.VirtualCourseDetailResponse
import me.muriloscolari.unicep.data.api.VirtualOverviewResponse
import me.muriloscolari.unicep.data.repo.CalendarAgendaItem
import me.muriloscolari.unicep.data.repo.CalendarDate
import me.muriloscolari.unicep.data.repo.CalendarFilter
import me.muriloscolari.unicep.data.repo.CalendarItemSource
import me.muriloscolari.unicep.data.repo.PRESET_CALENDAR_REMINDER_MINUTES
import me.muriloscolari.unicep.data.repo.addDays
import me.muriloscolari.unicep.data.repo.buildCalendarAgendaItems
import me.muriloscolari.unicep.data.repo.buildMonthGrid
import me.muriloscolari.unicep.data.repo.filterCalendarItems
import me.muriloscolari.unicep.data.repo.filterSubjectSuggestions
import me.muriloscolari.unicep.data.repo.formatBrDate
import me.muriloscolari.unicep.data.repo.formatCalendarReminderChipLabel
import me.muriloscolari.unicep.data.repo.formatCalendarReminderLabel
import me.muriloscolari.unicep.data.repo.formatIsoDate
import me.muriloscolari.unicep.data.repo.fullDateLabelPt
import me.muriloscolari.unicep.data.repo.groupCalendarItemsByDate
import me.muriloscolari.unicep.data.repo.localDateTimeToEpochMillis
import me.muriloscolari.unicep.data.repo.monthNamePt
import me.muriloscolari.unicep.data.repo.nextMonth
import me.muriloscolari.unicep.data.repo.normalizeSearchText
import me.muriloscolari.unicep.data.repo.parseBrDate
import me.muriloscolari.unicep.data.repo.parseHourMinute
import me.muriloscolari.unicep.data.repo.parseIsoDate
import me.muriloscolari.unicep.data.repo.previousMonth
import me.muriloscolari.unicep.data.repo.upcomingCalendarItems
import me.muriloscolari.unicep.platform.DesignStyle
import me.muriloscolari.unicep.platform.PlatformBridge
import me.muriloscolari.unicep.platform.PlatformVerticalScrollbar
import me.muriloscolari.unicep.platform.PortalHapticType
import me.muriloscolari.unicep.ui.designsystem.AppleActivityIndicator
import me.muriloscolari.unicep.ui.designsystem.AppleSheet
import me.muriloscolari.unicep.ui.designsystem.AppleSheetDetent
import me.muriloscolari.unicep.ui.designsystem.CardStyle
import me.muriloscolari.unicep.ui.designsystem.CopyTextButton
import me.muriloscolari.unicep.ui.designsystem.LocalAppleContentInsets
import me.muriloscolari.unicep.ui.designsystem.LocalAppleTypography
import me.muriloscolari.unicep.ui.designsystem.LocalDesignStyle
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.ui.designsystem.PortalBadge
import me.muriloscolari.unicep.ui.designsystem.PortalCard
import me.muriloscolari.unicep.ui.designsystem.PortalColors
import me.muriloscolari.unicep.ui.designsystem.PortalDivider
import me.muriloscolari.unicep.ui.designsystem.PortalIcons
import me.muriloscolari.unicep.ui.designsystem.PortalPrimaryButton
import me.muriloscolari.unicep.ui.designsystem.PortalSecondaryButton
import me.muriloscolari.unicep.ui.designsystem.PortalSectionHeader
import me.muriloscolari.unicep.ui.designsystem.PortalTextButton
import me.muriloscolari.unicep.ui.designsystem.appleScrollBoundaryHaptics
import me.muriloscolari.unicep.ui.designsystem.tabularNumberStyle

private data class CalendarColorOption(
    val key: String,
    val label: String
)

private val CALENDAR_COLOR_OPTIONS = listOf(
    CalendarColorOption("accent", "Azul"),
    CalendarColorOption("red", "Prova"),
    CalendarColorOption("orange", "Entrega"),
    CalendarColorOption("green", "Estudo"),
    CalendarColorOption("indigo", "Pessoal"),
    CalendarColorOption("teal", "Outro")
)

private fun resolveCalendarColor(colors: PortalColors, key: String): Color = when (key) {
    "red" -> colors.red
    "orange" -> colors.orange
    "green" -> colors.green
    "indigo" -> colors.indigo
    "teal" -> colors.teal
    else -> colors.accent
}

fun currentLocalCalendarDate(): CalendarDate {
    val info = PlatformBridge.currentLocalDateTime()
    return CalendarDate(
        year = info.year.coerceIn(1970, 2100),
        month = info.month.coerceIn(1, 12),
        day = info.day.coerceIn(1, 31)
    )
}

@Composable
fun CalendarScreen(
    userEvents: List<UserCalendarEvent>,
    virtualOverview: VirtualOverviewResponse?,
    virtualCourseDetails: Map<Long, VirtualCourseDetailResponse>,
    selectedDate: CalendarDate,
    onSelectDate: (CalendarDate) -> Unit,
    onAddEvent: (CalendarDate) -> Unit,
    onEditEvent: (UserCalendarEvent) -> Unit,
    onRequestDeleteEvent: (UserCalendarEvent) -> Unit,
    onOpenVirtualAssignment: (VirtualAssignmentItem) -> Unit,
    onOpenWeb: (String) -> Unit,
    loading: Boolean = false,
    listState: LazyListState = rememberLazyListState()
) {
    val colors = LocalPortalColors.current
    val style = LocalDesignStyle.current
    val appleType = LocalAppleTypography.current
    val appleInsets = LocalAppleContentInsets.current
    val isApple = style == DesignStyle.APPLE

    val localInfo = remember { PlatformBridge.currentLocalDateTime() }
    val today = remember(localInfo) {
        CalendarDate(
            year = localInfo.year.coerceIn(1970, 2100),
            month = localInfo.month.coerceIn(1, 12),
            day = localInfo.day.coerceIn(1, 31)
        )
    }

    var displayedYearMonth by remember { mutableStateOf(selectedDate.year to selectedDate.month) }
    LaunchedEffect(selectedDate.year, selectedDate.month) {
        displayedYearMonth = selectedDate.year to selectedDate.month
    }

    var activeFilter by remember { mutableStateOf(CalendarFilter.ALL) }

    val allItems = remember(userEvents, virtualOverview, virtualCourseDetails, localInfo.utcOffsetMinutes) {
        buildCalendarAgendaItems(
            userEvents = userEvents,
            virtualOverview = virtualOverview,
            virtualCourseDetails = virtualCourseDetails,
            utcOffsetMinutes = localInfo.utcOffsetMinutes
        )
    }

    val displayedYear = displayedYearMonth.first
    val displayedMonth = displayedYearMonth.second
    val monthAllCount = remember(allItems, displayedYear, displayedMonth) {
        allItems.count { it.date.year == displayedYear && it.date.month == displayedMonth }
    }
    val monthVirtualCount = remember(allItems, displayedYear, displayedMonth) {
        allItems.count { it.date.year == displayedYear && it.date.month == displayedMonth && it.source != CalendarItemSource.PERSONAL }
    }
    val monthPersonalCount = remember(allItems, displayedYear, displayedMonth) {
        allItems.count { it.date.year == displayedYear && it.date.month == displayedMonth && it.source == CalendarItemSource.PERSONAL }
    }

    val filteredItems = remember(allItems, activeFilter) {
        filterCalendarItems(allItems, activeFilter)
    }
    val itemsByDate = remember(filteredItems) {
        groupCalendarItemsByDate(filteredItems)
    }
    val selectedDayItems = itemsByDate[selectedDate].orEmpty()
    val upcomingItems = remember(filteredItems, today) {
        upcomingCalendarItems(filteredItems, fromDate = today, days = 14)
    }

    val monthGrid = remember(displayedYear, displayedMonth) {
        buildMonthGrid(displayedYear, displayedMonth)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(colors.background)) {
        val isWide = maxWidth >= 640.dp
        val isWideSplit = maxWidth >= 920.dp
        val horizontalPad = if (isWide) 24.dp else 16.dp
        val topPad = if (isApple) appleInsets.calculateTopPadding() else 12.dp
        val bottomPad = if (isApple) appleInsets.calculateBottomPadding() else 24.dp

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 1080.dp)
                    .fillMaxSize()
                    .appleScrollBoundaryHaptics(listState),
                contentPadding = PaddingValues(
                    start = horizontalPad,
                    end = horizontalPad,
                    top = topPad,
                    bottom = bottomPad
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Header
                item(key = "calendar_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Calendário",
                                    color = colors.textPrimary,
                                    style = if (isApple) appleType.largeTitle else MaterialTheme.typography.headlineLarge
                                )
                                if (loading) {
                                    AppleActivityIndicator(size = 18.dp, color = colors.accent)
                                }
                            }
                            Text(
                                text = if (monthAllCount == 0) {
                                    "Prazos do Virtual e eventos pessoais"
                                } else {
                                    "$monthAllCount compromisso(s) em ${monthNamePt(displayedMonth).lowercase()}"
                                },
                                color = colors.textSecondary,
                                style = appleType.footnote
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectedDate != today || displayedYearMonth != (today.year to today.month)) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(colors.surfaceSecondary)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable {
                                            PlatformBridge.performHaptic(PortalHapticType.SELECTION)
                                            displayedYearMonth = today.year to today.month
                                            onSelectDate(today)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Hoje",
                                        color = colors.accent,
                                        style = appleType.footnote,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(colors.accent)
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable {
                                        PlatformBridge.performHaptic(PortalHapticType.SELECTION)
                                        onAddEvent(selectedDate)
                                    }
                                    .padding(horizontal = 13.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = PortalIcons.Plus,
                                        contentDescription = "Novo evento",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Evento",
                                        color = Color.White,
                                        style = appleType.footnote,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // Filter pills
                item(key = "calendar_filters") {
                    CalendarFilterChipsRow(
                        activeFilter = activeFilter,
                        monthAllCount = monthAllCount,
                        monthVirtualCount = monthVirtualCount,
                        monthPersonalCount = monthPersonalCount,
                        isWide = isWide,
                        onSelect = { activeFilter = it }
                    )
                }

                if (isWideSplit) {
                    item(key = "calendar_desktop_split") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(modifier = Modifier.weight(1.05f)) {
                                CalendarMonthGridCard(
                                    displayedYear = displayedYear,
                                    displayedMonth = displayedMonth,
                                    selectedDate = selectedDate,
                                    today = today,
                                    monthGrid = monthGrid,
                                    itemsByDate = itemsByDate,
                                    onPreviousMonth = {
                                        displayedYearMonth = previousMonth(displayedYear, displayedMonth)
                                    },
                                    onNextMonth = {
                                        displayedYearMonth = nextMonth(displayedYear, displayedMonth)
                                    },
                                    onSelectDate = onSelectDate
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                SelectedDayAgendaSection(
                                    selectedDate = selectedDate,
                                    selectedDayItems = selectedDayItems,
                                    onAddEvent = onAddEvent,
                                    onEditEvent = onEditEvent,
                                    onRequestDeleteEvent = onRequestDeleteEvent,
                                    onOpenVirtualAssignment = onOpenVirtualAssignment,
                                    onOpenWeb = onOpenWeb
                                )
                                Upcoming14DaysSection(
                                    today = today,
                                    upcomingItems = upcomingItems,
                                    onSelectDate = onSelectDate,
                                    onOpenVirtualAssignment = onOpenVirtualAssignment,
                                    onOpenWeb = onOpenWeb,
                                    onEditEvent = onEditEvent
                                )
                            }
                        }
                    }
                } else {
                    item(key = "calendar_month_grid") {
                        CalendarMonthGridCard(
                            displayedYear = displayedYear,
                            displayedMonth = displayedMonth,
                            selectedDate = selectedDate,
                            today = today,
                            monthGrid = monthGrid,
                            itemsByDate = itemsByDate,
                            onPreviousMonth = {
                                displayedYearMonth = previousMonth(displayedYear, displayedMonth)
                            },
                            onNextMonth = {
                                displayedYearMonth = nextMonth(displayedYear, displayedMonth)
                            },
                            onSelectDate = onSelectDate
                        )
                    }

                    item(key = "selected_day_section") {
                        SelectedDayAgendaSection(
                            selectedDate = selectedDate,
                            selectedDayItems = selectedDayItems,
                            onAddEvent = onAddEvent,
                            onEditEvent = onEditEvent,
                            onRequestDeleteEvent = onRequestDeleteEvent,
                            onOpenVirtualAssignment = onOpenVirtualAssignment,
                            onOpenWeb = onOpenWeb
                        )
                    }

                    item(key = "upcoming_14_days") {
                        Upcoming14DaysSection(
                            today = today,
                            upcomingItems = upcomingItems,
                            onSelectDate = onSelectDate,
                            onOpenVirtualAssignment = onOpenVirtualAssignment,
                            onOpenWeb = onOpenWeb,
                            onEditEvent = onEditEvent
                        )
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
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarFilterChipsRow(
    activeFilter: CalendarFilter,
    monthAllCount: Int,
    monthVirtualCount: Int,
    monthPersonalCount: Int,
    isWide: Boolean,
    onSelect: (CalendarFilter) -> Unit
) {
    val colors = LocalPortalColors.current
    val style = LocalDesignStyle.current
    val appleType = LocalAppleTypography.current
    val isApple = style == DesignStyle.APPLE
    val options = listOf(
        Triple(CalendarFilter.ALL, "Tudo", monthAllCount),
        Triple(CalendarFilter.VIRTUAL, "Prazos Virtual", monthVirtualCount),
        Triple(CalendarFilter.PERSONAL, "Meus eventos", monthPersonalCount)
    )
    val chipsContent: @Composable () -> Unit = {
        options.forEach { (filter, label, count) ->
            val selected = activeFilter == filter
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) colors.accent else colors.surface)
                    .then(
                        if (!selected && !isApple) {
                            Modifier.border(1.dp, colors.divider.copy(alpha = 0.45f), CircleShape)
                        } else Modifier
                    )
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable {
                        PlatformBridge.performHaptic(PortalHapticType.SELECTION)
                        onSelect(filter)
                    }
                    .padding(horizontal = 13.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$label ($count)",
                    color = if (selected) Color.White else colors.textPrimary,
                    style = tabularNumberStyle(appleType.footnote),
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
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
            chipsContent()
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            chipsContent()
        }
    }
}

@Composable
private fun CalendarMonthGridCard(
    displayedYear: Int,
    displayedMonth: Int,
    selectedDate: CalendarDate,
    today: CalendarDate,
    monthGrid: List<CalendarDate?>,
    itemsByDate: Map<CalendarDate, List<CalendarAgendaItem>>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (CalendarDate) -> Unit
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    PortalCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceSecondary)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable {
                        PlatformBridge.performHaptic(PortalHapticType.SELECTION)
                        onPreviousMonth()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = PortalIcons.ChevronLeft,
                    contentDescription = "Mês anterior",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = "${monthNamePt(displayedMonth)} $displayedYear",
                color = colors.textPrimary,
                style = appleType.headline,
                fontWeight = FontWeight.SemiBold
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceSecondary)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable {
                        PlatformBridge.performHaptic(PortalHapticType.SELECTION)
                        onNextMonth()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = PortalIcons.ChevronRight,
                    contentDescription = "Próximo mês",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        val weekHeaders = listOf("D", "S", "T", "Q", "Q", "S", "S")
        Row(modifier = Modifier.fillMaxWidth()) {
            weekHeaders.forEach { d ->
                Text(
                    text = d,
                    color = colors.textSecondary,
                    style = appleType.caption2,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        val weeks = monthGrid.chunked(7)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            weeks.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    week.forEach { day ->
                        if (day == null) {
                            Spacer(Modifier.weight(1f).height(46.dp))
                        } else {
                            val isSelected = day == selectedDate
                            val isToday = day == today
                            val dayEvents = itemsByDate[day].orEmpty()
                            val dotColors = dayEvents
                                .map { resolveCalendarColor(colors, it.colorKey) }
                                .distinct()
                                .take(3)

                            val cellBg = when {
                                isSelected -> colors.accent
                                isToday -> colors.accentSoft
                                else -> Color.Transparent
                            }
                            val borderMod = if (isToday && !isSelected) {
                                Modifier.border(1.2.dp, colors.accent, RoundedCornerShape(12.dp))
                            } else Modifier

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(cellBg)
                                    .then(borderMod)
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable {
                                        PlatformBridge.performHaptic(PortalHapticType.SELECTION)
                                        onSelectDate(day)
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = day.day.toString(),
                                    color = when {
                                        isSelected -> Color.White
                                        isToday -> colors.accent
                                        else -> colors.textPrimary
                                    },
                                    style = tabularNumberStyle(appleType.subheadline),
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium
                                )
                                Spacer(Modifier.height(3.dp))
                                if (dotColors.isNotEmpty()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        dotColors.forEach { dotColor ->
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) Color.White.copy(alpha = 0.92f) else dotColor)
                                            )
                                        }
                                    }
                                } else {
                                    Spacer(Modifier.height(5.dp))
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
private fun SelectedDayAgendaSection(
    selectedDate: CalendarDate,
    selectedDayItems: List<CalendarAgendaItem>,
    onAddEvent: (CalendarDate) -> Unit,
    onEditEvent: (UserCalendarEvent) -> Unit,
    onRequestDeleteEvent: (UserCalendarEvent) -> Unit,
    onOpenVirtualAssignment: (VirtualAssignmentItem) -> Unit,
    onOpenWeb: (String) -> Unit
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PortalSectionHeader(
            title = fullDateLabelPt(selectedDate),
            trailing = {
                PortalTextButton(
                    text = "+ Adicionar",
                    onClick = { onAddEvent(selectedDate) }
                )
            }
        )
        if (selectedDayItems.isEmpty()) {
            PortalCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(18.dp)
            ) {
                Text(
                    text = "Nenhum prazo ou evento neste dia.",
                    color = colors.textPrimary,
                    style = appleType.headline
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Adicione provas, trabalhos, revisões ou lembretes para ser notificado no horário.",
                    color = colors.textSecondary,
                    style = appleType.footnote
                )
                Spacer(Modifier.height(12.dp))
                PortalSecondaryButton(
                    text = "Criar evento em ${formatBrDate(selectedDate)}",
                    onClick = { onAddEvent(selectedDate) }
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                selectedDayItems.forEach { item ->
                    CalendarAgendaCard(
                        item = item,
                        onEditEvent = onEditEvent,
                        onRequestDeleteEvent = onRequestDeleteEvent,
                        onOpenVirtualAssignment = onOpenVirtualAssignment,
                        onOpenWeb = onOpenWeb
                    )
                }
            }
        }
    }
}

@Composable
private fun Upcoming14DaysSection(
    today: CalendarDate,
    upcomingItems: List<CalendarAgendaItem>,
    onSelectDate: (CalendarDate) -> Unit,
    onOpenVirtualAssignment: (VirtualAssignmentItem) -> Unit,
    onOpenWeb: (String) -> Unit,
    onEditEvent: (UserCalendarEvent) -> Unit
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PortalSectionHeader("Próximos 14 dias")
        if (upcomingItems.isEmpty()) {
            PortalCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text(
                    text = "Nenhum prazo ou compromisso nos próximos 14 dias.",
                    color = colors.textSecondary,
                    style = appleType.footnote
                )
            }
        } else {
            PortalCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp)
            ) {
                upcomingItems.forEachIndexed { idx, item ->
                    if (idx > 0) PortalDivider(Modifier.padding(start = 16.dp))
                    val dayBadge = when (item.date) {
                        today -> "Hoje"
                        addDays(today, 1) -> "Amanhã"
                        else -> "${item.date.day.toString().padStart(2, '0')}/${item.date.month.toString().padStart(2, '0')}"
                    }
                    val itemColor = resolveCalendarColor(colors, item.colorKey)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable {
                                onSelectDate(item.date)
                                when {
                                    item.assignment != null -> onOpenVirtualAssignment(item.assignment)
                                    item.webUrl.isNotBlank() -> onOpenWeb(item.webUrl)
                                    item.userEvent != null -> onEditEvent(item.userEvent)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(32.dp)
                                .clip(CircleShape)
                                .background(itemColor)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                color = colors.textPrimary,
                                style = appleType.subheadline,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val meta = listOfNotNull(
                                dayBadge,
                                item.timeLabel.takeIf { it.isNotBlank() },
                                item.subtitle.takeIf { it.isNotBlank() }
                            ).joinToString(" · ")
                            Text(
                                text = meta,
                                color = colors.textSecondary,
                                style = tabularNumberStyle(appleType.caption1),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (item.statusBadge.isNotBlank()) {
                            PortalBadge(text = item.statusBadge, color = itemColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarAgendaCard(
    item: CalendarAgendaItem,
    onEditEvent: (UserCalendarEvent) -> Unit,
    onRequestDeleteEvent: (UserCalendarEvent) -> Unit,
    onOpenVirtualAssignment: (VirtualAssignmentItem) -> Unit,
    onOpenWeb: (String) -> Unit
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val accentColor = resolveCalendarColor(colors, item.colorKey)

    val cardClick: (() -> Unit)? = when {
        item.userEvent != null -> { { onEditEvent(item.userEvent) } }
        item.assignment != null -> { { onOpenVirtualAssignment(item.assignment) } }
        item.webUrl.isNotBlank() -> { { onOpenWeb(item.webUrl) } }
        else -> null
    }

    PortalCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = cardClick,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .width(4.dp)
                    .height(42.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (item.timeLabel.isNotBlank()) {
                            Text(
                                text = item.timeLabel,
                                color = accentColor,
                                style = tabularNumberStyle(appleType.caption1),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (item.statusBadge.isNotBlank()) {
                            PortalBadge(text = item.statusBadge, color = accentColor)
                        }
                    }
                    if (item.userEvent != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(colors.surfaceSecondary)
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable { onEditEvent(item.userEvent) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = PortalIcons.Edit,
                                    contentDescription = "Editar evento",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(colors.red.copy(alpha = 0.12f))
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable { onRequestDeleteEvent(item.userEvent) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = PortalIcons.Trash,
                                    contentDescription = "Excluir evento",
                                    tint = colors.red,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    } else if (cardClick != null) {
                        Icon(
                            imageVector = if (item.assignment != null) PortalIcons.ChevronRight else PortalIcons.ArrowUpRight,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                val infoContent: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = item.title,
                            color = colors.textPrimary,
                            style = appleType.headline,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (item.subtitle.isNotBlank()) {
                            Text(
                                text = item.subtitle,
                                color = colors.textSecondary,
                                style = appleType.footnote,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (item.description.isNotBlank()) {
                            Text(
                                text = item.description,
                                color = colors.textSecondary,
                                style = appleType.caption1,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (cardClick == null) {
                    SelectionContainer { infoContent() }
                } else {
                    infoContent()
                }

                if (item.description.isNotBlank()) {
                    CopyTextButton(
                        textToCopy = item.description,
                        label = "Copiar obs.",
                        copiedLabel = "Copiado",
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                val reminders = item.userEvent?.reminderMinutes.orEmpty()
                if (reminders.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = PortalIcons.Bell,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Notificar: " + reminders.sorted().joinToString(", ") { formatCalendarReminderLabel(it) },
                            color = colors.textSecondary,
                            style = appleType.caption1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarEventEditorSheet(
    existing: UserCalendarEvent?,
    initialDate: CalendarDate,
    subjectSuggestions: List<String>,
    saving: Boolean,
    onSave: (CalendarEventInput) -> Unit,
    onRequestDelete: ((UserCalendarEvent) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val localInfo = remember { PlatformBridge.currentLocalDateTime() }
    val today = remember(localInfo) {
        CalendarDate(
            year = localInfo.year.coerceIn(1970, 2100),
            month = localInfo.month.coerceIn(1, 12),
            day = localInfo.day.coerceIn(1, 31)
        )
    }

    val startDate = remember(existing, initialDate) {
        existing?.date?.let { parseIsoDate(it) } ?: initialDate
    }

    var title by remember(existing) { mutableStateOf(existing?.title.orEmpty()) }
    var dateText by remember(existing, initialDate) { mutableStateOf(formatBrDate(startDate)) }
    var allDay by remember(existing) { mutableStateOf(existing?.allDay ?: false) }
    var timeText by remember(existing) { mutableStateOf(existing?.time?.takeIf { it.isNotBlank() } ?: "19:00") }
    var disciplineName by remember(existing) { mutableStateOf(existing?.disciplineName.orEmpty()) }
    var colorKey by remember(existing) { mutableStateOf(existing?.colorKey?.ifBlank { "accent" } ?: "accent") }
    var selectedReminders by remember(existing) {
        mutableStateOf(existing?.reminderMinutes ?: listOf(60, 1440))
    }
    var description by remember(existing) { mutableStateOf(existing?.description.orEmpty()) }

    val parsedDate = parseBrDate(dateText) ?: parseIsoDate(dateText)
    val parsedHm = if (!allDay) parseHourMinute(timeText) else (8 to 0)
    val canSave = !saving && title.trim().isNotEmpty() && parsedDate != null && (allDay || parsedHm != null)

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.accent,
        unfocusedBorderColor = colors.divider,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        focusedLabelColor = colors.accent,
        unfocusedLabelColor = colors.textSecondary,
        cursorColor = colors.accent
    )

    AppleSheet(
        title = if (existing == null) "Novo evento" else "Editar evento",
        subtitle = parsedDate?.let { fullDateLabelPt(it) } ?: "Defina data e lembretes",
        onDismiss = onDismiss,
        initialDetent = AppleSheetDetent.Large,
        desktopMaxWidth = 620.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 160) title = it },
                label = { Text("Título *") },
                placeholder = { Text("Ex: Prova P2, Entrega TCC, Revisão...") },
                singleLine = true,
                colors = textFieldColors,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Data
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Data",
                    color = colors.textPrimary,
                    style = appleType.subheadline,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        today to "Hoje",
                        addDays(today, 1) to "Amanhã",
                        addDays(today, 7) to "+7 dias"
                    ).forEach { (target, label) ->
                        val active = parsedDate == target
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (active) colors.accent else colors.surfaceSecondary)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable {
                                    PlatformBridge.performHaptic(PortalHapticType.SELECTION)
                                    dateText = formatBrDate(target)
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (active) Color.White else colors.textPrimary,
                                style = appleType.footnote,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { input ->
                            val clean = input.filter { it.isDigit() || it == '/' || it == '-' }.take(10)
                            dateText = clean
                        },
                        label = { Text("Data (DD/MM/AAAA)") },
                        placeholder = { Text("18/11/2026") },
                        singleLine = true,
                        isError = parsedDate == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = textFieldColors,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceSecondary)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable {
                                val base = parsedDate ?: startDate
                                dateText = formatBrDate(addDays(base, -1))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("−1d", color = colors.textPrimary, style = appleType.caption1, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceSecondary)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable {
                                val base = parsedDate ?: startDate
                                dateText = formatBrDate(addDays(base, 1))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+1d", color = colors.textPrimary, style = appleType.caption1, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Horário / Dia inteiro
            PortalCard(
                modifier = Modifier.fillMaxWidth(),
                cardStyle = CardStyle.Plain,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Dia inteiro", color = colors.textPrimary, style = appleType.body, fontWeight = FontWeight.Medium)
                        Text(
                            text = if (allDay) "Lembretes consideram 08:00 do dia" else "Defina a hora exata do evento",
                            color = colors.textSecondary,
                            style = appleType.caption1
                        )
                    }
                    Switch(
                        checked = allDay,
                        onCheckedChange = {
                            PlatformBridge.performHaptic(PortalHapticType.SELECTION)
                            allDay = it
                        }
                    )
                }

                if (!allDay) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("08:00", "14:00", "19:00", "21:00", "23:59").forEach { preset ->
                            val selected = timeText == preset
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) colors.accent else colors.surface)
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable {
                                        PlatformBridge.performHaptic(PortalHapticType.SELECTION)
                                        timeText = preset
                                    }
                                    .padding(horizontal = 11.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = preset,
                                    color = if (selected) Color.White else colors.textPrimary,
                                    style = tabularNumberStyle(appleType.footnote),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = timeText,
                        onValueChange = { input ->
                            val clean = input.filter { it.isDigit() || it == ':' }.take(5)
                            timeText = clean
                        },
                        label = { Text("Horário (HH:MM)") },
                        placeholder = { Text("19:00") },
                        singleLine = true,
                        isError = parsedHm == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = textFieldColors,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Disciplina opcional
            val filteredSubjectSuggestions = remember(subjectSuggestions, disciplineName) {
                filterSubjectSuggestions(subjectSuggestions, disciplineName)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = disciplineName,
                    onValueChange = { if (it.length <= 120) disciplineName = it },
                    label = { Text("Disciplina (opcional)") },
                    placeholder = { Text("Ex: Engenharia de Software") },
                    trailingIcon = if (disciplineName.isNotEmpty()) {
                        {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable {
                                        PlatformBridge.performHaptic(PortalHapticType.SELECTION)
                                        disciplineName = ""
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = PortalIcons.Close,
                                    contentDescription = "Limpar disciplina",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else null,
                    singleLine = true,
                    colors = textFieldColors,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (filteredSubjectSuggestions.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filteredSubjectSuggestions.forEach { subj ->
                            val selected = disciplineName.isNotBlank() &&
                                normalizeSearchText(disciplineName) == normalizeSearchText(subj)
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (selected) colors.accentSoft else colors.surfaceSecondary)
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable {
                                        PlatformBridge.performHaptic(PortalHapticType.SELECTION)
                                        disciplineName = if (disciplineName.equals(subj, ignoreCase = true)) "" else subj
                                    }
                                    .padding(horizontal = 11.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = subj,
                                    color = if (selected) colors.accent else colors.textSecondary,
                                    style = appleType.caption1,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Categoria / Cor
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Categoria",
                    color = colors.textPrimary,
                    style = appleType.subheadline,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CALENDAR_COLOR_OPTIONS.forEach { opt ->
                        val c = resolveCalendarColor(colors, opt.key)
                        val selected = colorKey == opt.key
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected) c.copy(alpha = 0.16f) else colors.surfaceSecondary)
                                .then(
                                    if (selected) Modifier.border(1.4.dp, c, CircleShape) else Modifier
                                )
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable {
                                    PlatformBridge.performHaptic(PortalHapticType.SELECTION)
                                    colorKey = opt.key
                                }
                                .padding(horizontal = 11.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(c)
                            )
                            Text(
                                text = opt.label,
                                color = if (selected) c else colors.textPrimary,
                                style = appleType.footnote,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Lembretes de notificação
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Notificar lembretes",
                        color = colors.textPrimary,
                        style = appleType.subheadline,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${selectedReminders.size}/5 selecionados",
                        color = colors.textSecondary,
                        style = tabularNumberStyle(appleType.caption1)
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PRESET_CALENDAR_REMINDER_MINUTES.forEach { mins ->
                        val selected = selectedReminders.contains(mins)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) colors.accent else colors.surfaceSecondary)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable {
                                    PlatformBridge.performHaptic(PortalHapticType.SELECTION)
                                    selectedReminders = if (selected) {
                                        selectedReminders - mins
                                    } else if (selectedReminders.size < 5) {
                                        (selectedReminders + mins).distinct().sorted()
                                    } else {
                                        selectedReminders
                                    }
                                }
                                .padding(horizontal = 11.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = formatCalendarReminderChipLabel(mins),
                                color = if (selected) Color.White else colors.textPrimary,
                                style = appleType.footnote,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 1000) description = it },
                label = { Text("Observações (opcional)") },
                placeholder = { Text("Sala, conteúdo da prova, links ou anotações...") },
                minLines = 2,
                maxLines = 4,
                colors = textFieldColors,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(4.dp))

            PortalPrimaryButton(
                text = if (existing == null) "Salvar evento" else "Atualizar evento",
                enabled = canSave,
                loading = saving,
                onClick = {
                    val date = parsedDate ?: return@PortalPrimaryButton
                    val hm = if (allDay) (8 to 0) else (parsedHm ?: return@PortalPrimaryButton)
                    val formattedTime = if (allDay) "" else "${hm.first.toString().padStart(2, '0')}:${hm.second.toString().padStart(2, '0')}"
                    val startMs = localDateTimeToEpochMillis(
                        date = date,
                        hour = hm.first,
                        minute = hm.second,
                        utcOffsetMinutes = localInfo.utcOffsetMinutes
                    )
                    onSave(
                        CalendarEventInput(
                            title = title.trim(),
                            description = description.trim(),
                            date = formatIsoDate(date),
                            time = formattedTime,
                            allDay = allDay,
                            startAtMillis = startMs,
                            disciplineName = disciplineName.trim(),
                            colorKey = colorKey,
                            reminderMinutes = selectedReminders.distinct().sorted().take(5)
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (existing != null && onRequestDelete != null) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PortalTextButton(
                        text = "Excluir evento",
                        color = colors.red,
                        onClick = { onRequestDelete(existing) }
                    )
                }
            }
        }
    }
}
