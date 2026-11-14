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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.muriloscolari.unicep.data.api.DashboardResponse
import me.muriloscolari.unicep.data.api.FinanceResponse
import me.muriloscolari.unicep.data.api.NotificationItem
import me.muriloscolari.unicep.data.api.ServicesResponse
import me.muriloscolari.unicep.platform.DesignStyle
import me.muriloscolari.unicep.platform.DesignStylePreference
import me.muriloscolari.unicep.platform.PlatformBridge
import me.muriloscolari.unicep.ui.designsystem.LocalDesignStyle
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.ui.designsystem.PortalBadge
import me.muriloscolari.unicep.ui.designsystem.PortalCard
import me.muriloscolari.unicep.ui.designsystem.PortalDivider
import me.muriloscolari.unicep.ui.designsystem.PortalListRow
import me.muriloscolari.unicep.ui.designsystem.PortalPrimaryButton
import me.muriloscolari.unicep.ui.designsystem.PortalSecondaryButton
import me.muriloscolari.unicep.ui.designsystem.PortalSectionHeader
import me.muriloscolari.unicep.ui.designsystem.PortalSegmentedControl

@Composable
fun CentralScreen(
    dashboard: DashboardResponse?,
    finance: FinanceResponse?,
    services: ServicesResponse?,
    notifications: List<NotificationItem>,
    stylePreference: DesignStylePreference,
    onSelectStyle: (DesignStylePreference) -> Unit,
    onOpenAva: () -> Unit,
    onOpenLink: (path: String, proxy: Boolean) -> Unit,
    onSyncNotifications: () -> Unit,
    onMarkAllRead: () -> Unit,
    onTestNotification: () -> Unit,
    onLogout: () -> Unit
) {
    val colors = LocalPortalColors.current
    val activeStyle = LocalDesignStyle.current
    val capabilities = remember { PlatformBridge.capabilities() }

    val styleOptions = listOf(
        DesignStylePreference.APPLE,
        DesignStylePreference.MATERIAL,
        DesignStylePreference.AUTO
    )
    val styleLabels = listOf("Apple", "Material 3", "Auto")

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Central", color = colors.textPrimary, style = MaterialTheme.typography.headlineLarge)
        }

        item {
            PortalPrimaryButton(
                text = "Abrir UnicepVirtual (D2L SSO)",
                onClick = onOpenAva,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            PortalSectionHeader(
                title = "Aparência",
                trailing = {
                    PortalBadge(
                        text = if (activeStyle == DesignStyle.APPLE) "Estilo Apple ativo" else "Material 3 ativo",
                        color = colors.accent
                    )
                }
            )
            PortalCard(modifier = Modifier.fillMaxWidth()) {
                PortalSegmentedControl(
                    options = styleLabels,
                    selectedIndex = styleOptions.indexOf(stylePreference).coerceAtLeast(0),
                    onSelect = { onSelectStyle(styleOptions[it]) }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (capabilities.fallbackReason != null && stylePreference == DesignStylePreference.AUTO) {
                        "Auto selecionou Material 3 (${capabilities.fallbackReason}). Você pode forçar Apple acima se preferir."
                    } else if (activeStyle == DesignStyle.APPLE) {
                        "Visual estilo iOS/macOS com campos preenchidos, cartões agrupados e barra flutuante."
                    } else {
                        "Material Design 3 com contornos, cores tonais e barra de navegação Material."
                    },
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }
        }

        item {
            PortalSectionHeader("Notificações de Notas")
            PortalCard(contentPadding = PaddingValues(0.dp)) {
                PortalListRow(
                    title = "Testar notificação no aparelho",
                    subtitle = if (capabilities.isWeb) {
                        "Dispara alerta via navegador e salva no histórico"
                    } else {
                        "Envia notificação imediata para a barra de status + histórico"
                    },
                    onClick = onTestNotification
                )
                PortalDivider(Modifier.padding(start = 16.dp))
                PortalListRow(
                    title = "Verificar novas notas agora",
                    subtitle = if (capabilities.isWeb) "Poller Docker ativo a cada 15 min" else "WorkManager Android ativo a cada 15 min",
                    onClick = onSyncNotifications
                )
                if (notifications.any { it.readAt == null }) {
                    PortalDivider(Modifier.padding(start = 16.dp))
                    PortalListRow(
                        title = "Marcar todas como lidas",
                        onClick = onMarkAllRead
                    )
                }
            }
        }

        if (notifications.isNotEmpty()) {
            item {
                PortalSectionHeader("Histórico de Alertas")
            }
            items(notifications.take(8), key = { it.id }) { n ->
                val isGrade = n.kind == "grade"
                PortalCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = n.disciplineName,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        PortalBadge(
                            text = if (n.readAt == null) "Nova" else "Lida",
                            color = if (n.readAt == null) colors.accent else colors.textSecondary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${n.field}: ${if (n.oldValue.isBlank()) "—" else n.oldValue} → ${n.newValue}",
                        color = if (isGrade) colors.green else colors.orange,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    )
                    Text(n.createdAt, color = colors.textSecondary, fontSize = 11.sp)
                }
            }
        }

        val installments = finance?.installments.orEmpty()
        val financeLinks = finance?.links.orEmpty()
        if (installments.isNotEmpty() || financeLinks.isNotEmpty()) {
            item {
                PortalSectionHeader("Financeiro")
                PortalCard(contentPadding = PaddingValues(0.dp)) {
                    installments.forEachIndexed { idx, item ->
                        if (idx > 0) PortalDivider(Modifier.padding(start = 16.dp))
                        val paid = item.status.contains("pago", ignoreCase = true) || item.status.contains("quit", ignoreCase = true)
                        PortalListRow(
                            title = "${item.installment} • Venc. ${item.dueDate}",
                            subtitle = "Bruto: ${item.grossAmount} • Desc.: ${item.discountAmount}",
                            trailing = {
                                PortalBadge(item.status, if (paid) colors.green else colors.orange)
                            }
                        )
                    }
                    financeLinks.forEachIndexed { idx, link ->
                        if (installments.isNotEmpty() || idx > 0) PortalDivider(Modifier.padding(start = 16.dp))
                        PortalListRow(title = link.title, onClick = { onOpenLink(link.path, link.proxy) })
                    }
                }
            }
        }

        val enrollmentLinks = dashboard?.summary?.enrollmentLinks.orEmpty()
        val shortcuts = services?.shortcuts.orEmpty()
        val docs = services?.institutionalDocs.orEmpty()
        if (enrollmentLinks.isNotEmpty() || shortcuts.isNotEmpty() || docs.isNotEmpty()) {
            item {
                PortalSectionHeader("Documentos e Serviços")
                PortalCard(contentPadding = PaddingValues(0.dp)) {
                    val allLinks = enrollmentLinks + shortcuts + docs
                    allLinks.forEachIndexed { idx, link ->
                        if (idx > 0) PortalDivider(Modifier.padding(start = 16.dp))
                        PortalListRow(
                            title = link.title,
                            subtitle = link.category.takeIf { it.isNotBlank() },
                            onClick = { onOpenLink(link.path, link.proxy) }
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            PortalSecondaryButton(
                text = "Sair e Limpar Sessão Salva",
                onClick = onLogout,
                tint = colors.red,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
