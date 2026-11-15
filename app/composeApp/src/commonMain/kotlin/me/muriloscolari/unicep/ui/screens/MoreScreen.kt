package me.muriloscolari.unicep.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.muriloscolari.unicep.data.api.DashboardResponse
import me.muriloscolari.unicep.data.api.FinanceResponse
import me.muriloscolari.unicep.data.api.NotificationItem
import me.muriloscolari.unicep.data.api.ServicesResponse
import me.muriloscolari.unicep.platform.AppUpdateInfo
import me.muriloscolari.unicep.platform.DesignStyle
import me.muriloscolari.unicep.platform.DesignStylePreference
import me.muriloscolari.unicep.platform.PlatformBridge
import me.muriloscolari.unicep.ui.designsystem.AppleActivityIndicator
import me.muriloscolari.unicep.ui.designsystem.AppleAlert
import me.muriloscolari.unicep.ui.designsystem.AppleProgressBar
import me.muriloscolari.unicep.ui.designsystem.AppleSheet
import me.muriloscolari.unicep.ui.designsystem.CardStyle
import me.muriloscolari.unicep.ui.designsystem.LocalAppleContentInsets
import me.muriloscolari.unicep.ui.designsystem.LocalAppleTypography
import me.muriloscolari.unicep.ui.designsystem.LocalDesignStyle
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.ui.designsystem.PortalBadge
import me.muriloscolari.unicep.ui.designsystem.PortalCard
import me.muriloscolari.unicep.ui.designsystem.PortalDivider
import me.muriloscolari.unicep.ui.designsystem.PortalIcons
import me.muriloscolari.unicep.ui.designsystem.PortalListRow
import me.muriloscolari.unicep.ui.designsystem.PortalPrimaryButton
import me.muriloscolari.unicep.ui.designsystem.PortalSecondaryButton
import me.muriloscolari.unicep.ui.designsystem.PortalSectionFooter
import me.muriloscolari.unicep.ui.designsystem.PortalSectionHeader
import me.muriloscolari.unicep.ui.designsystem.PortalSegmentedControl
import me.muriloscolari.unicep.ui.designsystem.PortalStatusDot
import me.muriloscolari.unicep.ui.designsystem.PortalTextButton
import me.muriloscolari.unicep.ui.designsystem.appleScrollBoundaryHaptics
import me.muriloscolari.unicep.ui.designsystem.tabularNumberStyle
import me.muriloscolari.unicep.ui.util.formatRelativeTime
import me.muriloscolari.unicep.ui.util.initialsFromName
import me.muriloscolari.unicep.ui.util.toDisplayTitle

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
    availableUpdate: AppUpdateInfo? = null,
    updateChecking: Boolean = false,
    updateDownloading: Boolean = false,
    updateProgress: Float = 0f,
    updateStatusMessage: String? = null,
    onCheckUpdate: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    onLogout: () -> Unit,
    loading: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    showAllNotifications: Boolean = false,
    onOpenAllNotifications: () -> Unit = {},
    onCloseAllNotifications: () -> Unit = {},
    renderSheetsInPlace: Boolean = true,
    onRequestLogout: (() -> Unit)? = null
) {
    val colors = LocalPortalColors.current
    val activeStyle = LocalDesignStyle.current
    val appleType = LocalAppleTypography.current
    val appleInsets = LocalAppleContentInsets.current
    val capabilities = remember { PlatformBridge.capabilities() }

    val styleOptions = listOf(
        DesignStylePreference.APPLE,
        DesignStylePreference.MATERIAL,
        DesignStylePreference.AUTO
    )
    val styleLabels = listOf("Apple", "Material 3", "Auto")

    if (activeStyle != DesignStyle.APPLE) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .appleScrollBoundaryHaptics(listState),
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
                            text = "Material 3 ativo",
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

            if (!capabilities.isWeb) {
                item {
                    PortalSectionHeader("Atualizações do app")
                    PortalCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("UNICEP Portal Android", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(
                                    "Versão instalada: v${PlatformBridge.currentAppVersion()}",
                                    color = colors.textSecondary,
                                    fontSize = 12.5.sp
                                )
                            }
                            if (availableUpdate != null) {
                                PortalBadge("v${availableUpdate.latestVersion} disponível", colors.green)
                            }
                        }
                        if (!updateStatusMessage.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(updateStatusMessage, color = colors.textSecondary, fontSize = 12.5.sp)
                        }
                        if (updateDownloading) {
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { updateProgress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = colors.accent,
                                trackColor = colors.accentSoft
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Baixando atualização (${(updateProgress * 100).toInt()}%)...",
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        if (availableUpdate != null) {
                            PortalPrimaryButton(
                                text = if (updateDownloading) "Baixando APK..." else "Baixar e instalar v${availableUpdate.latestVersion}",
                                onClick = onInstallUpdate,
                                enabled = !updateDownloading
                            )
                        } else {
                            PortalSecondaryButton(
                                text = if (updateChecking) "Verificando no Forgejo..." else "Verificar atualizações",
                                onClick = onCheckUpdate
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
        return
    }

    // Apple Liquid Glass CentralScreen (estilo app Ajustes)
    var confirmLogout by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(colors.background)) {
        val isWide = maxWidth >= 640.dp
        val horizontalPad = if (isWide) 24.dp else 16.dp
        val topPad = appleInsets.calculateTopPadding()
        val bottomPad = appleInsets.calculateBottomPadding()

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .appleScrollBoundaryHaptics(listState),
                contentPadding = PaddingValues(
                    start = horizontalPad,
                    end = horizontalPad,
                    top = topPad,
                    bottom = bottomPad
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item(key = "central_title") {
                    Text(
                        text = "Central",
                        color = colors.textPrimary,
                        style = appleType.largeTitle
                    )
                }

                // Cartão de perfil estilo Apple ID
                if (dashboard != null) {
                    item(key = "profile_card") {
                        val student = dashboard.student
                        val displayName = student.name.toDisplayTitle().ifBlank { "Aluno UNICEP" }
                        val initials = remember(student.name) { initialsFromName(student.name) }
                        PortalCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(listOf(colors.accent, colors.indigo))
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initials,
                                        color = Color.White,
                                        style = appleType.title3,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = displayName,
                                        color = colors.textPrimary,
                                        style = appleType.headline,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = listOfNotNull(
                                            "RA ${student.ra}",
                                            student.campus.takeIf { it.isNotBlank() },
                                            student.semester.takeIf { it.isNotBlank() }
                                        ).joinToString(" · "),
                                        color = colors.textSecondary,
                                        style = appleType.footnote,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Acesso rápido (UnicepVirtual)
                item(key = "quick_access") {
                    Column {
                        PortalSectionHeader("Acesso rápido")
                        PortalCard(contentPadding = PaddingValues(0.dp)) {
                            PortalListRow(
                                title = "UnicepVirtual (D2L SSO)",
                                subtitle = "Ambiente virtual de aprendizagem",
                                leadingIcon = PortalIcons.Globe,
                                leadingTint = colors.indigo,
                                onClick = onOpenAva,
                                showChevron = false,
                                trailing = {
                                    Icon(
                                        imageVector = PortalIcons.ArrowUpRight,
                                        contentDescription = "Abrir UnicepVirtual",
                                        tint = colors.accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                // Notificações
                item(key = "notifications_actions") {
                    Column {
                        PortalSectionHeader("Notificações")
                        PortalCard(contentPadding = PaddingValues(0.dp)) {
                            PortalListRow(
                                title = "Verificar novas notas",
                                leadingIcon = PortalIcons.Refresh,
                                leadingTint = colors.accent,
                                onClick = onSyncNotifications,
                                showChevron = !loading,
                                trailing = if (loading) {
                                    { AppleActivityIndicator(size = 18.dp, color = colors.textSecondary) }
                                } else null
                            )
                            PortalDivider(Modifier.padding(start = 58.dp))
                            PortalListRow(
                                title = "Enviar notificação de teste",
                                leadingIcon = PortalIcons.Sparkles,
                                leadingTint = colors.orange,
                                onClick = onTestNotification
                            )
                            if (notifications.any { it.readAt == null }) {
                                PortalDivider(Modifier.padding(start = 58.dp))
                                PortalListRow(
                                    title = "Marcar todas como lidas",
                                    leadingIcon = PortalIcons.CheckCircle,
                                    leadingTint = colors.green,
                                    onClick = onMarkAllRead
                                )
                            }
                        }
                        PortalSectionFooter(
                            text = if (capabilities.isWeb) {
                                "Verificamos novas notas automaticamente a cada 15 minutos no servidor."
                            } else {
                                "Verificamos novas notas automaticamente a cada 15 minutos em segundo plano."
                            }
                        )
                    }
                }

                // Histórico de alertas
                if (notifications.isNotEmpty()) {
                    item(key = "alerts_history") {
                        Column {
                            PortalSectionHeader(
                                title = "Histórico de alertas",
                                trailing = if (notifications.size > 5) {
                                    {
                                        PortalTextButton(
                                            text = "Ver todos (${notifications.size})",
                                            onClick = onOpenAllNotifications
                                        )
                                    }
                                } else null
                            )
                            PortalCard(contentPadding = PaddingValues(0.dp)) {
                                notifications.take(5).forEachIndexed { idx, n ->
                                    if (idx > 0) PortalDivider(Modifier.padding(start = 16.dp))
                                    NotificationAlertRow(n = n)
                                }
                            }
                        }
                    }
                }

                // Financeiro
                val installments = finance?.installments.orEmpty()
                val financeLinks = finance?.links.orEmpty()
                if (installments.isNotEmpty() || financeLinks.isNotEmpty()) {
                    item(key = "finance_section") {
                        Column {
                            PortalSectionHeader("Financeiro")
                            PortalCard(contentPadding = PaddingValues(0.dp)) {
                                installments.forEachIndexed { idx, item ->
                                    if (idx > 0) PortalDivider(Modifier.padding(start = 16.dp))
                                    val paid = item.status.contains("pago", ignoreCase = true) || item.status.contains("quit", ignoreCase = true)
                                    val overdue = item.status.contains("venc", ignoreCase = true) || item.status.contains("atras", ignoreCase = true)
                                    val statusColor = when {
                                        paid -> colors.green
                                        overdue -> colors.red
                                        else -> colors.orange
                                    }
                                    PortalListRow(
                                        title = "${item.installment} · vence ${item.dueDate}",
                                        subtitle = "Bruto ${item.grossAmount} · Desc. ${item.discountAmount}",
                                        trailing = {
                                            PortalStatusDot(
                                                text = item.status,
                                                color = statusColor,
                                                tintText = true
                                            )
                                        }
                                    )
                                }
                                financeLinks.forEachIndexed { idx, link ->
                                    if (installments.isNotEmpty() || idx > 0) PortalDivider(Modifier.padding(start = 58.dp))
                                    PortalListRow(
                                        title = link.title,
                                        leadingIcon = PortalIcons.CreditCard,
                                        leadingTint = colors.green,
                                        onClick = { onOpenLink(link.path, link.proxy) },
                                        showChevron = false,
                                        trailing = {
                                            Icon(
                                                imageVector = PortalIcons.ArrowUpRight,
                                                contentDescription = null,
                                                tint = colors.accent,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Documentos e Serviços
                val enrollmentLinks = dashboard?.summary?.enrollmentLinks.orEmpty()
                val shortcuts = services?.shortcuts.orEmpty()
                val docs = services?.institutionalDocs.orEmpty()
                val allLinks = enrollmentLinks + shortcuts + docs
                if (allLinks.isNotEmpty()) {
                    item(key = "docs_services") {
                        Column {
                            PortalSectionHeader("Documentos e serviços")
                            PortalCard(contentPadding = PaddingValues(0.dp)) {
                                allLinks.forEachIndexed { idx, link ->
                                    if (idx > 0) PortalDivider(Modifier.padding(start = 58.dp))
                                    PortalListRow(
                                        title = link.title,
                                        subtitle = link.category.takeIf { it.isNotBlank() },
                                        leadingIcon = PortalIcons.Document,
                                        leadingTint = colors.gray,
                                        onClick = { onOpenLink(link.path, link.proxy) },
                                        showChevron = false,
                                        trailing = {
                                            Icon(
                                                imageVector = PortalIcons.ArrowUpRight,
                                                contentDescription = null,
                                                tint = colors.accent,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Aparência
                item(key = "appearance_section") {
                    Column {
                        PortalSectionHeader("Aparência")
                        PortalCard(modifier = Modifier.fillMaxWidth()) {
                            PortalSegmentedControl(
                                options = styleLabels,
                                selectedIndex = styleOptions.indexOf(stylePreference).coerceAtLeast(0),
                                onSelect = { onSelectStyle(styleOptions[it]) }
                            )
                        }
                        PortalSectionFooter(
                            text = if (capabilities.fallbackReason != null && stylePreference == DesignStylePreference.AUTO) {
                                "Auto selecionaria Material 3 (${capabilities.fallbackReason}). Modo Apple forçado usa fallback sólido sem desfoque."
                            } else {
                                "Liquid Glass com tipografia Inter, células agrupadas sem bordas e navegação flutuante."
                            }
                        )
                    }
                }

                // Atualizações do app (Android)
                if (!capabilities.isWeb) {
                    item(key = "app_updates") {
                        Column {
                            PortalSectionHeader("Aplicativo")
                            PortalCard(contentPadding = PaddingValues(0.dp)) {
                                PortalListRow(
                                    title = "Versão instalada",
                                    leadingIcon = PortalIcons.Info,
                                    leadingTint = colors.teal,
                                    trailingValue = "v${PlatformBridge.currentAppVersion()}",
                                    showChevron = false
                                )
                                PortalDivider(Modifier.padding(start = 58.dp))
                                if (availableUpdate != null) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        PortalListRow(
                                            title = if (updateDownloading) "Baixando v${availableUpdate.latestVersion}…" else "Atualizar para v${availableUpdate.latestVersion}",
                                            subtitle = updateStatusMessage,
                                            leadingIcon = PortalIcons.Download,
                                            leadingTint = colors.green,
                                            onClick = if (!updateDownloading) onInstallUpdate else null,
                                            trailing = {
                                                if (updateDownloading) {
                                                    Text(
                                                        text = "${(updateProgress * 100).toInt()}%",
                                                        color = colors.accent,
                                                        style = tabularNumberStyle(appleType.subheadline),
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                } else {
                                                    PortalBadge("Nova", colors.green)
                                                }
                                            }
                                        )
                                        if (updateDownloading) {
                                            Box(modifier = Modifier.padding(start = 58.dp, end = 16.dp, bottom = 12.dp)) {
                                                AppleProgressBar(progress = updateProgress, color = colors.accent)
                                            }
                                        }
                                    }
                                } else {
                                    PortalListRow(
                                        title = if (updateChecking) "Verificando no Forgejo…" else "Verificar atualizações",
                                        subtitle = updateStatusMessage,
                                        leadingIcon = PortalIcons.Refresh,
                                        leadingTint = colors.accent,
                                        onClick = if (!updateChecking) onCheckUpdate else null,
                                        trailing = if (updateChecking) {
                                            { AppleActivityIndicator(size = 18.dp, color = colors.textSecondary) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                }

                // Sair da conta (destrutivo com confirmação)
                item(key = "logout_section") {
                    PortalCard(contentPadding = PaddingValues(0.dp)) {
                        PortalListRow(
                            title = "Sair da conta",
                            destructive = true,
                            onClick = {
                                if (onRequestLogout != null) onRequestLogout() else confirmLogout = true
                            }
                        )
                    }
                }
            }
        }

        if (renderSheetsInPlace && showAllNotifications) {
            AppleAllNotificationsSheet(
                notifications = notifications,
                onDismiss = onCloseAllNotifications
            )
        }

        if (confirmLogout) {
            AppleAlert(
                title = "Sair da conta?",
                message = "Sua sessão salva será removida deste dispositivo.",
                confirmText = "Sair",
                confirmDestructive = true,
                onConfirm = {
                    confirmLogout = false
                    onLogout()
                },
                onDismiss = { confirmLogout = false }
            )
        }
    }
}

@Composable
fun AppleAllNotificationsSheet(
    notifications: List<NotificationItem>,
    onDismiss: () -> Unit
) {
    AppleSheet(
        title = "Histórico de alertas",
        subtitle = "${notifications.size} notificações",
        onDismiss = onDismiss
    ) {
        PortalCard(
            modifier = Modifier.fillMaxWidth(),
            cardStyle = CardStyle.Plain,
            contentPadding = PaddingValues(0.dp)
        ) {
            notifications.forEachIndexed { idx, n ->
                if (idx > 0) PortalDivider(Modifier.padding(start = 16.dp))
                NotificationAlertRow(n = n)
            }
        }
    }
}

@Composable
private fun NotificationAlertRow(n: NotificationItem) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val isGrade = n.kind == "grade"
    val numericNew = n.newValue.replace(',', '.').toDoubleOrNull()
    val changeColor = when {
        !isGrade -> colors.orange
        numericNew != null && numericNew < 6.0 -> colors.red
        else -> colors.green
    }
    val oldDisplay = if (n.oldValue.isBlank()) "—" else n.oldValue
    val relativeTime = remember(n.createdAt) { formatRelativeTime(n.createdAt) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (n.readAt == null) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.accent)
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = n.disciplineName.toDisplayTitle(),
                color = colors.textPrimary,
                style = appleType.headline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${n.field}: $oldDisplay → ${n.newValue}",
                color = changeColor,
                style = appleType.subheadline,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = relativeTime,
            color = colors.textSecondary,
            style = appleType.footnote
        )
    }
}
