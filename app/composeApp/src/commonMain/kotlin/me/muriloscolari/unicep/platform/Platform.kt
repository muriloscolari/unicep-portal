package me.muriloscolari.unicep.platform

import androidx.compose.runtime.Composable
import io.ktor.client.engine.HttpClientEngine
import me.muriloscolari.unicep.data.api.UserCalendarEvent

object AppTabIndex {
    const val SEMESTER = 0
    const val COURSE = 1
    const val VIRTUAL = 2
    const val CALENDAR = 3
    const val CENTRAL = 4
}

enum class DesignStylePreference(val label: String) {
    AUTO("Automático"),
    APPLE("Estilo Apple"),
    MATERIAL("Material Design 3")
}

enum class DesignStyle {
    APPLE,
    MATERIAL
}

data class DeviceCapabilities(
    val supportsAppleEffects: Boolean,
    val fallbackReason: String?,
    val defaultServerUrl: String,
    val isWeb: Boolean
)

data class AppUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val releaseName: String,
    val releaseNotes: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val htmlUrl: String
)

data class ExportSection(
    val heading: String,
    val badge: String = "",
    val lines: List<String> = emptyList()
)

data class ExportCardData(
    val title: String,
    val subtitle: String = "",
    val footer: String = "",
    val sections: List<ExportSection> = emptyList(),
    val plainText: String = ""
)

enum class PortalHapticType {
    IMPACT_LIGHT,
    IMPACT_MEDIUM,
    IMPACT_HEAVY,
    BOUNDARY_THUD,
    SELECTION
}

data class LocalDateTimeInfo(
    val dayOfWeekIso: Int, // 1 = Segunda .. 7 = Domingo
    val hour: Int,
    val minute: Int,
    val year: Int = 2026,
    val month: Int = 1,
    val day: Int = 1,
    val utcOffsetMinutes: Int = -180
)

data class PredictiveBackState(
    val active: Boolean = false,
    val progress: Float = 0f,
    val swipeEdge: Int = 0
)

expect object PlatformBridge {
    fun capabilities(): DeviceCapabilities
    fun currentAppVersion(): String
    suspend fun checkForAppUpdate(): AppUpdateInfo?
    suspend fun downloadAndInstallAppUpdate(apkUrl: String, onProgress: (Float) -> Unit): Result<Unit>
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
    fun openExternalUrl(url: String): Boolean
    fun shareText(title: String, text: String): Boolean
    fun exportPng(fileName: String, data: ExportCardData): Boolean
    fun printDocument(title: String, data: ExportCardData): Boolean
    fun performHaptic(type: PortalHapticType, intensity: Float = 1f)
    fun scheduleGradeSync()
    fun cancelGradeSync()
    fun syncCalendarReminders(events: List<UserCalendarEvent>)
    fun cancelAllCalendarReminders()
    fun requestNotificationPermissionIfNeeded()
    fun postSystemNotification(id: Int, title: String, body: String, openTab: Int = 3): Boolean
    fun initNavigationHistory(initialRoute: String)
    fun pushNavigationRoute(route: String)
    fun setPopRouteListener(listener: (String) -> Unit)
    fun setOpenTabListener(listener: ((Int) -> Unit)?)
    fun dispatchOpenTab(tab: Int)
    fun setAndroidBackHandler(handler: (() -> Boolean)?)
    fun setAndroidBackEnabled(enabled: Boolean)
    fun setAndroidPredictiveBackListener(listener: ((PredictiveBackState) -> Unit)?)
    fun createHttpEngine(): HttpClientEngine
    fun currentTimeMillis(): Long
    fun currentLocalDateTime(): LocalDateTimeInfo
    fun formatEpochMillisLocal(millis: Long): String
    fun isReduceMotionEnabled(): Boolean
    fun supportsInAppDocumentViewer(): Boolean
    fun supportsCustomDownloadFolder(): Boolean
    fun isUsingCustomDownloadFolder(): Boolean
    fun getDownloadFolderLabel(): String
    suspend fun pickDownloadFolder(): String?
    fun clearDownloadFolder()
    suspend fun saveDownloadedFile(
        courseName: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Result<String>
    fun triggerBrowserDownload(url: String, fileName: String)
}

@Composable
expect fun PlatformDocumentViewer(
    title: String,
    courseName: String,
    fileName: String,
    mimeType: String,
    bytes: ByteArray?,
    fallbackUrl: String,
    downloading: Boolean,
    onDownload: () -> Unit,
    onClose: () -> Unit
)
