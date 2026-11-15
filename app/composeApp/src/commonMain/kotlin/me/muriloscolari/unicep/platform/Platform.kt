package me.muriloscolari.unicep.platform

import io.ktor.client.engine.HttpClientEngine

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

expect object PlatformBridge {
    fun capabilities(): DeviceCapabilities
    fun currentAppVersion(): String
    suspend fun checkForAppUpdate(): AppUpdateInfo?
    suspend fun downloadAndInstallAppUpdate(apkUrl: String, onProgress: (Float) -> Unit): Result<Unit>
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
    fun openExternalUrl(url: String)
    fun scheduleGradeSync()
    fun cancelGradeSync()
    fun requestNotificationPermissionIfNeeded()
    fun postSystemNotification(id: Int, title: String, body: String, openTab: Int = 2): Boolean
    fun initNavigationHistory(initialRoute: String)
    fun pushNavigationRoute(route: String)
    fun setPopRouteListener(listener: (String) -> Unit)
    fun setAndroidBackHandler(handler: (() -> Boolean)?)
    fun createHttpEngine(): HttpClientEngine
    fun currentTimeMillis(): Long
    fun isReduceMotionEnabled(): Boolean
}
