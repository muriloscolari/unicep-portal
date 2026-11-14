package me.muriloscolari.unicep.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.muriloscolari.unicep.data.api.CurriculumResponse
import me.muriloscolari.unicep.data.api.ScheduleResponse
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.ui.designsystem.PortalBadge
import me.muriloscolari.unicep.ui.designsystem.PortalCard
import me.muriloscolari.unicep.ui.designsystem.PortalDivider
import me.muriloscolari.unicep.ui.designsystem.PortalListRow
import me.muriloscolari.unicep.ui.designsystem.PortalSegmentedControl
import me.muriloscolari.unicep.ui.designsystem.PortalTextField

@Composable
fun AcademicScreen(
    curriculum: CurriculumResponse?,
    schedule: ScheduleResponse?,
    onOpenProxy: (String) -> Unit
) {
    val colors = LocalPortalColors.current
    var tab by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Acadêmico", color = colors.textPrimary, style = MaterialTheme.typography.headlineLarge)
        }

        item {
            PortalSegmentedControl(
                options = listOf("Histórico", "Grade", "Horário"),
                selectedIndex = tab,
                onSelect = { tab = it }
            )
        }

        if (tab < 2) {
            item {
                PortalTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = "Filtrar disciplina..."
                )
            }
        }

        when (tab) {
            0 -> {
                val list = curriculum?.transcript.orEmpty().filter {
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

            2 -> {
                val rows = schedule?.rows.orEmpty()
                if (rows.isEmpty()) {
                    item {
                        PortalCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "O quadro de horários retornou vazio no portal neste momento (comum fora do período letivo ou fins de semana).",
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

        item { Spacer(Modifier.height(16.dp)) }
    }
}
