package me.muriloscolari.unicep.data.repo

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.muriloscolari.unicep.data.api.VirtualAssignmentItem
import me.muriloscolari.unicep.platform.PlatformBridge

private val virtualAlertJson = Json { ignoreUnknownKeys = true }

val DEFAULT_VIRTUAL_DEADLINE_LEAD_HOURS = listOf(24, 72)
val PRESET_VIRTUAL_DEADLINE_HOURS = listOf(6, 12, 24, 48, 72, 120, 168)

fun formatLeadHoursLabel(hours: Int): String = when {
    hours < 24 -> "${hours}h"
    hours == 24 -> "24h (1 dia)"
    hours % 24 == 0 -> "${hours / 24} dias"
    else -> "${hours}h"
}

fun readVirtualNotifyNewAssignment(): Boolean =
    PlatformBridge.getString(PrefKeys.VIRTUAL_NOTIFY_NEW_ASSIGNMENT)
        ?.toBooleanStrictOrNull()
        ?: true

fun saveVirtualNotifyNewAssignment(enabled: Boolean) {
    PlatformBridge.putString(PrefKeys.VIRTUAL_NOTIFY_NEW_ASSIGNMENT, enabled.toString())
}

fun readVirtualDeadlineLeadHours(): List<Int> {
    val raw = PlatformBridge.getString(PrefKeys.VIRTUAL_DEADLINE_LEAD_HOURS) ?: return DEFAULT_VIRTUAL_DEADLINE_LEAD_HOURS
    return runCatching {
        virtualAlertJson.decodeFromString<List<Int>>(raw)
            .mapNotNull { h -> h.takeIf { it in 1..336 } }
            .distinct()
            .sorted()
    }.getOrElse { DEFAULT_VIRTUAL_DEADLINE_LEAD_HOURS }
}

fun saveVirtualDeadlineLeadHours(hoursList: List<Int>): List<Int> {
    val clean = hoursList.mapNotNull { h -> h.takeIf { it in 1..336 } }.distinct().sorted()
    PlatformBridge.putString(PrefKeys.VIRTUAL_DEADLINE_LEAD_HOURS, virtualAlertJson.encodeToString(clean))
    return clean
}

fun resolveAssignmentDueAtMillis(item: VirtualAssignmentItem): Long {
    if (item.dueAtMillis > 0L) return item.dueAtMillis
    return parseIso8601ToEpochMillis(item.dueDate) ?: 0L
}

fun evaluateVirtualDeadlineAlerts(
    assignments: List<VirtualAssignmentItem>,
    leadHoursList: List<Int> = readVirtualDeadlineLeadHours(),
    nowMillis: Long = PlatformBridge.currentTimeMillis()
) {
    if (leadHoursList.isEmpty() || assignments.isEmpty()) return
    val sortedLeads = leadHoursList.filter { it in 1..336 }.distinct().sorted()
    if (sortedLeads.isEmpty()) return

    val prevMap: MutableMap<String, Boolean> = PlatformBridge.getString(PrefKeys.VIRTUAL_DEADLINE_NOTIFIED)
        ?.let { raw -> runCatching { virtualAlertJson.decodeFromString<Map<String, Boolean>>(raw) }.getOrNull() }
        ?.toMutableMap()
        ?: mutableMapOf()

    // Prune submitted / expired items so storage stays small.
    val activeAssignmentPrefixes = assignments
        .asSequence()
        .filter { it.status == "not_submitted" }
        .map { "${it.orgUnitId}:${it.id}:" }
        .toSet()

    var changed = prevMap.keys.removeAll { key -> activeAssignmentPrefixes.none { prefix -> key.startsWith(prefix) } }

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
        val notifId = 80_000 + (bucketKey.hashCode() and 0x7FFF)
        val posted = PlatformBridge.postSystemNotification(
            id = notifId,
            title = "Prazo próximo ($remainingLabel): ${assignment.name}",
            body = "${assignment.orgUnitName.ifBlank { "Sala Virtual" }} • Atividade ainda não enviada.",
            openTab = 2
        )
        if (posted) {
            matchedBuckets.forEach { h -> prevMap["$keyPrefix$h"] = true }
            changed = true
        }
    }

    if (changed) {
        PlatformBridge.putString(PrefKeys.VIRTUAL_DEADLINE_NOTIFIED, virtualAlertJson.encodeToString(prevMap))
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

private fun parseIso8601ToEpochMillis(raw: String): Long? {
    val s = raw.trim()
    if (s.length < 19 || s[4] != '-' || s[7] != '-' || (s[10] != 'T' && s[10] != ' ')) return null
    val year = s.substring(0, 4).toIntOrNull() ?: return null
    val month = s.substring(5, 7).toIntOrNull() ?: return null
    val day = s.substring(8, 10).toIntOrNull() ?: return null
    val hour = s.substring(11, 13).toIntOrNull() ?: return null
    val minute = s.substring(14, 16).toIntOrNull() ?: return null
    val second = s.substring(17, 19).toIntOrNull() ?: return null
    if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null

    var idx = 19
    if (idx < s.length && s[idx] == '.') {
        idx++
        while (idx < s.length && s[idx].isDigit()) idx++
    }
    var offsetMinutes = 0
    if (idx < s.length) {
        when (s[idx]) {
            'Z', 'z' -> offsetMinutes = 0
            '+', '-' -> {
                val sign = if (s[idx] == '-') -1 else 1
                val tail = s.substring(idx + 1).replace(":", "")
                val offH = tail.take(2).toIntOrNull() ?: return null
                val offM = tail.drop(2).take(2).toIntOrNull() ?: 0
                offsetMinutes = sign * (offH * 60 + offM)
            }
        }
    }

    val daysSinceEpoch = daysFromCivil(year, month, day)
    val localSeconds = daysSinceEpoch * 86_400L + hour * 3_600L + minute * 60L + second
    val utcSeconds = localSeconds - offsetMinutes * 60L
    return utcSeconds * 1000L
}

// Howard Hinnant's civil-from-days inverse for UTC epoch days (1970-01-01 = 0).
private fun daysFromCivil(yInput: Int, mInput: Int, d: Int): Long {
    val y = (if (mInput <= 2) yInput - 1 else yInput).toLong()
    val era = if (y >= 0) y / 400 else (y - 399) / 400
    val yoe = y - era * 400
    val m = mInput.toLong()
    val doy = (153L * (m + if (m > 2) -3L else 9L) + 2L) / 5L + d.toLong() - 1L
    val doe = yoe * 365L + yoe / 4L - yoe / 100L + doy
    return era * 146_097L + doe - 719_468L
}
