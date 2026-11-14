package me.muriloscolari.unicep.platform

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import kotlinx.browser.window

actual object PlatformBridge {
    actual fun capabilities(): DeviceCapabilities {
        val origin = window.location.origin
        val defaultUrl = if (origin.startsWith("http")) origin else "http://127.0.0.1:47832"
        return DeviceCapabilities(
            supportsAppleEffects = true,
            fallbackReason = null,
            defaultServerUrl = defaultUrl,
            isWeb = true
        )
    }

    actual fun getString(key: String): String? = runCatching { window.localStorage.getItem(key) }.getOrNull()

    actual fun putString(key: String, value: String) {
        runCatching { window.localStorage.setItem(key, value) }
    }

    actual fun remove(key: String) {
        runCatching { window.localStorage.removeItem(key) }
    }

    actual fun openExternalUrl(url: String) {
        window.open(url, "_blank")
    }

    actual fun scheduleGradeSync() {}

    actual fun cancelGradeSync() {}

    actual fun requestNotificationPermissionIfNeeded() {}

    actual fun createHttpEngine(): HttpClientEngine = Js.create()
}
