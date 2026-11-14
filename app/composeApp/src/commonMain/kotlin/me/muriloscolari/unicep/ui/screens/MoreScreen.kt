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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import me.muriloscolari.unicep.data.api.FinanceResponse
import me.muriloscolari.unicep.data.api.NotificationItem
import me.muriloscolari.unicep.data.api.ServicesResponse
import me.muriloscolari.unicep.platform.DesignStylePreference
import me.muriloscolari.unicep.platform.PlatformBridge
import me.muriloscolari.unicep.ui.designsystem.LocalDesignStyle
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.ui.designsystem.PortalBadge
import me.muriloscolari.unicep.ui.designsystem.PortalCard
import me.muriloscolari.unicep.ui.designsystem.PortalDivider
import me.muriloscolari.unicep.ui.designsystem.PortalListRow
import me.muriloscolari.unicep.ui.designsystem.PortalPrimaryButton
import me.muriloscolari.unicep.ui.designsystem.PortalSectionHeader
import me.muriloscolari.unicep.ui.designsystem.PortalSegmentedControl

@Composable
fun MoreScreen(
    finance: FinanceResponse?,
    services: ServicesResponse?,
    notifications: List<NotificationItem>,
    stylePreference: DesignStylePreference,
    initialSubTab: Int = 0,
    onSelectStyle: (DesignStylePreference) -> Unit,
    onOpenLink: (path: String, proxy: Boolean) -> Unit,
    onSyncNotifications: () -> Unit,
    onMarkAllRead: () -> Unit,
    onTestNotification: () -> Unit,
    onLogout: () -> Unit
) {
    val colors = LocalPortalColors.current
    val activeStyle = LocalDesignStyle.current
    val capabilities = remember { PlatformBridge.capabilities() }
    var subTab by remember(initialSubTab) { mutableStateOf(initialSubTab) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Mais", color = colors.textPrimary, style = MaterialTheme.typography.headlineLarge)
        }

        item {
            PortalSegmentedControl(
                options = listOf("Alertas", "Financeiro", "Serviços", "Ajustes"),
                selectedIndex = subTab,
                onSelect = { subTab = it }
            )
        }

        when (subTab) {
            0 -> {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PortalPrimaryButton(
                            text = "Verificar agora",
                            onClick = onSyncNotifications,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = onMarkAllRead,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Marcar lidas", fontSize = 13.sp)
                        }
                    }
                }
                if (notifications.isEmpty()) {
                    item {
                        PortalCard(modifier = Modifier.fillMaxWidth()) {
                            Text("Nenhuma atualização de nota registrada ainda.", color = colors.textSecondary, fontSize = 14.sp)
                        }
                    }
                } else {
                    items(notifications, key = { it.id }) { n ->
                        val isGrade = n.kind == "grade"
                        PortalCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(n.disciplineName, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
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
                                fontSize = 14.sp
                            )
                            Text(n.createdAt, color = colors.textSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }

            1 -> {
                val installments = finance?.installments.orEmpty()
                if (installments.isNotEmpty()) {
                    item {
                        PortalSectionHeader("Mensalidades do Semestre")
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
                        }
                    }
                }
                val links = finance?.links.orEmpty()
                if (links.isNotEmpty()) {
                    item {
                        PortalSectionHeader("Ações Financeiras")
                        PortalCard(contentPadding = PaddingValues(0.dp)) {
                            links.forEachIndexed { idx, link ->
                                if (idx > 0) PortalDivider(Modifier.padding(start = 16.dp))
                                PortalListRow(title = link.title, onClick = { onOpenLink(link.path, link.proxy) })
                            }
                        }
                    }
                }
            }

            2 -> {
                val shortcuts = services?.shortcuts.orEmpty()
                if (shortcuts.isNotEmpty()) {
                    item {
                        PortalSectionHeader("Atalhos do Portal")
                        PortalCard(contentPadding = PaddingValues(0.dp)) {
                            shortcuts.forEachIndexed { idx, link ->
                                if (idx > 0) PortalDivider(Modifier.padding(start = 16.dp))
                                PortalListRow(
                                    title = link.title,
                                    subtitle = link.category,
                                    onClick = { onOpenLink(link.path, link.proxy) }
                                )
                            }
                        }
                    }
                }
                val docs = services?.institutionalDocs.orEmpty()
                if (docs.isNotEmpty()) {
                    item {
                        PortalSectionHeader("Documentos Institucionais")
                        PortalCard(contentPadding = PaddingValues(0.dp)) {
                            docs.forEachIndexed { idx, link ->
                                if (idx > 0) PortalDivider(Modifier.padding(start = 16.dp))
                                PortalListRow(title = link.title, onClick = { onOpenLink(link.path, link.proxy) })
                            }
                        }
                    }
                }
            }

            3 -> {
                item {
                    PortalSectionHeader("Interface e Performance")
                    PortalCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Estilo ativo: ${activeStyle.name}",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        if (capabilities.fallbackReason != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Fallback automático ativo: ${capabilities.fallbackReason}",
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        val prefs = DesignStylePreference.entries
                        PortalSegmentedControl(
                            options = listOf("Auto", "Apple", "Material"),
                            selectedIndex = prefs.indexOf(stylePreference).coerceAtLeast(0),
                            onSelect = { onSelectStyle(prefs[it]) }
                        )
                    }
                }

                item {
                    PortalSectionHeader("Notificações e Sessão")
                    PortalCard(contentPadding = PaddingValues(0.dp)) {
                        PortalListRow(
                            title = "Criar notificação de teste",
                            subtitle = "Valida o alerta no painel e no WorkManager Android",
                            onClick = onTestNotification
                        )
                        PortalDivider(Modifier.padding(start = 16.dp))
                        PortalListRow(
                            title = "Sincronização em segundo plano",
                            subtitle = if (capabilities.isWeb) "Rodando no servidor Docker a cada 15 min" else "Worker Android ativo a cada 15 min"
                        )
                    }
                }

                item {
                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.red)
                    ) {
                        Text("Sair e Limpar Sessão Salva", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}
