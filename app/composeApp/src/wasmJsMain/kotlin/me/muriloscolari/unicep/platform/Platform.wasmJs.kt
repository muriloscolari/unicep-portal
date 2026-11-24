@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package me.muriloscolari.unicep.platform

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import kotlinx.browser.document
import kotlinx.browser.window
import me.muriloscolari.unicep.data.api.UserCalendarEvent
import me.muriloscolari.unicep.ui.designsystem.LocalPortalColors
import org.w3c.dom.events.Event

private fun jsonString(s: String): String = buildString(s.length + 2) {
    append('"')
    for (ch in s) {
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (ch.code < 0x20) append("\\u${ch.code.toString(16).padStart(4, '0')}") else append(ch)
        }
    }
    append('"')
}

private fun encodeExportJson(data: ExportCardData): String = buildString {
    append('{')
    append("\"title\":").append(jsonString(data.title)).append(',')
    append("\"subtitle\":").append(jsonString(data.subtitle)).append(',')
    append("\"footer\":").append(jsonString(data.footer)).append(',')
    append("\"sections\":[")
    data.sections.forEachIndexed { idx, sec ->
        if (idx > 0) append(',')
        append('{')
        append("\"heading\":").append(jsonString(sec.heading)).append(',')
        append("\"badge\":").append(jsonString(sec.badge)).append(',')
        append("\"lines\":[")
        sec.lines.forEachIndexed { lIdx, line ->
            if (lIdx > 0) append(',')
            append(jsonString(line))
        }
        append("]}")
    }
    append("]}")
}

private fun jsCopyTextToClipboard(text: String): Boolean = js(
    """(() => {
        function fallbackCopy(val) {
            try {
                var ta = document.createElement('textarea');
                ta.value = val;
                ta.setAttribute('readonly', '');
                ta.style.position = 'fixed';
                ta.style.left = '-9999px';
                ta.style.top = '0';
                document.body.appendChild(ta);
                ta.select();
                ta.setSelectionRange(0, val.length);
                var ok = document.execCommand('copy');
                document.body.removeChild(ta);
                return !!ok;
            } catch (e2) {
                return false;
            }
        }
        try {
            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(text).catch(function() { fallbackCopy(text); });
                return true;
            }
        } catch (e) {}
        return fallbackCopy(text);
    })()"""
)

private fun jsSetDocumentTitle(title: String): Unit = js(
    """{
        try { document.title = title; } catch (e) {}
    }"""
)

private fun jsReadInitialHashRoute(): String = js(
    """(() => {
        try {
            var h = window.location.hash || '';
            return h.startsWith('#') ? h.slice(1) : '';
        } catch (e) {
            return '';
        }
    })()"""
)

private fun jsHideInitialLoader(): Unit = js(
    """{
        try {
            var loader = document.getElementById('loader');
            if (loader) {
                loader.style.opacity = '0';
                setTimeout(function() {
                    try { loader.remove(); } catch (e) {}
                }, 220);
            }
        } catch (e) {}
    }"""
)

private fun jsIsInputFocused(): Boolean = js(
    """(() => {
        try {
            var el = document.activeElement;
            if (!el) return false;
            var tag = (el.tagName || '').toLowerCase();
            return tag === 'input' || tag === 'textarea' || tag === 'select' || !!el.isContentEditable;
        } catch (e) {
            return false;
        }
    })()"""
)

private fun jsGetKeyboardEventKey(ev: Event): String = js("(ev && ev.key) ? String(ev.key) : ''")

private fun jsGetKeyboardEventCode(ev: Event): String = js("(ev && ev.code) ? String(ev.code) : ''")

private fun jsGetKeyboardEventFlags(ev: Event): Int =
    js("ev ? ((ev.ctrlKey ? 1 : 0) | (ev.metaKey ? 2 : 0) | (ev.altKey ? 4 : 0) | (ev.shiftKey ? 8 : 0)) : 0")

private fun jsExportCardPng(fileName: String, jsonPayload: String): Boolean = js(
    """(() => {
        try {
            var data = JSON.parse(jsonPayload);
            var width = 1080;
            var pad = 48;
            var innerPad = 28;
            var contentW = width - pad * 2;
            var cardTextW = contentW - innerPad * 2;

            var canvas = document.createElement('canvas');
            var ctx = canvas.getContext('2d');

            function wrapLines(text, font, maxW) {
                ctx.font = font;
                if (!text) return [];
                var words = String(text).split(/\s+/);
                var lines = [];
                var current = '';
                for (var i = 0; i < words.length; i++) {
                    var test = current ? current + ' ' + words[i] : words[i];
                    if (ctx.measureText(test).width > maxW && current) {
                        lines.push(current);
                        current = words[i];
                    } else {
                        current = test;
                    }
                }
                if (current) lines.push(current);
                return lines;
            }

            var fTitle = '700 40px Inter, -apple-system, BlinkMacSystemFont, sans-serif';
            var fSub = '400 24px Inter, -apple-system, BlinkMacSystemFont, sans-serif';
            var fHead = '700 28px Inter, -apple-system, BlinkMacSystemFont, sans-serif';
            var fBadge = '600 22px Inter, -apple-system, BlinkMacSystemFont, sans-serif';
            var fLine = '400 23px Inter, -apple-system, BlinkMacSystemFont, sans-serif';
            var fFooter = '400 20px Inter, -apple-system, BlinkMacSystemFont, sans-serif';

            var titleLines = wrapLines(data.title, fTitle, contentW - 22);
            var subLines = data.subtitle ? wrapLines(data.subtitle, fSub, contentW - 22) : [];
            var sections = (data.sections || []).map(function(sec) {
                var hLines = wrapLines(sec.heading, fHead, cardTextW);
                var bLines = sec.badge ? wrapLines(sec.badge, fBadge, cardTextW) : [];
                var lBlocks = (sec.lines || []).map(function(l) { return wrapLines(l, fLine, cardTextW); });
                var totalLineCount = lBlocks.reduce(function(acc, arr) { return acc + arr.length; }, 0);
                var h = innerPad * 2 + hLines.length * 36 + (bLines.length ? 8 + bLines.length * 30 : 0) + totalLineCount * 32 + (lBlocks.length ? 8 : 0);
                return { hLines: hLines, bLines: bLines, lBlocks: lBlocks, cardHeight: h };
            });

            var totalH = pad + titleLines.length * 48 + (subLines.length ? 8 + subLines.length * 32 : 0) + 28 +
                sections.reduce(function(acc, s) { return acc + s.cardHeight + 18; }, 0) +
                (data.footer ? 36 : 0) + pad;

            canvas.width = width;
            canvas.height = Math.max(240, totalH);

            ctx.fillStyle = '#F2F4F8';
            ctx.fillRect(0, 0, canvas.width, canvas.height);

            function roundRect(x, y, w, h, r) {
                ctx.beginPath();
                ctx.moveTo(x + r, y);
                ctx.arcTo(x + w, y, x + w, y + h, r);
                ctx.arcTo(x + w, y + h, x, y + h, r);
                ctx.arcTo(x, y + h, x, y, r);
                ctx.arcTo(x, y, x + w, y, r);
                ctx.closePath();
            }

            var y = pad;
            var headerH = titleLines.length * 48 + (subLines.length ? 8 + subLines.length * 32 : 0);
            ctx.fillStyle = '#007AFF';
            roundRect(pad, y + 4, 8, Math.max(36, headerH - 8), 4);
            ctx.fill();

            ctx.textBaseline = 'top';
            ctx.fillStyle = '#0F172A';
            ctx.font = fTitle;
            titleLines.forEach(function(line) {
                ctx.fillText(line, pad + 22, y);
                y += 48;
            });
            if (subLines.length) {
                y += 8;
                ctx.fillStyle = '#475569';
                ctx.font = fSub;
                subLines.forEach(function(line) {
                    ctx.fillText(line, pad + 22, y);
                    y += 32;
                });
            }
            y += 28;

            sections.forEach(function(sec) {
                roundRect(pad, y, contentW, sec.cardHeight, 24);
                ctx.fillStyle = '#FFFFFF';
                ctx.fill();
                ctx.strokeStyle = '#E2E8F0';
                ctx.lineWidth = 2;
                ctx.stroke();

                var cy = y + innerPad;
                ctx.fillStyle = '#0F172A';
                ctx.font = fHead;
                sec.hLines.forEach(function(l) { ctx.fillText(l, pad + innerPad, cy); cy += 36; });
                if (sec.bLines.length) {
                    cy += 8;
                    ctx.fillStyle = '#007AFF';
                    ctx.font = fBadge;
                    sec.bLines.forEach(function(l) { ctx.fillText(l, pad + innerPad, cy); cy += 30; });
                }
                if (sec.lBlocks.length) {
                    cy += 8;
                    ctx.fillStyle = '#334155';
                    ctx.font = fLine;
                    sec.lBlocks.forEach(function(block) {
                        block.forEach(function(l) { ctx.fillText(l, pad + innerPad, cy); cy += 32; });
                    });
                }
                y += sec.cardHeight + 18;
            });

            if (data.footer) {
                y += 12;
                ctx.fillStyle = '#64748B';
                ctx.font = fFooter;
                ctx.fillText(data.footer, pad, y);
            }

            canvas.toBlob(function(blob) {
                if (!blob) return;
                var url = URL.createObjectURL(blob);
                var a = document.createElement('a');
                a.href = url;
                a.download = fileName + '.png';
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                setTimeout(function() { URL.revokeObjectURL(url); }, 2000);
            }, 'image/png');
            return true;
        } catch (e) {
            return false;
        }
    })()"""
)

private fun jsPrintExportCard(jsonPayload: String): Boolean = js(
    """(() => {
        try {
            var data = JSON.parse(jsonPayload);
            var oldRoot = document.getElementById('unicep-print-root');
            if (oldRoot) oldRoot.remove();
            var oldStyle = document.getElementById('unicep-print-style');
            if (oldStyle) oldStyle.remove();

            var style = document.createElement('style');
            style.id = 'unicep-print-style';
            style.textContent =
                '#unicep-print-root { display: none; }' +
                '@media print {' +
                '  body > *:not(#unicep-print-root) { display: none !important; }' +
                '  #unicep-print-root { display: block !important; font-family: Inter, -apple-system, BlinkMacSystemFont, sans-serif; color: #0F172A; padding: 16px; }' +
                '  #unicep-print-root .up-header { border-bottom: 2px solid #E2E8F0; padding-bottom: 12px; margin-bottom: 16px; }' +
                '  #unicep-print-root .up-title { font-size: 22px; font-weight: 700; margin: 0 0 4px 0; }' +
                '  #unicep-print-root .up-sub { font-size: 13px; color: #475569; margin: 0; }' +
                '  #unicep-print-root .up-card { border: 1px solid #CBD5E1; border-radius: 10px; padding: 12px 14px; margin-bottom: 10px; page-break-inside: avoid; }' +
                '  #unicep-print-root .up-card-head { display: flex; justify-content: space-between; align-items: baseline; gap: 12px; margin-bottom: 6px; }' +
                '  #unicep-print-root .up-card-title { font-size: 14px; font-weight: 600; margin: 0; }' +
                '  #unicep-print-root .up-badge { font-size: 12px; font-weight: 600; color: #1D4ED8; background: #EFF6FF; padding: 2px 8px; border-radius: 999px; white-space: nowrap; }' +
                '  #unicep-print-root .up-line { font-size: 12.5px; color: #334155; margin: 3px 0; line-height: 1.4; }' +
                '  #unicep-print-root .up-footer { margin-top: 18px; padding-top: 10px; border-top: 1px solid #E2E8F0; font-size: 11px; color: #64748B; text-align: center; }' +
                '}';
            document.head.appendChild(style);

            var root = document.createElement('div');
            root.id = 'unicep-print-root';

            var header = document.createElement('div');
            header.className = 'up-header';
            var h1 = document.createElement('h1');
            h1.className = 'up-title';
            h1.textContent = data.title || '';
            header.appendChild(h1);
            if (data.subtitle) {
                var sub = document.createElement('p');
                sub.className = 'up-sub';
                sub.textContent = data.subtitle;
                header.appendChild(sub);
            }
            root.appendChild(header);

            (data.sections || []).forEach(function(sec) {
                var card = document.createElement('div');
                card.className = 'up-card';
                var head = document.createElement('div');
                head.className = 'up-card-head';
                var h2 = document.createElement('h2');
                h2.className = 'up-card-title';
                h2.textContent = sec.heading || '';
                head.appendChild(h2);
                if (sec.badge) {
                    var badge = document.createElement('span');
                    badge.className = 'up-badge';
                    badge.textContent = sec.badge;
                    head.appendChild(badge);
                }
                card.appendChild(head);
                (sec.lines || []).forEach(function(line) {
                    var p = document.createElement('div');
                    p.className = 'up-line';
                    p.textContent = line;
                    card.appendChild(p);
                });
                root.appendChild(card);
            });

            if (data.footer) {
                var footer = document.createElement('div');
                footer.className = 'up-footer';
                footer.textContent = data.footer;
                root.appendChild(footer);
            }

            document.body.appendChild(root);

            var cleanup = function() {
                root.remove();
                style.remove();
                window.removeEventListener('afterprint', cleanup);
            };
            window.addEventListener('afterprint', cleanup);
            setTimeout(function() { window.print(); }, 60);
            return true;
        } catch (e) {
            return false;
        }
    })()"""
)

private fun jsCurrentTimeMillis(): Double = js("Date.now()")

private fun jsLocalDayOfWeekIso(): Int = js("(() => { const d = new Date().getDay(); return d === 0 ? 7 : d; })()")

private fun jsLocalHour(): Int = js("new Date().getHours()")

private fun jsLocalMinute(): Int = js("new Date().getMinutes()")

private fun jsLocalYear(): Int = js("new Date().getFullYear()")

private fun jsLocalMonth(): Int = js("new Date().getMonth() + 1")

private fun jsLocalDay(): Int = js("new Date().getDate()")

private fun jsLocalUtcOffsetMinutes(): Int = js("-(new Date().getTimezoneOffset())")

private fun jsIsDocumentVisible(): Boolean = js("document.visibilityState === 'visible'")

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

private fun jsTriggerAnchorDownload(url: String, fileName: String): Boolean = js(
    """(() => {
        try {
            var a = document.createElement('a');
            a.href = url;
            a.download = fileName || 'arquivo';
            a.rel = 'noopener noreferrer';
            a.style.display = 'none';
            document.body.appendChild(a);
            a.click();
            setTimeout(function() {
                try { document.body.removeChild(a); } catch (e) {}
            }, 250);
            return true;
        } catch (e) {
            return false;
        }
    })()"""
)

private fun jsFormatEpochMillisLocal(millis: Double): String = js(
    """(() => {
        try {
            if (!millis || millis <= 0) return '';
            var d = new Date(millis);
            if (isNaN(d.getTime())) return '';
            function p(n) { return n < 10 ? '0' + n : '' + n; }
            return p(d.getDate()) + '/' + p(d.getMonth() + 1) + '/' + d.getFullYear() + ' ' + p(d.getHours()) + ':' + p(d.getMinutes());
        } catch (e) {
            return '';
        }
    })()"""
)

private fun jsDownloadBase64Blob(base64: String, mimeType: String, fileName: String): Boolean = js(
    """(() => {
        try {
            var bin = atob(base64);
            var len = bin.length;
            var bytes = new Uint8Array(len);
            for (var i = 0; i < len; i++) {
                bytes[i] = bin.charCodeAt(i);
            }
            var blob = new Blob([bytes], { type: mimeType || 'application/octet-stream' });
            var url = URL.createObjectURL(blob);
            var a = document.createElement('a');
            a.href = url;
            a.download = fileName || 'arquivo';
            a.rel = 'noopener noreferrer';
            a.style.display = 'none';
            document.body.appendChild(a);
            a.click();
            setTimeout(function() {
                try { URL.revokeObjectURL(url); } catch (e) {}
                try { document.body.removeChild(a); } catch (e) {}
            }, 1000);
            return true;
        } catch (e) {
            return false;
        }
    })()"""
)

private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

private fun encodeBytesToBase64(bytes: ByteArray): String {
    val out = StringBuilder((bytes.size + 2) / 3 * 4)
    var i = 0
    while (i < bytes.size) {
        val b0 = bytes[i].toInt() and 0xFF
        val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
        val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
        out.append(BASE64_ALPHABET[b0 shr 2])
        out.append(BASE64_ALPHABET[((b0 and 0x03) shl 4) or (b1 shr 4)])
        out.append(if (i + 1 < bytes.size) BASE64_ALPHABET[((b1 and 0x0F) shl 2) or (b2 shr 6)] else '=')
        out.append(if (i + 2 < bytes.size) BASE64_ALPHABET[b2 and 0x3F] else '=')
        i += 3
    }
    return out.toString()
}

actual object PlatformBridge {
    private var popListener: ((String) -> Unit)? = null
    private var webShortcutListener: ((WebShortcutAction) -> Boolean)? = null
    private var appResumeListener: (() -> Unit)? = null
    private var historyInitialized = false
    private var rootRoute = "semestre"
    private var lastKnownRoute = ""

    init {
        runCatching {
            document.addEventListener("visibilitychange") {
                if (jsIsDocumentVisible()) {
                    appResumeListener?.invoke()
                }
            }
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
            window.addEventListener("keydown") { ev ->
                val key = jsGetKeyboardEventKey(ev)
                val code = jsGetKeyboardEventCode(ev)
                val flags = jsGetKeyboardEventFlags(ev)
                val hasCtrlOrMeta = (flags and 3) != 0
                val hasAlt = (flags and 4) != 0
                val action = when {
                    key == "Escape" -> WebShortcutAction.ESCAPE
                    hasCtrlOrMeta && !hasAlt && (key.equals("k", ignoreCase = true) || code == "KeyK") ->
                        WebShortcutAction.GLOBAL_SEARCH
                    hasAlt && !hasCtrlOrMeta && !jsIsInputFocused() -> when {
                        key == "1" || code == "Digit1" -> WebShortcutAction.TAB_1
                        key == "2" || code == "Digit2" -> WebShortcutAction.TAB_2
                        key == "3" || code == "Digit3" -> WebShortcutAction.TAB_3
                        key == "4" || code == "Digit4" -> WebShortcutAction.TAB_4
                        key == "5" || code == "Digit5" -> WebShortcutAction.TAB_5
                        else -> null
                    }
                    else -> null
                }
                if (action != null && webShortcutListener?.invoke(action) == true) {
                    ev.preventDefault()
                    ev.stopPropagation()
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

    actual fun openExternalUrl(url: String): Boolean {
        val clean = url.trim()
        if (!clean.startsWith("https://", ignoreCase = true) && !clean.startsWith("http://", ignoreCase = true)) {
            return false
        }
        return runCatching {
            window.open(clean, "_blank", "noopener,noreferrer") != null
        }.getOrDefault(false)
    }

    actual fun copyText(text: String): Boolean {
        return jsCopyTextToClipboard(text)
    }

    actual fun shareText(title: String, text: String): Boolean {
        return jsCopyTextToClipboard(text)
    }

    actual fun exportPng(fileName: String, data: ExportCardData): Boolean {
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "unicep_export" }
        return jsExportCardPng(safeName, encodeExportJson(data))
    }

    actual fun printDocument(title: String, data: ExportCardData): Boolean {
        return jsPrintExportCard(encodeExportJson(data))
    }

    actual fun scheduleGradeSync() {}

    actual fun cancelGradeSync() {}

    actual fun syncCalendarReminders(events: List<UserCalendarEvent>) {}

    actual fun cancelAllCalendarReminders() {}

    actual fun requestNotificationPermissionIfNeeded() {
        jsRequestWebNotificationPermission()
    }

    actual fun postSystemNotification(id: Int, title: String, body: String, openTab: Int): Boolean {
        return jsShowWebNotification(title, body)
    }

    actual fun supportsBackgroundBatterySettings(): Boolean = false

    actual fun isIgnoringBatteryOptimizations(): Boolean = true

    actual fun requestIgnoreBatteryOptimizations(): Boolean = false

    actual fun openAppBackgroundSettings(): Boolean = false

    actual fun setAppResumeListener(listener: (() -> Unit)?) {
        appResumeListener = listener
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

    actual fun readInitialNavigationRoute(): String? =
        jsReadInitialHashRoute().trim().takeIf { it.isNotBlank() && it != "guard" }

    actual fun setPopRouteListener(listener: (String) -> Unit) {
        popListener = listener
    }

    actual fun setWebKeyShortcutListener(listener: ((WebShortcutAction) -> Boolean)?) {
        webShortcutListener = listener
    }

    actual fun setPageTitle(title: String) {
        jsSetDocumentTitle(title)
    }

    actual fun notifyFirstFrameRendered() {
        jsHideInitialLoader()
    }

    actual fun setOpenTabListener(listener: ((Int) -> Unit)?) {}

    actual fun dispatchOpenTab(tab: Int) {}

    actual fun setAndroidBackHandler(handler: (() -> Boolean)?) {}

    actual fun setAndroidBackEnabled(enabled: Boolean) {}

    actual fun setAndroidPredictiveBackListener(listener: ((PredictiveBackState) -> Unit)?) {}

    actual fun performHaptic(type: PortalHapticType, intensity: Float) {}

    actual fun currentAppVersion(): String = "1.12.1"

    actual suspend fun checkForAppUpdate(): AppUpdateInfo? = null

    actual suspend fun downloadAndInstallAppUpdate(apkUrl: String, onProgress: (Float) -> Unit): Result<Unit> =
        Result.failure(UnsupportedOperationException("Atualização automática disponível apenas no Android."))

    actual fun createHttpEngine(): HttpClientEngine = Js.create()

    actual fun currentTimeMillis(): Long = jsCurrentTimeMillis().toLong()

    actual fun currentLocalDateTime(): LocalDateTimeInfo = LocalDateTimeInfo(
        dayOfWeekIso = jsLocalDayOfWeekIso(),
        hour = jsLocalHour(),
        minute = jsLocalMinute(),
        year = jsLocalYear(),
        month = jsLocalMonth(),
        day = jsLocalDay(),
        utcOffsetMinutes = jsLocalUtcOffsetMinutes()
    )

    actual fun formatEpochMillisLocal(millis: Long): String {
        if (millis <= 0L) return ""
        return jsFormatEpochMillisLocal(millis.toDouble())
    }

    actual fun isReduceMotionEnabled(): Boolean = jsPrefersReducedMotion()

    actual fun supportsInAppDocumentViewer(): Boolean = false

    actual fun supportsCustomDownloadFolder(): Boolean = false

    actual fun isUsingCustomDownloadFolder(): Boolean = false

    actual fun getDownloadFolderLabel(): String = "Pasta Downloads do navegador"

    actual suspend fun pickDownloadFolder(): String? = null

    actual fun clearDownloadFolder() {}

    actual suspend fun saveDownloadedFile(
        courseName: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Result<String> {
        val safeCourse = courseName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(48)
        val safeFile = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "arquivo" }
        val finalName = if (safeCourse.isNotBlank()) "$safeCourse - $safeFile" else safeFile
        val ok = jsDownloadBase64Blob(
            base64 = encodeBytesToBase64(bytes),
            mimeType = mimeType.ifBlank { "application/octet-stream" },
            fileName = finalName
        )
        return if (ok) {
            Result.success("Downloads do navegador ($finalName)")
        } else {
            Result.failure(IllegalStateException("Não foi possível iniciar o download no navegador."))
        }
    }

    actual fun triggerBrowserDownload(url: String, fileName: String) {
        val clean = url.trim()
        if (!clean.startsWith("https://", ignoreCase = true) && !clean.startsWith("http://", ignoreCase = true)) {
            return
        }
        val safeFile = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "arquivo" }
        if (!jsTriggerAnchorDownload(clean, safeFile)) {
            openExternalUrl(clean)
        }
    }
}

@Composable
actual fun PlatformVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier
) {
    val colors = LocalPortalColors.current
    val base = defaultScrollbarStyle()
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(listState),
        modifier = modifier,
        style = base.copy(
            thickness = 6.dp,
            shape = CircleShape,
            unhoverColor = colors.textSecondary.copy(alpha = 0.28f),
            hoverColor = colors.textSecondary.copy(alpha = 0.58f),
            hoverDurationMillis = 200
        )
    )
}

@Composable
actual fun PlatformVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier
) {
    val colors = LocalPortalColors.current
    val base = defaultScrollbarStyle()
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = modifier,
        style = base.copy(
            thickness = 6.dp,
            shape = CircleShape,
            unhoverColor = colors.textSecondary.copy(alpha = 0.28f),
            hoverColor = colors.textSecondary.copy(alpha = 0.58f),
            hoverDurationMillis = 200
        )
    )
}

@Composable
actual fun PlatformDocumentViewer(
    title: String,
    courseName: String,
    fileName: String,
    mimeType: String,
    bytes: ByteArray?,
    fallbackUrl: String,
    downloading: Boolean,
    onDownload: () -> Unit,
    onClose: () -> Unit
) {
}
