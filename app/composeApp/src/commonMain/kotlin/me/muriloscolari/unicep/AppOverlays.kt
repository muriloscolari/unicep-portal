package me.muriloscolari.unicep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.muriloscolari.unicep.data.api.CalendarEventInput
import me.muriloscolari.unicep.data.api.UserCalendarEvent
import me.muriloscolari.unicep.data.api.VirtualAnnouncement
import me.muriloscolari.unicep.data.api.VirtualAssignmentItem
import me.muriloscolari.unicep.data.repo.CalendarDate
import me.muriloscolari.unicep.data.repo.GlobalSearchResult
import me.muriloscolari.unicep.data.repo.PortalUiState
import me.muriloscolari.unicep.data.repo.SubjectVirtualLink
import me.muriloscolari.unicep.data.repo.VirtualDownloadRequest
import me.muriloscolari.unicep.platform.AppUpdateInfo
import me.muriloscolari.unicep.platform.DesignStyle
import me.muriloscolari.unicep.ui.designsystem.AppleAlert
import me.muriloscolari.unicep.ui.designsystem.AppleProgressBar
import me.muriloscolari.unicep.ui.designsystem.LocalAppleTypography
import me.muriloscolari.unicep.ui.designsystem.LocalDesignStyle
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.ui.screens.AppleAllNotificationsSheet
import me.muriloscolari.unicep.ui.screens.AppleAttendanceLimitSheet
import me.muriloscolari.unicep.ui.screens.AppleSubjectDetailSheet
import me.muriloscolari.unicep.ui.screens.CalendarEventEditorSheet
import me.muriloscolari.unicep.ui.screens.GlobalSearchSheet
import me.muriloscolari.unicep.ui.screens.UnifiedSubject
import me.muriloscolari.unicep.ui.screens.VirtualAnnouncementSheet
import me.muriloscolari.unicep.ui.screens.VirtualAssignmentSheet

@Composable
internal fun AppSheetsAndDialogsHost(
    state: PortalUiState,
    mergedSubjects: List<UnifiedSubject>,
    activeSubject: UnifiedSubject?,
    activeSubjectVirtualLink: SubjectVirtualLink?,
    riskySubjects: List<UnifiedSubject>,
    selectedVirtualAssignment: VirtualAssignmentItem?,
    selectedVirtualAnnouncement: VirtualAnnouncement?,
    showRiskyAttendanceModal: Boolean,
    showAllNotificationsModal: Boolean,
    showCalendarEditor: Boolean,
    editingCalendarEvent: UserCalendarEvent?,
    calendarDraftDate: CalendarDate?,
    selectedCalendarDate: CalendarDate,
    calendarSubjectSuggestions: List<String>,
    showGlobalSearchModal: Boolean,
    confirmDeleteCalendarEvent: UserCalendarEvent?,
    confirmLogoutDialog: Boolean,
    showUpdateDialog: Boolean,
    availableUpdate: AppUpdateInfo?,
    updateDownloading: Boolean,
    updateProgress: Float,
    updateStatusMessage: String?,
    onViewD2LFile: (path: String, title: String, courseName: String, fileName: String) -> Unit,
    onDownloadD2LFile: (path: String, fileName: String, courseName: String) -> Unit,
    onDownloadBatch: (List<VirtualDownloadRequest>) -> Unit,
    onOpenSalaVirtualWeb: (String) -> Unit,
    onDismissVirtualAssignment: () -> Unit,
    onDismissVirtualAnnouncement: () -> Unit,
    onDismissSubjectDetail: () -> Unit,
    onOpenVirtualCourseFromSubject: (Long) -> Unit,
    onOpenVirtualAssignmentFromSubject: (VirtualAssignmentItem) -> Unit,
    onOpenVirtualTabFromSubject: () -> Unit,
    onSelectRiskySubject: (String) -> Unit,
    onDismissRiskyAttendance: () -> Unit,
    onDismissAllNotifications: () -> Unit,
    onSaveCalendarEvent: (CalendarEventInput) -> Unit,
    onRequestDeleteCalendarEvent: (UserCalendarEvent) -> Unit,
    onDismissCalendarEditor: () -> Unit,
    onSelectGlobalSearchResult: (GlobalSearchResult) -> Unit,
    onDismissGlobalSearch: () -> Unit,
    onConfirmDeleteCalendarEvent: (UserCalendarEvent) -> Unit,
    onDismissDeleteCalendarEvent: () -> Unit,
    onConfirmLogout: () -> Unit,
    onDismissLogout: () -> Unit,
    onInstallAppUpdate: () -> Unit,
    onDismissUpdateDialog: () -> Unit
) {
    val style = LocalDesignStyle.current
    val colors = LocalPortalColors.current
    val isApple = style == DesignStyle.APPLE

    when {
        state.activeDocumentViewer != null -> Unit
        selectedVirtualAssignment != null -> VirtualAssignmentSheet(
            assignment = selectedVirtualAssignment,
            onViewD2LFile = onViewD2LFile,
            onDownloadD2LFile = onDownloadD2LFile,
            onDownloadBatch = onDownloadBatch,
            downloadingFileKey = state.downloadingFileKey,
            onOpenWeb = onOpenSalaVirtualWeb,
            onDismiss = onDismissVirtualAssignment
        )
        selectedVirtualAnnouncement != null -> VirtualAnnouncementSheet(
            announcement = selectedVirtualAnnouncement,
            onViewD2LFile = onViewD2LFile,
            onDownloadD2LFile = onDownloadD2LFile,
            onDownloadBatch = onDownloadBatch,
            downloadingFileKey = state.downloadingFileKey,
            onDismiss = onDismissVirtualAnnouncement
        )
        activeSubject != null -> AppleSubjectDetailSheet(
            subject = activeSubject,
            freqAlertThreshold = state.freqAlertThreshold,
            virtualLink = activeSubjectVirtualLink,
            onOpenVirtualCourse = onOpenVirtualCourseFromSubject,
            onOpenVirtualAssignment = onOpenVirtualAssignmentFromSubject,
            onOpenVirtualTab = onOpenVirtualTabFromSubject,
            onDismiss = onDismissSubjectDetail
        )
        showRiskyAttendanceModal && riskySubjects.isNotEmpty() -> AppleAttendanceLimitSheet(
            subjects = riskySubjects,
            onSelectSubject = onSelectRiskySubject,
            onDismiss = onDismissRiskyAttendance
        )
        showAllNotificationsModal -> AppleAllNotificationsSheet(
            notifications = state.notifications,
            onDismiss = onDismissAllNotifications
        )
        showCalendarEditor -> CalendarEventEditorSheet(
            existing = editingCalendarEvent,
            initialDate = calendarDraftDate ?: selectedCalendarDate,
            subjectSuggestions = calendarSubjectSuggestions,
            saving = state.calendarSaving,
            onSave = onSaveCalendarEvent,
            onRequestDelete = editingCalendarEvent?.let { ev ->
                { onRequestDeleteCalendarEvent(ev) }
            },
            onDismiss = onDismissCalendarEditor
        )
        showGlobalSearchModal -> GlobalSearchSheet(
            subjects = mergedSubjects,
            schedule = state.schedule,
            curriculum = state.curriculum,
            virtualOverview = state.virtualOverview,
            virtualCourseDetails = state.virtualCourseDetails,
            calendarEvents = state.calendarEvents,
            onSelectResult = onSelectGlobalSearchResult,
            onDismiss = onDismissGlobalSearch
        )
    }

    confirmDeleteCalendarEvent?.let { targetEvent ->
        if (isApple) {
            AppleAlert(
                title = "Excluir evento?",
                message = "Deseja remover \"${targetEvent.title}\" do seu calendário?",
                confirmText = "Excluir",
                confirmDestructive = true,
                onConfirm = { onConfirmDeleteCalendarEvent(targetEvent) },
                onDismiss = onDismissDeleteCalendarEvent
            )
        } else {
            AlertDialog(
                onDismissRequest = onDismissDeleteCalendarEvent,
                containerColor = colors.surface,
                titleContentColor = colors.textPrimary,
                textContentColor = colors.textSecondary,
                title = { Text("Excluir evento?", fontWeight = FontWeight.Bold) },
                text = { Text("Deseja remover \"${targetEvent.title}\" do seu calendário?") },
                confirmButton = {
                    TextButton(onClick = { onConfirmDeleteCalendarEvent(targetEvent) }) {
                        Text("Excluir", color = colors.red, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissDeleteCalendarEvent) {
                        Text("Cancelar", color = colors.textSecondary)
                    }
                }
            )
        }
    }

    if (confirmLogoutDialog) {
        AppleAlert(
            title = "Sair da conta?",
            message = "Sua sessão salva será removida deste dispositivo.",
            confirmText = "Sair",
            confirmDestructive = true,
            onConfirm = onConfirmLogout,
            onDismiss = onDismissLogout
        )
    }

    if (showUpdateDialog && availableUpdate != null) {
        if (isApple) {
            AppleAlert(
                title = "Atualização disponível (v${availableUpdate.latestVersion})",
                message = buildString {
                    append("Versão instalada: v${availableUpdate.currentVersion} → v${availableUpdate.latestVersion}")
                    if (availableUpdate.releaseName.isNotBlank()) append("\n${availableUpdate.releaseName}")
                    if (availableUpdate.releaseNotes.isNotBlank()) append("\n\n${availableUpdate.releaseNotes}")
                },
                confirmText = if (updateDownloading) "Baixando..." else "Atualizar",
                dismissText = "Depois",
                confirmEnabled = !updateDownloading,
                dismissEnabled = !updateDownloading,
                onConfirm = onInstallAppUpdate,
                onDismiss = onDismissUpdateDialog,
                extraContent = if (updateDownloading) {
                    {
                        AppleProgressBar(progress = updateProgress, color = colors.accent)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Baixando atualização (${(updateProgress * 100).toInt()}%)…",
                            color = colors.textSecondary,
                            style = LocalAppleTypography.current.caption1
                        )
                    }
                } else if (!updateStatusMessage.isNullOrBlank() && updateStatusMessage.contains("Permita", ignoreCase = true)) {
                    {
                        Text(
                            text = updateStatusMessage,
                            color = colors.orange,
                            style = LocalAppleTypography.current.caption1
                        )
                    }
                } else null
            )
        } else {
            AlertDialog(
                onDismissRequest = { if (!updateDownloading) onDismissUpdateDialog() },
                containerColor = colors.surface,
                titleContentColor = colors.textPrimary,
                textContentColor = colors.textSecondary,
                title = { Text("Atualização disponível (v${availableUpdate.latestVersion})", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Versão instalada: v${availableUpdate.currentVersion} → Nova: v${availableUpdate.latestVersion}")
                        if (availableUpdate.releaseName.isNotBlank()) {
                            Text(availableUpdate.releaseName, color = colors.textPrimary, fontWeight = FontWeight.Medium)
                        }
                        if (availableUpdate.releaseNotes.isNotBlank()) {
                            Text(availableUpdate.releaseNotes, fontSize = 12.5.sp)
                        }
                        if (updateDownloading) {
                            LinearProgressIndicator(
                                progress = { updateProgress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = colors.accent,
                                trackColor = colors.accentSoft
                            )
                            Text("Baixando atualização (${(updateProgress * 100).toInt()}%)...", fontSize = 12.sp)
                        } else if (!updateStatusMessage.isNullOrBlank() && updateStatusMessage.contains("Permita", ignoreCase = true)) {
                            Text(updateStatusMessage, color = colors.orange, fontSize = 12.5.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = onInstallAppUpdate,
                        enabled = !updateDownloading
                    ) {
                        Text(
                            if (updateDownloading) "Baixando..." else "Atualizar agora",
                            color = colors.accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onDismissUpdateDialog,
                        enabled = !updateDownloading
                    ) {
                        Text("Depois", color = colors.textSecondary)
                    }
                }
            )
        }
    }
}
