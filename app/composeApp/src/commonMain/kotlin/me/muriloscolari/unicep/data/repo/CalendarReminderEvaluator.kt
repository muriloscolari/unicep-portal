package me.muriloscolari.unicep.data.repo

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.muriloscolari.unicep.data.api.UserCalendarEvent
import me.muriloscolari.unicep.platform.AppTabIndex
import me.muriloscolari.unicep.platform.PlatformBridge

private val calendarReminderJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

data class ScheduledCalendarReminder(
    val eventId: Long,
    val reminderKey: String,
    val notifId: Int,
    val triggerAtMillis: Long,
    val title: String,
    val body: String
)

fun resolveCalendarEventStartMillis(
    event: UserCalendarEvent,
    utcOffsetMinutes: Int = -180
): Long {
    if (event.startAtMillis > 0L) return event.startAtMillis
    val date = parseIsoDate(event.date) ?: return 0L
    val hm = if (!event.allDay) parseHourMinute(event.time) else null
    val hour = hm?.first ?: 8
    val minute = hm?.second ?: 0
    return localDateTimeToEpochMillis(date, hour, minute, utcOffsetMinutes)
}

fun buildCalendarReminderNotificationText(
    event: UserCalendarEvent,
    minutesBefore: Int
): Pair<String, String> {
    val title = when {
        minutesBefore <= 0 -> "Agora: ${event.title}"
        minutesBefore < 60 -> "Em $minutesBefore min: ${event.title}"
        minutesBefore == 60 -> "Em 1 hora: ${event.title}"
        minutesBefore < 1440 -> "Em ${minutesBefore / 60}h: ${event.title}"
        minutesBefore == 1440 -> "Amanhã: ${event.title}"
        minutesBefore == 10080 -> "Em 1 semana: ${event.title}"
        minutesBefore % 1440 == 0 -> "Em ${minutesBefore / 1440} dias: ${event.title}"
        else -> "Em $minutesBefore min: ${event.title}"
    }
    val timeLabel = if (event.allDay) "Dia inteiro" else event.time.ifBlank { "" }
    val body = listOfNotNull(
        event.disciplineName.takeIf { it.isNotBlank() },
        timeLabel.takeIf { it.isNotBlank() },
        event.description.takeIf { it.isNotBlank() }
    ).joinToString(" · ").ifBlank { "Lembrete do seu calendário acadêmico" }
    return title to body
}

fun computeUpcomingReminderTriggers(
    events: List<UserCalendarEvent>,
    nowMillis: Long,
    horizonMillis: Long = 30L * 86_400_000L,
    utcOffsetMinutes: Int = -180
): List<ScheduledCalendarReminder> {
    val maxTrigger = nowMillis + horizonMillis
    val out = mutableListOf<ScheduledCalendarReminder>()
    events.forEach { ev ->
        if (ev.id <= 0L || ev.reminderMinutes.isEmpty()) return@forEach
        val startMs = resolveCalendarEventStartMillis(ev, utcOffsetMinutes)
        if (startMs <= 0L) return@forEach

        ev.reminderMinutes.distinct().sorted().forEach { mins ->
            if (mins !in 0..20_160) return@forEach
            val triggerMs = startMs - mins.toLong() * 60_000L
            if (triggerMs > nowMillis && triggerMs <= maxTrigger) {
                val key = "${ev.id}:$startMs:$mins"
                val notifId = 90_000 + ((key.hashCode() and 0x7FFF) % 9_000)
                val (title, body) = buildCalendarReminderNotificationText(ev, mins)
                out += ScheduledCalendarReminder(
                    eventId = ev.id,
                    reminderKey = key,
                    notifId = notifId,
                    triggerAtMillis = triggerMs,
                    title = title,
                    body = body
                )
            }
        }
    }
    return out.sortedBy { it.triggerAtMillis }
}

fun evaluateCalendarReminders(
    events: List<UserCalendarEvent>,
    nowMillis: Long = PlatformBridge.currentTimeMillis(),
    utcOffsetMinutes: Int = -180,
    storageGet: (String) -> String? = PlatformBridge::getString,
    storagePut: (String, String) -> Unit = PlatformBridge::putString,
    onNotify: (id: Int, title: String, body: String, openTab: Int) -> Boolean = PlatformBridge::postSystemNotification
) {
    val prevMap: MutableMap<String, Boolean> = storageGet(PrefKeys.CALENDAR_REMINDERS_NOTIFIED)
        ?.let { raw -> runCatching { calendarReminderJson.decodeFromString<Map<String, Boolean>>(raw) }.getOrNull() }
        ?.toMutableMap()
        ?: mutableMapOf()

    val validKeys = mutableSetOf<String>()
    val dueReminders = mutableListOf<ScheduledCalendarReminder>()
    val staleGraceMs = 24L * 3_600_000L
    val futureToleranceMs = 60_000L

    events.forEach { ev ->
        if (ev.id <= 0L || ev.reminderMinutes.isEmpty()) return@forEach
        val startMs = resolveCalendarEventStartMillis(ev, utcOffsetMinutes)
        if (startMs <= 0L) return@forEach

        ev.reminderMinutes.distinct().sorted().forEach { mins ->
            if (mins !in 0..20_160) return@forEach
            val triggerMs = startMs - mins.toLong() * 60_000L
            val key = "${ev.id}:$startMs:$mins"
            if (triggerMs >= nowMillis - staleGraceMs) {
                validKeys += key
            }
            if (triggerMs in (nowMillis - staleGraceMs)..(nowMillis + futureToleranceMs) && prevMap[key] != true) {
                val notifId = 90_000 + ((key.hashCode() and 0x7FFF) % 9_000)
                val (title, body) = buildCalendarReminderNotificationText(ev, mins)
                dueReminders += ScheduledCalendarReminder(
                    eventId = ev.id,
                    reminderKey = key,
                    notifId = notifId,
                    triggerAtMillis = triggerMs,
                    title = title,
                    body = body
                )
            }
        }
    }

    var changed = prevMap.keys.removeAll { it !in validKeys }
    dueReminders.sortedBy { it.triggerAtMillis }.forEach { rem ->
        val posted = onNotify(rem.notifId, rem.title, rem.body, AppTabIndex.CALENDAR)
        if (posted) {
            prevMap[rem.reminderKey] = true
            changed = true
        }
    }

    if (changed) {
        storagePut(PrefKeys.CALENDAR_REMINDERS_NOTIFIED, calendarReminderJson.encodeToString(prevMap))
    }
}
