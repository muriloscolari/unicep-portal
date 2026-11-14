package me.muriloscolari.unicep.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import me.muriloscolari.unicep.platform.DesignStyle
import me.muriloscolari.unicep.ui.designsystem.LocalDesignStyle
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.ui.designsystem.PortalBadge
import me.muriloscolari.unicep.ui.designsystem.PortalCard
import me.muriloscolari.unicep.ui.designsystem.PortalDivider
import me.muriloscolari.unicep.ui.designsystem.PortalIcons
import me.muriloscolari.unicep.ui.designsystem.PortalSectionHeader
import me.muriloscolari.unicep.ui.designsystem.PortalSegmentedControl
import me.muriloscolari.unicep.ui.designsystem.tabularNumberStyle
import kotlin.math.roundToInt

data class UnifiedSubject(
    val code: String,
    val name: String,
    val grade: PartialGradeItem?,
    val report: ReportCardItem?
)

@Composable
fun SemesterScreen(
    dashboard: DashboardResponse?,
    periods: List<PeriodOption>,
    selectedPeriodId: String,
    grades: List<PartialGradeItem>,
    report: ReportCardResponse,
    unreadNotifications: List<NotificationItem>,
    selectedSubjectCode: String?,
    onSelectPeriod: (String) -> Unit,
    onOpenSubject: (String) -> Unit,
    onCloseSubject: () -> Unit,
    onOpenNotifications: () -> Unit
) {
    val colors = LocalPortalColors.current
    val selectedIdx = periods.indexOfFirst { it.id == selectedPeriodId }.coerceAtLeast(0)

    val mergedSubjects = remember(grades, report.items) {
        val gradeMap = grades.associateBy { it.code }
        val reportMap = report.items.associateBy { it.code }
        val orderedCodes = (grades.map { it.code } + report.items.map { it.code }).distinct()
        orderedCodes.map { code ->
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

    val riskyCount = remember(report.items) {
        report.items.count { it.hours > 0 && it.remainingAbsences <= 3 }
    }

    val studentFirstName = remember(dashboard?.student?.name) {
        dashboard?.student?.name
            ?.split(" ")
            ?.firstOrNull()
            ?.lowercase()
            ?.replaceFirstChar { it.uppercase() }
            ?: "Aluno"
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
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
                    PortalCard(modifier = Modifier.weight(1f), contentPadding = PaddingValues(14.dp)) {
                        Text("Frequência", color = colors.textSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (riskyCount == 0) "Tranquila" else "$riskyCount no limite",
                            color = if (riskyCount == 0) colors.green else colors.orange,
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
                    UnifiedSubjectCard(
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
        if (activeSubject != null) {
            SubjectDetailModal(
                subject = activeSubject,
                onDismiss = onCloseSubject
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnifiedSubjectCard(
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
            val ratio = (report.absences.toFloat() / report.maxAbsences.toFloat()).coerceIn(0f, 1f)
            val barColor = when {
                report.remainingAbsences <= 0 -> colors.red
                ratio >= 0.75f -> colors.orange
                else -> colors.green
            }
            Spacer(Modifier.height(12.dp))
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
                    text = if (report.remainingAbsences >= 0) "Restam ${report.remainingAbsences}" else "Excedeu ${-report.remainingAbsences}",
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
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectDetailModal(
    subject: UnifiedSubject,
    onDismiss: () -> Unit
) {
    val colors = LocalPortalColors.current
    val style = LocalDesignStyle.current
    val grade = subject.grade
    val report = subject.report
    val slots = remember(grade) { buildEvalSlots(grade) }
    val sim = remember(slots) { computeNeededGrade(slots, targetAvg = 6.0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(if (style == DesignStyle.APPLE) 24.dp else 20.dp),
            color = colors.surface,
            border = BorderStroke(0.5.dp, colors.divider),
            tonalElevation = if (style == DesignStyle.MATERIAL) 6.dp else 0.dp
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
                        Text(subject.name, color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(PortalIcons.Close, contentDescription = "Fechar", tint = colors.textSecondary)
                    }
                }

                Spacer(Modifier.height(14.dp))
                PortalDivider()
                Spacer(Modifier.height(14.dp))

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
                }
            }
        }
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
    val labels = missing.joinToString(", ") { it.label }

    return when {
        needed <= 0.0 -> "Você já garantiu média >= 6,0 mesmo tirando 0 em $labels."
        needed > 10.0 -> "Precisaria de $formatted em $labels (acima de 10,0 com os pesos atuais)."
        else -> "Precisa tirar em média $formatted nas avaliações restantes ($labels) para fechar com 6,0."
    }
}

internal fun shortenPeriod(label: String): String {
    return label
        .replace("semestre de ", "")
        .replace("º ", "/")
        .trim()
}
