@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package me.muriloscolari.unicep.platform

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import kotlinx.browser.window

private fun jsCurrentTimeMillis(): Double = js("Date.now()")

private fun jsPrefersReducedMotion(): Boolean = js(
    """(() => {
        try { return !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches); }
        catch (e) { return false; }
    })()"""
)

private fun jsShowWebNotification(title: String, body: String): Boolean = js(
    """{
        try {
            if (!('Notification' in window)) return false;
            if (Notification.permission === 'granted') {
                new Notification(title, { body: body });
                return true;
            }
            if (Notification.permission !== 'denied') {
                Notification.requestPermission().then(function(p) {
                    if (p === 'granted') new Notification(title, { body: body });
                });
                return true;
            }
            return false;
        } catch (e) {
            return false;
        }
    }"""
)

private fun jsRequestWebNotificationPermission(): Unit = js(
    """{
        try {
            if ('Notification' in window && Notification.permission === 'default') {
                Notification.requestPermission();
            }
        } catch (e) {}
    }"""
)

private fun jsReplaceHistoryRoute(route: String): Unit = js(
    """{
        try {
            window.history.replaceState({ route: route }, '', '#' + route);
        } catch (e) {}
    }"""
)

private fun jsPushHistoryRoute(route: String): Unit = js(
    """{
        try {
            window.history.pushState({ route: route }, '', '#' + route);
        } catch (e) {}
    }"""
)

private fun jsReadCurrentRoute(): String = js(
    """{
        try {
            var s = window.history.state;
            if (s && typeof s.route === 'string') return s.route;
            var h = window.location.hash || '';
            return h.startsWith('#') ? h.slice(1) : '';
        } catch (e) {
            return '';
        }
    }"""
)

actual object PlatformBridge {
    private var popListener: ((String) -> Unit)? = null
    private var historyInitialized = false
    private var rootRoute = "semestre"
    private var lastKnownRoute = ""

    init {
        runCatching {
            window.addEventListener("popstate") {
                val route = jsReadCurrentRoute()
                if (route == "guard") {
                    jsPushHistoryRoute(rootRoute)
                    lastKnownRoute = rootRoute
                    popListener?.invoke(rootRoute)
                } else if (route.isNotBlank()) {
                    lastKnownRoute = route
                    popListener?.invoke(route)
                }
            }
        }
    }

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
        val clean = url.trim()
        if (!clean.startsWith("https://", ignoreCase = true) && !clean.startsWith("http://", ignoreCase = true)) {
            return
        }
        window.open(clean, "_blank", "noopener,noreferrer")
    }

    actual fun scheduleGradeSync() {}

    actual fun cancelGradeSync() {}

    actual fun requestNotificationPermissionIfNeeded() {
        jsRequestWebNotificationPermission()
    }

    actual fun postSystemNotification(id: Int, title: String, body: String, openTab: Int): Boolean {
        return jsShowWebNotification(title, body)
    }

    actual fun initNavigationHistory(initialRoute: String) {
        rootRoute = initialRoute
        if (historyInitialized) return
        historyInitialized = true
        val existing = jsReadCurrentRoute()
        if (existing.isBlank() || existing == "guard") {
            jsReplaceHistoryRoute("guard")
            jsPushHistoryRoute(initialRoute)
            lastKnownRoute = initialRoute
        } else {
            jsReplaceHistoryRoute(existing)
            lastKnownRoute = existing
        }
    }

    actual fun pushNavigationRoute(route: String) {
        if (!historyInitialized) {
            initNavigationHistory(route)
            return
        }
        if (route == lastKnownRoute) return
        lastKnownRoute = route
        jsPushHistoryRoute(route)
    }

    actual fun setPopRouteListener(listener: (String) -> Unit) {
        popListener = listener
    }

    actual fun setAndroidBackHandler(handler: (() -> Boolean)?) {}

    actual fun currentAppVersion(): String = "web"

    actual suspend fun checkForAppUpdate(): AppUpdateInfo? = null

    actual suspend fun downloadAndInstallAppUpdate(apkUrl: String, onProgress: (Float) -> Unit): Result<Unit> =
        Result.failure(UnsupportedOperationException("Atualização automática disponível apenas no Android."))

    actual fun createHttpEngine(): HttpClientEngine = Js.create()

    actual fun currentTimeMillis(): Long = jsCurrentTimeMillis().toLong()

    actual fun isReduceMotionEnabled(): Boolean = jsPrefersReducedMotion()
}
