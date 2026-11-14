package me.muriloscolari.unicep.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.muriloscolari.unicep.data.api.PeriodOption
import me.muriloscolari.unicep.data.api.ReportCardItem
import me.muriloscolari.unicep.data.api.ReportCardResponse
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.ui.designsystem.PortalBadge
import me.muriloscolari.unicep.ui.designsystem.PortalCard
import me.muriloscolari.unicep.ui.designsystem.PortalSegmentedControl
import me.muriloscolari.unicep.ui.designsystem.tabularNumberStyle

@Composable
fun ReportScreen(
    periods: List<PeriodOption>,
    selectedPeriodId: String,
    report: ReportCardResponse,
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
            Text("Boletim e Faltas", color = colors.textPrimary, style = MaterialTheme.typography.headlineLarge)
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

        if (report.items.isEmpty()) {
            item {
                PortalCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Sem dados de boletim neste período.", color = colors.textSecondary, fontSize = 14.sp)
                }
            }
        } else {
            items(report.items, key = { it.code }) { item ->
                ReportSubjectCard(item)
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

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ReportSubjectCard(item: ReportCardItem) {
    val colors = LocalPortalColors.current
    val statusColor = when (item.status) {
        "A" -> colors.green
        "R" -> colors.red
        "C" -> colors.accent
        else -> colors.textSecondary
    }

    val absenceRatio = if (item.maxAbsences > 0) {
        (item.absences.toFloat() / item.maxAbsences.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val absenceBarColor = when {
        item.maxAbsences == 0 -> colors.accent
        item.remainingAbsences <= 0 -> colors.red
        absenceRatio >= 0.75f -> colors.orange
        else -> colors.green
    }

    PortalCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Text(item.name, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = listOfNotNull(
                        item.code,
                        item.classGroup.takeIf { it.isNotBlank() },
                        item.updatedAt.takeIf { it.isNotBlank() }?.let { "Atual. $it" }
                    ).joinToString(" • "),
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }
            PortalBadge(item.statusLabel.ifBlank { item.status }, statusColor)
        }

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MetricMini("Média Final", item.finalAverage.ifBlank { "—" })
            MetricMini("Faltas", "${item.absences}/${if (item.maxAbsences > 0) item.maxAbsences else "—"}")
            MetricMini("Frequência", item.freqPercent?.let { "${it.toInt()}%" } ?: "—")
            MetricMini("CH", "${item.hours}h")
        }

        if (item.maxAbsences > 0) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceSecondary)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(absenceRatio)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(absenceBarColor)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (item.remainingAbsences >= 0) {
                    "Pode faltar mais ${item.remainingAbsences} aula(s) antes de atingir 25% (${item.maxAbsences}h max.)"
                } else {
                    "Limite de 25% excedido em ${-item.remainingAbsences} falta(s)"
                },
                color = if (item.remainingAbsences <= 2) absenceBarColor else colors.textSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun MetricMini(label: String, value: String) {
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
