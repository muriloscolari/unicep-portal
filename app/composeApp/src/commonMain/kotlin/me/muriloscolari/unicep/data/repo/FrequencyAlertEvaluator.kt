package me.muriloscolari.unicep.data.repo

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.muriloscolari.unicep.data.api.ReportCardItem
import me.muriloscolari.unicep.platform.AppTabIndex
import me.muriloscolari.unicep.platform.PlatformBridge
import kotlin.math.roundToInt

private val alertJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

fun readFrequencyAlertThreshold(
    storageGet: (String) -> String? = PlatformBridge::getString
): Int {
    val raw = storageGet(PrefKeys.FREQ_ALERT_THRESHOLD)?.toIntOrNull()
    return raw ?: 80
}

fun saveFrequencyAlertThreshold(
    threshold: Int,
    storagePut: (String, String) -> Unit = PlatformBridge::putString
) {
    val clean = if (threshold <= 0) 0 else threshold.coerceIn(75, 95)
    storagePut(PrefKeys.FREQ_ALERT_THRESHOLD, clean.toString())
}

fun computeReportFreqPercent(item: ReportCardItem): Double? {
    item.freqPercent?.let { return it.coerceIn(0.0, 100.0) }
    if (item.hours <= 0) return null
    val attended = (item.hours - item.absences).coerceAtLeast(0)
    return ((attended.toDouble() / item.hours.toDouble()) * 100.0).coerceIn(0.0, 100.0)
}

fun evaluateFrequencyAlerts(
    items: List<ReportCardItem>,
    thresholdPercent: Int = readFrequencyAlertThreshold(),
    storageGet: (String) -> String? = PlatformBridge::getString,
    storagePut: (String, String) -> Unit = PlatformBridge::putString,
    onNotify: (id: Int, title: String, body: String, openTab: Int) -> Boolean = PlatformBridge::postSystemNotification
) {
    if (thresholdPercent <= 0 || items.isEmpty()) return

    val prevMap: MutableMap<String, Int> = storageGet(PrefKeys.FREQ_ALERT_NOTIFIED)
        ?.let { raw -> runCatching { alertJson.decodeFromString<Map<String, Int>>(raw) }.getOrNull() }
        ?.toMutableMap()
        ?: mutableMapOf()

    var changed = false
    items.forEach { item ->
        val freq = computeReportFreqPercent(item)?.roundToInt() ?: return@forEach
        val key = item.code.ifBlank { item.name }
        if (freq <= thresholdPercent) {
            val prevFreq = prevMap[key]
            if (prevFreq == null || freq < prevFreq) {
                val rem = item.remainingAbsences
                val remText = when {
                    rem < 0 -> "excedeu ${-rem} falta(s)"
                    rem == 0 -> "sem margem de faltas"
                    rem == 1 -> "resta 1 falta"
                    else -> "restam $rem faltas"
                }
                val notifId = 70_000 + (key.hashCode() and 0x7FFF)
                val posted = onNotify(
                    notifId,
                    "Frequência em $freq%: ${item.name}",
                    "Atingiu seu limite de $thresholdPercent% ($remText).",
                    AppTabIndex.SEMESTER
                )
                if (posted) {
                    prevMap[key] = freq
                    changed = true
                }
            }
        } else if (prevMap.remove(key) != null) {
            changed = true
        }
    }

    if (changed) {
        storagePut(PrefKeys.FREQ_ALERT_NOTIFIED, alertJson.encodeToString(prevMap))
    }
}
