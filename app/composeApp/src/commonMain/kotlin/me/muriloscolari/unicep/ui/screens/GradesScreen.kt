package me.muriloscolari.unicep.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.muriloscolari.unicep.data.api.PartialGradeItem
import me.muriloscolari.unicep.data.api.PeriodOption
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.ui.designsystem.PortalBadge
import me.muriloscolari.unicep.ui.designsystem.PortalCard
import me.muriloscolari.unicep.ui.designsystem.PortalDivider
import me.muriloscolari.unicep.ui.designsystem.PortalSegmentedControl
import me.muriloscolari.unicep.ui.designsystem.tabularNumberStyle
import kotlin.math.roundToInt

@Composable
fun GradesScreen(
    periods: List<PeriodOption>,
    selectedPeriodId: String,
    items: List<PartialGradeItem>,
    onSelectPeriod: (String) -> Unit
) {
    val colors = LocalPortalColors.current
    val selectedIdx = periods.indexOfFirst { it.id == selectedPeriodId }.coerceAtLeast(0)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Notas Parciais", color = colors.textPrimary, style = MaterialTheme.typography.headlineLarge)
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

        if (items.isEmpty()) {
            item {
                PortalCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Nenhuma disciplina encontrada para este período.", color = colors.textSecondary, fontSize = 14.sp)
                }
            }
        } else {
            items(items, key = { it.code }) { item ->
                GradeSubjectCard(item)
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GradeSubjectCard(item: PartialGradeItem) {
    val colors = LocalPortalColors.current
    var showSimulator by remember(item.code) { mutableStateOf(false) }

    val avgText = item.average.display.ifBlank { "—" }
    val avgColor = when {
        item.average.display.isBlank() -> colors.textSecondary
        item.belowPassing -> colors.red
        else -> colors.green
    }

    val slots = remember(item) {
        buildList {
            for (i in 0 until 4) {
                val grade = item.exams.getOrNull(i)
                val weight = item.examWeights.getOrNull(i)
                if (!grade?.display.isNullOrBlank() || !weight?.display.isNullOrBlank()) {
                    add(EvalSlot("P${i + 1}", grade?.display.orEmpty(), grade?.value, weight?.display.orEmpty(), weight?.value))
                }
            }
            for (i in 0 until 4) {
                val grade = item.works.getOrNull(i)
                val weight = item.workWeights.getOrNull(i)
                if (!grade?.display.isNullOrBlank() || !weight?.display.isNullOrBlank()) {
                    add(EvalSlot("T${i + 1}", grade?.display.orEmpty(), grade?.value, weight?.display.orEmpty(), weight?.value))
                }
            }
        }
    }

    PortalCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Text(item.name, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(item.code, color = colors.textSecondary, fontSize = 12.sp)
            }
            PortalBadge("Média $avgText", avgColor)
        }

        Spacer(Modifier.height(12.dp))

        if (slots.isEmpty()) {
            Text("Aguardando lançamento de notas ou pesos", color = colors.textSecondary, fontSize = 13.sp)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                slots.forEach { slot ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceSecondary.copy(alpha = 0.65f))
                            .padding(horizontal = 11.dp, vertical = 7.dp),
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
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            style = tabularNumberStyle(MaterialTheme.typography.titleMedium)
                        )
                    }
                }
            }
        }

        val sim = remember(slots) { computeNeededGrade(slots, targetAvg = 6.0) }
        if (sim != null) {
            Spacer(Modifier.height(10.dp))
            PortalDivider()
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showSimulator = !showSimulator },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Simulador (meta 6,0)", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(if (showSimulator) "Ocultar" else "Calcular", color = colors.accent, fontSize = 12.sp)
            }
            AnimatedVisibility(visible = showSimulator) {
                Text(
                    text = sim,
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

private data class EvalSlot(
    val label: String,
    val gradeDisplay: String,
    val gradeValue: Double?,
    val weightDisplay: String,
    val weightValue: Double?
)

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
