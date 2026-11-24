package me.muriloscolari.unicep.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.muriloscolari.unicep.data.api.VirtualAnnouncement
import me.muriloscolari.unicep.data.api.VirtualAssignmentItem
import me.muriloscolari.unicep.data.api.VirtualFileAttachment
import me.muriloscolari.unicep.data.repo.VirtualDownloadRequest
import me.muriloscolari.unicep.platform.DesignStyle
import me.muriloscolari.unicep.platform.PlatformBridge
import me.muriloscolari.unicep.platform.PlatformVerticalScrollbar
import me.muriloscolari.unicep.platform.PortalHapticType
import me.muriloscolari.unicep.ui.designsystem.AppleSheet
import me.muriloscolari.unicep.ui.designsystem.AppleSheetDetent
import me.muriloscolari.unicep.ui.designsystem.CopyTextButton
import me.muriloscolari.unicep.ui.designsystem.LocalAppleTypography
import me.muriloscolari.unicep.ui.designsystem.LocalDesignStyle
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.ui.designsystem.PortalBadge
import me.muriloscolari.unicep.ui.designsystem.PortalCard
import me.muriloscolari.unicep.ui.designsystem.PortalDivider
import me.muriloscolari.unicep.ui.designsystem.PortalIcons
import me.muriloscolari.unicep.ui.designsystem.PortalListRow
import me.muriloscolari.unicep.ui.designsystem.PortalSecondaryButton
import me.muriloscolari.unicep.ui.designsystem.PortalSectionHeader
import me.muriloscolari.unicep.ui.designsystem.SelectableRichText
import me.muriloscolari.unicep.ui.util.isInAppPreviewSupported
import me.muriloscolari.unicep.ui.util.shouldDirectDownloadOnWeb
import me.muriloscolari.unicep.ui.util.toDisplayTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirtualAssignmentSheet(
    assignment: VirtualAssignmentItem,
    onViewD2LFile: (path: String, title: String, courseName: String, fileName: String) -> Unit,
    onDownloadD2LFile: (path: String, fileName: String, courseName: String) -> Unit,
    onDownloadBatch: (List<VirtualDownloadRequest>) -> Unit,
    downloadingFileKey: String?,
    onOpenWeb: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val style = LocalDesignStyle.current
    val subtitle = assignment.orgUnitName.takeIf { it.isNotBlank() }?.toDisplayTitle()
    if (style == DesignStyle.APPLE) {
        AppleSheet(
            title = assignment.name,
            subtitle = subtitle,
            onDismiss = onDismiss,
            initialDetent = AppleSheetDetent.Large,
            desktopMaxWidth = 720.dp
        ) {
            VirtualAssignmentSheetContent(
                assignment = assignment,
                onViewD2LFile = onViewD2LFile,
                onDownloadD2LFile = onDownloadD2LFile,
                onDownloadBatch = onDownloadBatch,
                downloadingFileKey = downloadingFileKey,
                onOpenWeb = onOpenWeb
            )
        }
    } else {
        val scrollState = rememberScrollState()
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    SelectionContainer {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = assignment.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = subtitle,
                                    color = LocalPortalColors.current.textSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    VirtualAssignmentSheetContent(
                        assignment = assignment,
                        onViewD2LFile = onViewD2LFile,
                        onDownloadD2LFile = onDownloadD2LFile,
                        onDownloadBatch = onDownloadBatch,
                        downloadingFileKey = downloadingFileKey,
                        onOpenWeb = onOpenWeb
                    )
                    Spacer(Modifier.height(24.dp))
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(vertical = 8.dp, horizontal = 3.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    PlatformVerticalScrollbar(
                        scrollState = scrollState,
                        modifier = Modifier.fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun VirtualAssignmentSheetContent(
    assignment: VirtualAssignmentItem,
    onViewD2LFile: (path: String, title: String, courseName: String, fileName: String) -> Unit,
    onDownloadD2LFile: (path: String, fileName: String, courseName: String) -> Unit,
    onDownloadBatch: (List<VirtualDownloadRequest>) -> Unit,
    downloadingFileKey: String?,
    onOpenWeb: (String) -> Unit
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val badgeColor = assignmentStatusColor(assignment.status)
    val courseName = assignment.orgUnitName.ifBlank { "Sala Virtual" }.toDisplayTitle()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PortalBadge(text = assignment.statusLabel, color = badgeColor)
        if (assignment.score != null) {
            val maxPart = assignment.outOf?.let { " / ${formatScore(it)}" }.orEmpty()
            PortalBadge(
                text = "Nota: ${formatScore(assignment.score)}$maxPart",
                color = colors.green
            )
        } else if (assignment.outOf != null) {
            PortalBadge(
                text = "Vale ${formatScore(assignment.outOf)} pts",
                color = colors.accent
            )
        }
    }

    val dueText = formatIsoDateTime(assignment.dueDate.ifBlank { assignment.endDate }, assignment.dueAtMillis)
    if (dueText.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        SelectionContainer {
            Text(
                text = "Prazo de entrega: $dueText",
                color = colors.textSecondary,
                fontSize = 13.sp
            )
        }
    }

    if (assignment.instructions.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        PortalSectionHeader(
            title = "Instruções",
            trailing = { CopyTextButton(textToCopy = assignment.instructions) }
        )
        Spacer(Modifier.height(4.dp))
        PortalCard {
            SelectableRichText(
                text = assignment.instructions,
                color = colors.textPrimary,
                style = appleType.subheadline.copy(fontSize = 13.5.sp, lineHeight = 20.sp)
            )
        }
    }

    if (assignment.attachments.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        val downloadable = assignment.attachments.filter { it.downloadPath.isNotBlank() }
        PortalSectionHeader(
            title = "Anexos da Atividade",
            trailing = if (downloadable.size > 1) {
                {
                    TextButton(
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        onClick = {
                            onDownloadBatch(
                                downloadable.map {
                                    VirtualDownloadRequest(
                                        path = it.downloadPath,
                                        fileName = it.fileName,
                                        courseName = courseName
                                    )
                                }
                            )
                        }
                    ) {
                        Text("Baixar todos (${downloadable.size})", color = colors.accent, fontSize = 12.sp)
                    }
                }
            } else null
        )
        Spacer(Modifier.height(4.dp))
        PortalCard(contentPadding = PaddingValues(0.dp)) {
            assignment.attachments.forEachIndexed { idx, att ->
                if (idx > 0) PortalDivider()
                AttachmentRow(
                    attachment = att,
                    isDownloading = att.downloadPath.isNotBlank() && downloadingFileKey == att.downloadPath,
                    onView = {
                        if (att.downloadPath.isNotBlank()) {
                            onViewD2LFile(att.downloadPath, att.fileName, courseName, att.fileName)
                        }
                    },
                    onDownload = {
                        if (att.downloadPath.isNotBlank()) {
                            onDownloadD2LFile(att.downloadPath, att.fileName, courseName)
                        }
                    }
                )
            }
        }
    }

    if (assignment.submittedFiles.isNotEmpty() || assignment.submissionDate.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        val downloadableSubmitted = assignment.submittedFiles.filter { it.downloadPath.isNotBlank() }
        PortalSectionHeader(
            title = "Seu Envio",
            trailing = if (downloadableSubmitted.size > 1) {
                {
                    TextButton(
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        onClick = {
                            onDownloadBatch(
                                downloadableSubmitted.map {
                                    VirtualDownloadRequest(
                                        path = it.downloadPath,
                                        fileName = it.fileName,
                                        courseName = courseName
                                    )
                                }
                            )
                        }
                    ) {
                        Text("Baixar todos (${downloadableSubmitted.size})", color = colors.accent, fontSize = 12.sp)
                    }
                }
            } else null
        )
        Spacer(Modifier.height(4.dp))
        PortalCard(contentPadding = PaddingValues(0.dp)) {
            if (assignment.submissionDate.isNotBlank()) {
                SelectionContainer {
                    Text(
                        text = "Enviado em ${formatIsoDateTime(assignment.submissionDate)}",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                if (assignment.submittedFiles.isNotEmpty()) {
                    PortalDivider()
                }
            }
            assignment.submittedFiles.forEachIndexed { idx, sf ->
                if (idx > 0) PortalDivider()
                if (sf.downloadPath.isNotBlank()) {
                    AttachmentRow(
                        attachment = sf,
                        isDownloading = downloadingFileKey == sf.downloadPath,
                        onView = {
                            onViewD2LFile(sf.downloadPath, sf.fileName, courseName, sf.fileName)
                        },
                        onDownload = {
                            onDownloadD2LFile(sf.downloadPath, sf.fileName, courseName)
                        }
                    )
                } else {
                    SelectionContainer {
                        PortalListRow(
                            title = sf.fileName,
                            subtitle = formatBytes(sf.size).ifBlank { "Atualize a atividade para baixar" },
                            leadingIcon = PortalIcons.Document,
                            leadingTint = colors.accent,
                            showChevron = false,
                            onClick = null
                        )
                    }
                }
            }
        }
    }

    if (assignment.feedbackText.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        PortalSectionHeader(
            title = "Feedback do Professor",
            trailing = { CopyTextButton(textToCopy = assignment.feedbackText) }
        )
        Spacer(Modifier.height(4.dp))
        PortalCard {
            SelectableRichText(
                text = assignment.feedbackText,
                color = colors.textPrimary,
                style = appleType.subheadline.copy(fontSize = 13.5.sp, lineHeight = 20.sp)
            )
        }
    }

    Spacer(Modifier.height(14.dp))
    PortalSecondaryButton(
        text = "Abrir entrega no navegador",
        onClick = { onOpenWeb(assignment.webUrl) },
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirtualAnnouncementSheet(
    announcement: VirtualAnnouncement,
    onViewD2LFile: (path: String, title: String, courseName: String, fileName: String) -> Unit,
    onDownloadD2LFile: (path: String, fileName: String, courseName: String) -> Unit,
    onDownloadBatch: (List<VirtualDownloadRequest>) -> Unit,
    downloadingFileKey: String?,
    onDismiss: () -> Unit
) {
    val style = LocalDesignStyle.current
    val subtitle = listOfNotNull(
        announcement.orgUnitName.takeIf { it.isNotBlank() }?.toDisplayTitle(),
        formatIsoDateTime(announcement.startDate.ifBlank { announcement.createdDate }).takeIf { it.isNotBlank() }
    ).joinToString(" · ").ifBlank { null }

    if (style == DesignStyle.APPLE) {
        AppleSheet(
            title = announcement.title,
            subtitle = subtitle,
            onDismiss = onDismiss,
            initialDetent = AppleSheetDetent.Medium,
            desktopMaxWidth = 720.dp
        ) {
            VirtualAnnouncementSheetContent(
                announcement = announcement,
                onViewD2LFile = onViewD2LFile,
                onDownloadD2LFile = onDownloadD2LFile,
                onDownloadBatch = onDownloadBatch,
                downloadingFileKey = downloadingFileKey
            )
        }
    } else {
        val scrollState = rememberScrollState()
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    SelectionContainer {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = announcement.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = subtitle,
                                    color = LocalPortalColors.current.textSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    VirtualAnnouncementSheetContent(
                        announcement = announcement,
                        onViewD2LFile = onViewD2LFile,
                        onDownloadD2LFile = onDownloadD2LFile,
                        onDownloadBatch = onDownloadBatch,
                        downloadingFileKey = downloadingFileKey
                    )
                    Spacer(Modifier.height(24.dp))
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(vertical = 8.dp, horizontal = 3.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    PlatformVerticalScrollbar(
                        scrollState = scrollState,
                        modifier = Modifier.fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun VirtualAnnouncementSheetContent(
    announcement: VirtualAnnouncement,
    onViewD2LFile: (path: String, title: String, courseName: String, fileName: String) -> Unit,
    onDownloadD2LFile: (path: String, fileName: String, courseName: String) -> Unit,
    onDownloadBatch: (List<VirtualDownloadRequest>) -> Unit,
    downloadingFileKey: String?
) {
    val colors = LocalPortalColors.current
    val appleType = LocalAppleTypography.current
    val courseName = announcement.orgUnitName.ifBlank { "Avisos Sala Virtual" }.toDisplayTitle()
    if (announcement.body.isNotBlank()) {
        PortalSectionHeader(
            title = "Comunicado",
            trailing = { CopyTextButton(textToCopy = announcement.body) }
        )
        Spacer(Modifier.height(4.dp))
        PortalCard {
            SelectableRichText(
                text = announcement.body,
                color = colors.textPrimary,
                style = appleType.subheadline.copy(lineHeight = 21.sp)
            )
        }
    }
    if (announcement.attachments.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        val downloadable = announcement.attachments.filter { it.downloadPath.isNotBlank() }
        PortalSectionHeader(
            title = "Anexos",
            trailing = if (downloadable.size > 1) {
                {
                    TextButton(
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        onClick = {
                            onDownloadBatch(
                                downloadable.map {
                                    VirtualDownloadRequest(
                                        path = it.downloadPath,
                                        fileName = it.fileName,
                                        courseName = courseName
                                    )
                                }
                            )
                        }
                    ) {
                        Text("Baixar todos (${downloadable.size})", color = colors.accent, fontSize = 12.sp)
                    }
                }
            } else null
        )
        Spacer(Modifier.height(4.dp))
        PortalCard(contentPadding = PaddingValues(0.dp)) {
            announcement.attachments.forEachIndexed { idx, att ->
                if (idx > 0) PortalDivider()
                AttachmentRow(
                    attachment = att,
                    isDownloading = att.downloadPath.isNotBlank() && downloadingFileKey == att.downloadPath,
                    onView = {
                        if (att.downloadPath.isNotBlank()) {
                            onViewD2LFile(att.downloadPath, att.fileName, courseName, att.fileName)
                        }
                    },
                    onDownload = {
                        if (att.downloadPath.isNotBlank()) {
                            onDownloadD2LFile(att.downloadPath, att.fileName, courseName)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AttachmentRow(
    attachment: VirtualFileAttachment,
    isDownloading: Boolean,
    onView: () -> Unit,
    onDownload: () -> Unit
) {
    val colors = LocalPortalColors.current
    val isWeb = remember { PlatformBridge.capabilities().isWeb }
    val sizeLabel = formatBytes(attachment.size)
    val actionHint = if (isWeb) {
        if (shouldDirectDownloadOnWeb(attachment.fileName)) "Clique para baixar" else "Clique para abrir"
    } else {
        if (isInAppPreviewSupported(attachment.fileName)) "Toque para abrir" else "Toque para baixar ou abrir"
    }
    val subtitle = if (sizeLabel.isNotBlank()) "$sizeLabel · $actionHint" else actionHint
    PortalListRow(
        title = attachment.fileName,
        subtitle = subtitle,
        leadingIcon = PortalIcons.Document,
        leadingTint = colors.accent,
        trailing = {
            if (attachment.downloadPath.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceSecondary)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(enabled = !isDownloading) {
                            PlatformBridge.performHaptic(PortalHapticType.SELECTION)
                            onDownload()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = colors.accent,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = PortalIcons.Download,
                            contentDescription = "Baixar anexo",
                            tint = colors.accent,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        },
        onClick = if (attachment.downloadPath.isNotBlank()) onView else null
    )
}
