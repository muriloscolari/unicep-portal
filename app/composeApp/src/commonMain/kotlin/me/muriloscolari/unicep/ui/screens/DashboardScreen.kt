package me.muriloscolari.unicep.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.muriloscolari.unicep.data.api.DashboardResponse
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.ui.designsystem.PortalBadge
import me.muriloscolari.unicep.ui.designsystem.PortalCard
import me.muriloscolari.unicep.ui.designsystem.PortalDivider
import me.muriloscolari.unicep.ui.designsystem.PortalListRow
import me.muriloscolari.unicep.ui.designsystem.PortalPrimaryButton
import me.muriloscolari.unicep.ui.designsystem.PortalSectionHeader
import me.muriloscolari.unicep.ui.designsystem.tabularNumberStyle
import androidx.compose.material3.MaterialTheme

@Composable
fun DashboardScreen(
    data: DashboardResponse,
    onGoGrades: () -> Unit,
    onGoReport: () -> Unit,
    onOpenAva: () -> Unit,
    onOpenLink: (path: String, proxy: Boolean) -> Unit,
    onOpenNotifications: () -> Unit
) {
    val colors = LocalPortalColors.current
    val currentSubjects = data.currentReport.items
    val riskySubjects = currentSubjects.filter { it.hours > 0 && it.remainingAbsences <= 3 }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Olá, ${data.student.name.split(" ").firstOrNull()?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Aluno"}",
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.headlineLarge
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "RA ${data.student.ra} • ${data.student.campus}",
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            }
        }

        if (data.unreadNotifications.isNotEmpty()) {
            item {
                PortalCard(onClick = onOpenNotifications) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Novidades em Notas", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            val latest = data.unreadNotifications.first()
                            Text(
                                "${latest.disciplineName}: ${latest.field} → ${latest.newValue}",
                                color = colors.textSecondary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        PortalBadge("${data.unreadNotifications.size} nova(s)", colors.accent)
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PortalCard(modifier = Modifier.weight(1f), onClick = onGoGrades) {
                    Text("Disciplinas", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${data.currentGrades.size}",
                        color = colors.textPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        style = tabularNumberStyle(MaterialTheme.typography.headlineLarge)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text("Ver notas parciais", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                PortalCard(modifier = Modifier.weight(1f), onClick = onGoReport) {
                    Text("Alerta de Faltas", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${riskySubjects.size}",
                        color = if (riskySubjects.isNotEmpty()) colors.orange else colors.green,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        style = tabularNumberStyle(MaterialTheme.typography.headlineLarge)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (riskySubjects.isEmpty()) "Frequência tranquila" else "Matéria(s) no limite",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        item {
            PortalPrimaryButton(
                text = "Abrir UnicepVirtual (D2L SSO)",
                onClick = onOpenAva,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (data.currentGrades.isNotEmpty()) {
            item {
                PortalSectionHeader(
                    title = "Resumo de Notas",
                    trailing = {
                        Text(
                            text = data.gradePeriods.firstOrNull()?.label ?: "",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                )
                PortalCard(contentPadding = PaddingValues(0.dp)) {
                    data.currentGrades.take(5).forEachIndexed { idx, g ->
                        if (idx > 0) PortalDivider(Modifier.padding(start = 16.dp))
                        val avgText = g.average.display.ifBlank { "—" }
                        val avgColor = when {
                            g.average.display.isBlank() -> colors.textSecondary
                            g.belowPassing -> colors.red
                            else -> colors.green
                        }
                        PortalListRow(
                            title = g.name,
                            subtitle = buildList {
                                g.exams.forEachIndexed { i, c -> if (c.display.isNotBlank()) add("P${i + 1}: ${c.display}") }
                                g.works.forEachIndexed { i, c -> if (c.display.isNotBlank()) add("T${i + 1}: ${c.display}") }
                            }.joinToString(" • ").ifBlank { "Sem notas lançadas ainda" },
                            trailing = {
                                PortalBadge("Média $avgText", avgColor)
                            },
                            onClick = onGoGrades
                        )
                    }
                }
            }
        }

        if (data.summary.enrollmentLinks.isNotEmpty()) {
            item {
                PortalSectionHeader("Matrícula")
                PortalCard(contentPadding = PaddingValues(0.dp)) {
                    data.summary.enrollmentLinks.forEachIndexed { idx, link ->
                        if (idx > 0) PortalDivider(Modifier.padding(start = 16.dp))
                        PortalListRow(
                            title = link.title,
                            onClick = { onOpenLink(link.path, link.proxy) }
                        )
                    }
                }
            }
        }

        if (data.summary.notices.isNotEmpty()) {
            item {
                PortalSectionHeader("Avisos da Coordenação")
                data.summary.notices.forEach { notice ->
                    PortalCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text(notice, color = colors.textPrimary, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
            }
        }

        if (data.summary.nacOpen) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.orange.copy(alpha = 0.14f))
                        .padding(14.dp)
                ) {
                    Text(
                        "Avaliação Institucional (NAC) aberta no portal. Você pode acessá-la na aba Mais → Serviços quando desejar.",
                        color = colors.orange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}
