package me.muriloscolari.unicep.data.repo

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.muriloscolari.unicep.data.api.VirtualAssignmentItem
import me.muriloscolari.unicep.platform.AppTabIndex
import me.muriloscolari.unicep.platform.PlatformBridge

private val virtualAlertJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

val DEFAULT_VIRTUAL_DEADLINE_LEAD_HOURS = listOf(24, 72)
val PRESET_VIRTUAL_DEADLINE_HOURS = listOf(6, 12, 24, 48, 72, 120, 168)

fun formatLeadHoursLabel(hours: Int): String = when {
    hours < 24 -> "${hours}h"
    hours == 24 -> "24h (1 dia)"
    hours % 24 == 0 -> "${hours / 24} dias"
    else -> "${hours}h"
}

fun readVirtualNotifyNewAssignment(
    storageGet: (String) -> String? = PlatformBridge::getString
): Boolean =
    storageGet(PrefKeys.VIRTUAL_NOTIFY_NEW_ASSIGNMENT)
        ?.toBooleanStrictOrNull()
        ?: true

fun saveVirtualNotifyNewAssignment(
    enabled: Boolean,
    storagePut: (String, String) -> Unit = PlatformBridge::putString
) {
    storagePut(PrefKeys.VIRTUAL_NOTIFY_NEW_ASSIGNMENT, enabled.toString())
}

fun readVirtualDeadlineLeadHours(
    storageGet: (String) -> String? = PlatformBridge::getString
): List<Int> {
    val raw = storageGet(PrefKeys.VIRTUAL_DEADLINE_LEAD_HOURS) ?: return DEFAULT_VIRTUAL_DEADLINE_LEAD_HOURS
    return runCatching {
        virtualAlertJson.decodeFromString<List<Int>>(raw)
            .mapNotNull { h -> h.takeIf { it in 1..336 } }
            .distinct()
            .sorted()
    }.getOrElse { DEFAULT_VIRTUAL_DEADLINE_LEAD_HOURS }
}

fun saveVirtualDeadlineLeadHours(
    hoursList: List<Int>,
    storagePut: (String, String) -> Unit = PlatformBridge::putString
): List<Int> {
    val clean = hoursList.mapNotNull { h -> h.takeIf { it in 1..336 } }.distinct().sorted()
    storagePut(PrefKeys.VIRTUAL_DEADLINE_LEAD_HOURS, virtualAlertJson.encodeToString(clean))
    return clean
}

fun resolveAssignmentDueAtMillis(item: VirtualAssignmentItem): Long {
    if (item.dueAtMillis > 0L) return item.dueAtMillis
    return parseIso8601ToEpochMillis(item.dueDate) ?: 0L
}

fun evaluateVirtualDeadlineAlerts(
    assignments: List<VirtualAssignmentItem>,
    leadHoursList: List<Int> = readVirtualDeadlineLeadHours(),
    nowMillis: Long = PlatformBridge.currentTimeMillis(),
    storageGet: (String) -> String? = PlatformBridge::getString,
    storagePut: (String, String) -> Unit = PlatformBridge::putString,
    onNotify: (id: Int, title: String, body: String, openTab: Int) -> Boolean = PlatformBridge::postSystemNotification
) {
    if (leadHoursList.isEmpty() || assignments.isEmpty()) return
    val sortedLeads = leadHoursList.filter { it in 1..336 }.distinct().sorted()
    if (sortedLeads.isEmpty()) return

    val prevMap: MutableMap<String, Boolean> = storageGet(PrefKeys.VIRTUAL_DEADLINE_NOTIFIED)
        ?.let { raw -> runCatching { virtualAlertJson.decodeFromString<Map<String, Boolean>>(raw) }.getOrNull() }
        ?.toMutableMap()
        ?: mutableMapOf()
    val prevIds: MutableMap<String, Int> = storageGet(PrefKeys.VIRTUAL_DEADLINE_NOTIF_IDS)
        ?.let { raw -> runCatching { virtualAlertJson.decodeFromString<Map<String, Int>>(raw) }.getOrNull() }
        ?.toMutableMap()
        ?: mutableMapOf()

    // Prune submitted / expired items so storage stays small.
    val activeAssignmentPrefixes = assignments
        .asSequence()
        .filter { it.status == "not_submitted" }
        .map { "${it.orgUnitId}:${it.id}:" }
        .toSet()

    var changed = prevMap.keys.removeAll { key -> activeAssignmentPrefixes.none { prefix -> key.startsWith(prefix) } }
    var idsChanged = prevIds.keys.removeAll { key -> activeAssignmentPrefixes.none { prefix -> key.startsWith(prefix) } }

    assignments.forEach { assignment ->
        if (assignment.status != "not_submitted") return@forEach
        val dueMs = resolveAssignmentDueAtMillis(assignment)
        if (dueMs <= nowMillis) return@forEach

        val remainingMs = dueMs - nowMillis
        val matchedBuckets = sortedLeads.filter { hours -> remainingMs <= hours.toLong() * 3_600_000L }
        if (matchedBuckets.isEmpty()) return@forEach

        val tightestHours = matchedBuckets.first()
        val keyPrefix = "${assignment.orgUnitId}:${assignment.id}:"
        val bucketKey = "$keyPrefix$tightestHours"
        if (prevMap[bucketKey] == true) return@forEach

        val remainingLabel = formatRemainingDeadline(remainingMs)
        val notifId = prevIds[bucketKey] ?: run {
            val used = prevIds.values.toSet()
            var candidate = 80_000
            while (used.contains(candidate) && candidate < 100_000) {
                candidate++
            }
            if (candidate >= 100_000) {
                candidate = 80_000 + (((prevIds.size) % 20_000))
            }
            prevIds[bucketKey] = candidate
            idsChanged = true
            candidate
        }
        val posted = onNotify(
            notifId,
            "Prazo próximo ($remainingLabel): ${assignment.name}",
            "${assignment.orgUnitName.ifBlank { "Sala Virtual" }} • Atividade ainda não enviada.",
            AppTabIndex.VIRTUAL
        )
        if (posted) {
            matchedBuckets.forEach { h -> prevMap["$keyPrefix$h"] = true }
            changed = true
        }
    }

    if (changed) {
        storagePut(PrefKeys.VIRTUAL_DEADLINE_NOTIFIED, virtualAlertJson.encodeToString(prevMap))
    }
    if (idsChanged) {
        storagePut(PrefKeys.VIRTUAL_DEADLINE_NOTIF_IDS, virtualAlertJson.encodeToString(prevIds))
    }
}

private fun formatRemainingDeadline(remainingMs: Long): String {
    val totalMinutes = (remainingMs / 60_000L).coerceAtLeast(1L)
    val totalHours = totalMinutes / 60L
    val days = totalHours / 24L
    return when {
        days >= 2L -> "$days dias"
        totalHours >= 24L -> {
            val remHours = totalHours % 24L
            if (remHours > 0L) "1 dia e ${remHours}h" else "1 dia"
        }
        totalHours >= 1L -> "${totalHours}h"
        else -> "${totalMinutes} min"
    }
}

private fun maxDayOfMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> {
        val isLeap = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
        if (isLeap) 29 else 28
    }
    else -> 0
}

fun parseIso8601ToEpochMillis(raw: String): Long? {
    val s = raw.trim()
    if (s.length < 19 || s[4] != '-' || s[7] != '-' || (s[10] != 'T' && s[10] != ' ')) return null
    val year = s.substring(0, 4).toIntOrNull() ?: return null
    val month = s.substring(5, 7).toIntOrNull() ?: return null
    val day = s.substring(8, 10).toIntOrNull() ?: return null
    val hour = s.substring(11, 13).toIntOrNull() ?: return null
    val minute = s.substring(14, 16).toIntOrNull() ?: return null
    val second = s.substring(17, 19).toIntOrNull() ?: return null
    if (year !in 1970..2100 || month !in 1..12) return null
    val maxDay = maxDayOfMonth(year, month)
    if (day !in 1..maxDay || hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null

    var idx = 19
    if (idx < s.length && s[idx] == '.') {
        idx++
        while (idx < s.length && s[idx].isDigit()) idx++
    }
    var offsetMinutes = 0
    if (idx < s.length) {
        when (s[idx]) {
            'Z', 'z' -> {
                if (idx != s.length - 1) return null
                offsetMinutes = 0
            }
            '+', '-' -> {
                val sign = if (s[idx] == '-') -1 else 1
                val tail = s.substring(idx + 1).replace(":", "")
                if (tail.length != 2 && tail.length != 4) return null
                val offH = tail.substring(0, 2).toIntOrNull() ?: return null
                val offM = if (tail.length == 4) (tail.substring(2, 4).toIntOrNull() ?: return null) else 0
                if (offH !in 0..23 || offM !in 0..59) return null
                offsetMinutes = sign * (offH * 60 + offM)
            }
            else -> return null
        }
    }

    val daysSinceEpoch = daysFromCivil(year, month, day)
    val localSeconds = daysSinceEpoch * 86_400L + hour * 3_600L + minute * 60L + second
    val utcSeconds = localSeconds - offsetMinutes * 60L
    return utcSeconds * 1000L
}
