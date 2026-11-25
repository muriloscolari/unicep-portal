package me.muriloscolari.unicep.data.repo

import me.muriloscolari.unicep.platform.PlatformBridge

const val DEFAULT_BG_SYNC_INTERVAL_MINUTES = 60
const val BG_FULL_SYNC_INTERVAL_MS = 3L * 60L * 60_000L
const val MAX_SCHEDULED_CALENDAR_REMINDERS = 30

val PRESET_BG_SYNC_INTERVAL_MINUTES: List<Int> = listOf(0, 30, 60, 120)

fun normalizeBackgroundSyncIntervalMinutes(minutes: Int): Int = when {
    minutes <= 0 -> 0
    minutes <= 30 -> 30
    minutes <= 60 -> 60
    else -> 120
}

fun readBackgroundSyncIntervalMinutes(
    storageGet: (String) -> String? = PlatformBridge::getString
): Int {
    val raw = storageGet(PrefKeys.BG_SYNC_INTERVAL_MINUTES)?.toIntOrNull()
        ?: return DEFAULT_BG_SYNC_INTERVAL_MINUTES
    return normalizeBackgroundSyncIntervalMinutes(raw)
}

fun saveBackgroundSyncIntervalMinutes(
    minutes: Int,
    storagePut: (String, String) -> Unit = PlatformBridge::putString
): Int {
    val clean = normalizeBackgroundSyncIntervalMinutes(minutes)
    storagePut(PrefKeys.BG_SYNC_INTERVAL_MINUTES, clean.toString())
    return clean
}

/**
 * Flex window passed to WorkManager PeriodicWorkRequest so Android JobScheduler can
 * coalesce syncs into Doze maintenance windows instead of waking the radio independently.
 */
fun computeFlexIntervalMinutes(intervalMinutes: Int): Long {
    val clean = normalizeBackgroundSyncIntervalMinutes(intervalMinutes)
    if (clean <= 0) return 0L
    return (clean / 4).coerceIn(10, 30).toLong()
}

/**
 * Heavy multi-endpoint full sync (dashboard, D2L overview, calendar) is skipped overnight
 * to avoid unnecessary radio/CPU work during standby sleep.
 */
fun isQuietHoursForFullSync(hour: Int): Boolean = hour in 0..6

fun formatBackgroundSyncIntervalLabel(minutes: Int): String = when (normalizeBackgroundSyncIntervalMinutes(minutes)) {
    0 -> "Manual"
    30 -> "30 min"
    60 -> "1 hora"
    120 -> "2 horas"
    else -> "${minutes} min"
}

fun buildCalendarTriggerSignature(triggers: List<ScheduledCalendarReminder>): String {
    if (triggers.isEmpty()) return ""
    return triggers.joinToString("|") { "${it.reminderKey}@${it.triggerAtMillis}:${it.notifId}" }
}
