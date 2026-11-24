package me.muriloscolari.unicep.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import me.muriloscolari.unicep.data.api.VirtualContentModule
import me.muriloscolari.unicep.data.api.VirtualContentTopic
import me.muriloscolari.unicep.data.repo.VirtualDownloadRequest
import me.muriloscolari.unicep.platform.PlatformBridge
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import me.muriloscolari.unicep.data.repo.parseIso8601ToEpochMillis

internal enum class VirtualOverviewSubTab(val label: String) {
    COURSES("Disciplinas"),
    ASSIGNMENTS("Atividades"),
    UPDATES("Avisos")
}

internal enum class VirtualCourseSubTab(val label: String) {
    ASSIGNMENTS("Atividades"),
    CONTENT("Conteúdo"),
    GRADES("Notas"),
    MORE("Mais")
}

internal enum class AssignmentFilter(val label: String) {
    ALL("Todas"),
    PENDING("Pendentes"),
    GRADED("Avaliadas")
}

internal fun collectDownloadableRequests(
    modules: List<VirtualContentModule>,
    courseName: String
): List<VirtualDownloadRequest> {
    val out = mutableListOf<VirtualDownloadRequest>()
    fun walk(list: List<VirtualContentModule>) {
        list.forEach { mod ->
            mod.topics.forEach { t ->
                if (t.downloadPath.isNotBlank()) {
                    out.add(
                        VirtualDownloadRequest(
                            path = t.downloadPath,
                            fileName = inferTopicFileName(t),
                            courseName = courseName
                        )
                    )
                }
            }
            if (mod.modules.isNotEmpty()) walk(mod.modules)
        }
    }
    walk(modules)
    return out.distinctBy { it.path }
}

internal fun inferTopicFileName(topic: VirtualContentTopic): String {
    val fromUrl = topic.url
        .substringBefore('?')
        .substringAfterLast('/')
        .trim()
    if (fromUrl.contains('.') && fromUrl.length in 3..120) {
        return fromUrl
    }
    return topic.title.ifBlank { "material_${topic.topicId}" }
}

@Composable
internal fun assignmentStatusColor(status: String): Color {
    val colors = LocalPortalColors.current
    return when (status) {
        "graded" -> colors.green
        "submitted" -> colors.accent
        "expired" -> colors.textSecondary
        else -> colors.orange
    }
}

internal fun formatIsoDateTime(raw: String, millisHint: Long = 0L): String {
    if (millisHint > 0L) {
        val formatted = PlatformBridge.formatEpochMillisLocal(millisHint)
        if (formatted.isNotBlank()) return formatted
    }
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val parsedMillis = parseIso8601ToEpochMillis(trimmed)
    if (parsedMillis != null && parsedMillis > 0L) {
        val local = PlatformBridge.formatEpochMillisLocal(parsedMillis)
        if (local.isNotBlank()) return local
    }
    if (trimmed.length < 10) return ""
    val datePart = trimmed.substring(0, 10)
    val parts = datePart.split("-")
    if (parts.size != 3) return trimmed
    return "${parts[2]}/${parts[1]}/${parts[0]}"
}

internal fun formatScore(value: Double): String {
    val rounded = ((value * 100.0).toInt()) / 100.0
    val asInt = rounded.toInt()
    return if (rounded == asInt.toDouble()) asInt.toString() else rounded.toString().replace('.', ',')
}

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return ""
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        else -> "${((bytes * 10L) / (1024L * 1024L)) / 10.0} MB"
    }
}
