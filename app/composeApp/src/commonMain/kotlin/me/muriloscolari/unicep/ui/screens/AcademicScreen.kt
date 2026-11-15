package me.muriloscolari.unicep.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import me.muriloscolari.unicep.ui.designsystem.PortalSearchField
import me.muriloscolari.unicep.ui.designsystem.PortalSectionHeader
import me.muriloscolari.unicep.ui.designsystem.PortalSegmentedControl
import me.muriloscolari.unicep.ui.designsystem.PortalStatusDot
import me.muriloscolari.unicep.ui.designsystem.PortalTextField
import me.muriloscolari.unicep.ui.designsystem.ProgressRing
import me.muriloscolari.unicep.ui.designsystem.tabularNumberStyle
import me.muriloscolari.unicep.ui.util.toDisplayTitle

@Composable
fun AcademicScreen(
    curriculum: CurriculumResponse?,
    schedule: ScheduleResponse?,
    selectedSubTab: Int,
    onSelectSubTab: (Int) -> Unit,
    onOpenProxy: (String) -> Unit,
    loading: Boolean = false,
    listState: LazyListState = rememberLazyListState()
) {
    val style = LocalDesignStyle.current
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val appleInsets = LocalAppleContentInsets.current

    var search by remember { mutableStateOf("") }

    val transcript = curriculum?.transcript.orEmpty()
    val approvedCount = remember(transcript) { transcript.count { it.status == "A" } }
    val inProgressCount = remember(transcript) { transcript.count { it.status == "C" } }
    val totalCount = transcript.size

    if (style != DesignStyle.APPLE) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(colors.background),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Curso", color = colors.textPrimary, style = MaterialTheme.typography.headlineLarge)
            }

            if (totalCount > 0) {
                item {
                    val ratio = (approvedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
                    val pct = (ratio * 100f).toInt()
                    PortalCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Progresso do Curso", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            PortalBadge("$approvedCount/$totalCount ($pct%)", colors.green)
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceSecondary)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(ratio)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(colors.green)
                            )
                        }
                        if (inProgressCount > 0) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "$inProgressCount disciplina(s) em andamento neste semestre",
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            item {
                PortalSegmentedControl(
                    options = listOf("Histórico", "Grade", "Horário"),
                    selectedIndex = selectedSubTab,
                    onSelect = onSelectSubTab
                )
            }

            if (selectedSubTab < 2) {
                item {
                    PortalTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = "Filtrar disciplina..."
                    )
                }
            }

            when (selectedSubTab) {
                0 -> {
                    val list = transcript.filter {
                        search.isBlank() || it.name.contains(search, ignoreCase = true)
                    }
                    if (list.isEmpty()) {
                        item {
                            PortalCard(modifier = Modifier.fillMaxWidth()) {
                                Text("Nenhuma disciplina encontrada no histórico.", color = colors.textSecondary, fontSize = 14.sp)
                            }
                        }
                    } else {
                        items(list, key = { "${it.name}_${it.yearSem}" }) { item ->
                            val statusColor = when (item.status) {
                                "A" -> colors.green
                                "C" -> colors.accent
                                "R" -> colors.red
                                else -> colors.textSecondary
                            }
                            PortalCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = if (item.syllabusUrl.isNotBlank()) ({ onOpenProxy(item.syllabusUrl) }) else null
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(item.name, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        val meta = listOfNotNull(
                                            item.yearSem.takeIf { it != "/" && it.isNotBlank() },
                                            item.hours.takeIf { it.isNotBlank() }?.let { "${it}h" },
                                            item.average.takeIf { it.isNotBlank() }?.let { "Média $it" },
                                            item.freqPercent.takeIf { it.isNotBlank() }?.let { "Freq $it%" }
                                        ).joinToString(" • ")
                                        if (meta.isNotBlank()) {
                                            Spacer(Modifier.height(3.dp))
                                            Text(meta, color = colors.textSecondary, fontSize = 12.sp)
                                        }
                                    }
                                    if (item.status.isNotBlank()) {
                                        PortalBadge(item.statusLabel.ifBlank { item.status }, statusColor)
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    val matrix = curriculum?.matrix.orEmpty().filter {
                        search.isBlank() || it.name.contains(search, ignoreCase = true) || it.code.contains(search, ignoreCase = true)
                    }
                    if (matrix.isEmpty()) {
                        item {
                            PortalCard(modifier = Modifier.fillMaxWidth()) {
                                Text("Matriz curricular indisponível ou sem resultados.", color = colors.textSecondary, fontSize = 14.sp)
                            }
                        }
                    } else {
                        item {
                            PortalCard(contentPadding = PaddingValues(0.dp)) {
                                matrix.forEachIndexed { idx, row ->
                                    if (idx > 0) PortalDivider(Modifier.padding(start = 16.dp))
                                    PortalListRow(
                                        title = row.name,
                                        subtitle = listOfNotNull(
                                            row.period.takeIf { it.isNotBlank() },
                                            row.code,
                                            row.hours.takeIf { it.isNotBlank() }?.let { "${it}h" },
                                            row.prerequisite.takeIf { it.isNotBlank() && it != "-" }?.let { "Pré-req: $it" }
                                        ).joinToString(" • ")
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    val rows = schedule?.rows.orEmpty()
                    if (rows.isEmpty()) {
                        item {
                            PortalCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "O quadro de horários retornou vazio no portal neste momento (comum fora do horário letivo ou fins de semana).",
                                    color = colors.textSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        item {
                            PortalCard(contentPadding = PaddingValues(0.dp)) {
                                rows.forEachIndexed { idx, cols ->
                                    if (idx > 0) PortalDivider(Modifier.padding(start = 16.dp))
                                    PortalListRow(
                                        title = cols.firstOrNull().orEmpty(),
                                        subtitle = cols.drop(1).filter { it.isNotBlank() }.joinToString(" • ")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // Apple Liquid Glass AcademicScreen
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(colors.background)) {
        val isWide = maxWidth >= 640.dp
        val horizontalPad = if (isWide) 24.dp else 16.dp
        val topPad = appleInsets.calculateTopPadding()
        val bottomPad = appleInsets.calculateBottomPadding()

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = horizontalPad,
                    end = horizontalPad,
                    top = topPad,
                    bottom = bottomPad
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "course_title") {
                    Text(
                        text = "Curso",
                        color = colors.textPrimary,
                        style = appleType.largeTitle
                    )
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
                        onSelect = onSelectSubTab
                    )
                }

                if (selectedSubTab < 2) {
                    item(key = "course_search") {
                        PortalSearchField(
                            value = search,
                            onValueChange = { search = it },
                            placeholder = "Buscar disciplina"
                        )
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
                                search.isBlank() || it.name.contains(search, ignoreCase = true)
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
                            val matrix = curriculum?.matrix.orEmpty().filter {
                                search.isBlank() || it.name.contains(search, ignoreCase = true) || it.code.contains(search, ignoreCase = true)
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

                        else -> {
                            val rows = schedule?.rows.orEmpty()
                            if (rows.isEmpty()) {
                                item(key = "empty_schedule") {
                                    AppleEmptyState(
                                        icon = PortalIcons.Calendar,
                                        title = "Sem aulas exibidas",
                                        subtitle = "O quadro de horários retornou vazio no portal neste momento (comum fora do horário letivo ou fins de semana)."
                                    )
                                }
                            } else {
                                item(key = "schedule_card") {
                                    PortalCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
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
