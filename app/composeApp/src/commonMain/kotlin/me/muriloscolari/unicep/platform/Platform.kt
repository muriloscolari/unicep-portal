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

expect object PlatformBridge {
    fun capabilities(): DeviceCapabilities
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
    fun openExternalUrl(url: String)
    fun scheduleGradeSync()
    fun cancelGradeSync()
    fun requestNotificationPermissionIfNeeded()
    fun createHttpEngine(): HttpClientEngine
}
