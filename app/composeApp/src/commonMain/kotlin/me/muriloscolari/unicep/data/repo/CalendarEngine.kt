package me.muriloscolari.unicep.data.repo

import androidx.compose.runtime.Immutable
import me.muriloscolari.unicep.data.api.UserCalendarEvent
import me.muriloscolari.unicep.data.api.VirtualAssignmentItem
import me.muriloscolari.unicep.data.api.VirtualCalendarEvent
import me.muriloscolari.unicep.data.api.VirtualCourseDetailResponse
import me.muriloscolari.unicep.data.api.VirtualOverviewResponse
import me.muriloscolari.unicep.data.api.VirtualQuizItem
import me.muriloscolari.unicep.ui.util.toDisplayTitle

val PRESET_CALENDAR_REMINDER_MINUTES = listOf(0, 15, 30, 60, 120, 1440, 2880, 10080)

fun formatCalendarReminderLabel(minutes: Int): String = when {
    minutes <= 0 -> "Na hora"
    minutes < 60 -> "$minutes min antes"
    minutes == 60 -> "1 hora antes"
    minutes < 1440 -> "${minutes / 60}h antes"
    minutes == 1440 -> "1 dia antes"
    minutes % 1440 == 0 && minutes / 1440 == 7 -> "1 semana antes"
    minutes % 1440 == 0 -> "${minutes / 1440} dias antes"
    else -> "${minutes} min antes"
}

fun formatCalendarReminderChipLabel(minutes: Int): String = when {
    minutes <= 0 -> "Na hora"
    minutes < 60 -> "${minutes}m"
    minutes < 1440 -> "${minutes / 60}h"
    minutes == 1440 -> "1 dia"
    minutes == 10080 -> "1 sem"
    minutes % 1440 == 0 -> "${minutes / 1440} dias"
    else -> "${minutes}m"
}

@Immutable
data class CalendarDate(
    val year: Int,
    val month: Int,
    val day: Int
) : Comparable<CalendarDate> {
    override fun compareTo(other: CalendarDate): Int {
        if (year != other.year) return year.compareTo(other.year)
        if (month != other.month) return month.compareTo(other.month)
        return day.compareTo(other.day)
    }

    fun toIsoString(): String = formatIsoDate(this)
    fun toBrString(): String = formatBrDate(this)
}

enum class CalendarItemSource {
    PERSONAL,
    VIRTUAL_ASSIGNMENT,
    VIRTUAL_QUIZ,
    VIRTUAL_EVENT
}

enum class CalendarFilter(val label: String) {
    ALL("Tudo"),
    VIRTUAL("Prazos Virtual"),
    PERSONAL("Meus eventos")
}

@Immutable
data class CalendarAgendaItem(
    val id: String,
    val source: CalendarItemSource,
    val date: CalendarDate,
    val sortMillis: Long,
    val timeLabel: String,
    val title: String,
    val subtitle: String = "",
    val description: String = "",
    val colorKey: String = "accent",
    val statusBadge: String = "",
    val allDay: Boolean = false,
    val userEvent: UserCalendarEvent? = null,
    val assignment: VirtualAssignmentItem? = null,
    val quiz: VirtualQuizItem? = null,
    val virtualEvent: VirtualCalendarEvent? = null,
    val webUrl: String = ""
)

fun isLeapYear(year: Int): Boolean =
    (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeapYear(year)) 29 else 28
    else -> 0
}

fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    val y = (if (month <= 2) year - 1 else year).toLong()
    val era = if (y >= 0L) y / 400L else (y - 399L) / 400L
    val yoe = y - era * 400L
    val m = month.toLong()
    val doy = (153L * (m + if (m > 2L) -3L else 9L) + 2L) / 5L + day.toLong() - 1L
    val doe = yoe * 365L + yoe / 4L - yoe / 100L + doy
    return era * 146_097L + doe - 719_468L
}

fun civilFromDays(z: Long): CalendarDate {
    val shifted = z + 719_468L
    val era = (if (shifted >= 0L) shifted else shifted - 146_096L) / 146_097L
    val doe = shifted - era * 146_097L
    val yoe = (doe - doe / 1460L + doe / 36_524L - doe / 146_096L) / 365L
    val y = yoe + era * 400L
    val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
    val mp = (5L * doy + 2L) / 153L
    val d = (doy - (153L * mp + 2L) / 5L + 1L).toInt()
    val m = (mp + if (mp < 10L) 3L else -9L).toInt()
    val year = (y + if (m <= 2) 1L else 0L).toInt()
    return CalendarDate(year = year, month = m, day = d)
}

/**
 * 1 = Segunda-feira .. 7 = Domingo
 */
fun dayOfWeekIso(date: CalendarDate): Int {
    val days = daysFromCivil(date.year, date.month, date.day)
    val mod = (((days + 3L) % 7L) + 7L) % 7L
    return mod.toInt() + 1
}

/**
 * 0 = Domingo .. 6 = Sábado
 */
fun dayOfWeekSundayFirst(date: CalendarDate): Int = dayOfWeekIso(date) % 7

fun addDays(date: CalendarDate, deltaDays: Int): CalendarDate =
    civilFromDays(daysFromCivil(date.year, date.month, date.day) + deltaDays.toLong())

fun previousMonth(year: Int, month: Int): Pair<Int, Int> =
    if (month <= 1) (year - 1) to 12 else year to (month - 1)

fun nextMonth(year: Int, month: Int): Pair<Int, Int> =
    if (month >= 12) (year + 1) to 1 else year to (month + 1)

fun formatIsoDate(date: CalendarDate): String =
    "${date.year.toString().padStart(4, '0')}-${date.month.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"

fun formatBrDate(date: CalendarDate): String =
    "${date.day.toString().padStart(2, '0')}/${date.month.toString().padStart(2, '0')}/${date.year.toString().padStart(4, '0')}"

fun parseIsoDate(raw: String): CalendarDate? {
    val s = raw.trim()
    if (s.length != 10 || s[4] != '-' || s[7] != '-') return null
    val y = s.substring(0, 4).toIntOrNull() ?: return null
    val m = s.substring(5, 7).toIntOrNull() ?: return null
    val d = s.substring(8, 10).toIntOrNull() ?: return null
    if (y !in 1970..2100 || m !in 1..12) return null
    val maxDay = daysInMonth(y, m)
    if (d !in 1..maxDay) return null
    return CalendarDate(y, m, d)
}

fun parseBrDate(raw: String): CalendarDate? {
    val s = raw.trim()
    if (s.length != 10 || s[2] != '/' || s[5] != '/') return null
    val d = s.substring(0, 2).toIntOrNull() ?: return null
    val m = s.substring(3, 5).toIntOrNull() ?: return null
    val y = s.substring(6, 10).toIntOrNull() ?: return null
    if (y !in 1970..2100 || m !in 1..12) return null
    val maxDay = daysInMonth(y, m)
    if (d !in 1..maxDay) return null
    return CalendarDate(y, m, d)
}

private fun floorDiv(a: Long, b: Long): Long {
    var r = a / b
    if ((a xor b) < 0L && r * b != a) {
        r--
    }
    return r
}

fun epochMillisToLocalDate(millis: Long, utcOffsetMinutes: Int = -180): CalendarDate {
    val localSec = floorDiv(millis, 1000L) + utcOffsetMinutes.toLong() * 60L
    val localDays = floorDiv(localSec, 86_400L)
    return civilFromDays(localDays)
}

fun epochMillisToLocalTime(millis: Long, utcOffsetMinutes: Int = -180): String {
    val localSec = floorDiv(millis, 1000L) + utcOffsetMinutes.toLong() * 60L
    val daySec = localSec - floorDiv(localSec, 86_400L) * 86_400L
    val hh = (daySec / 3600L).toInt()
    val mm = ((daySec % 3600L) / 60L).toInt()
    return "${hh.toString().padStart(2, '0')}:${mm.toString().padStart(2, '0')}"
}

fun localDateTimeToEpochMillis(
    date: CalendarDate,
    hour: Int,
    minute: Int,
    utcOffsetMinutes: Int = -180
): Long {
    val days = daysFromCivil(date.year, date.month, date.day)
    val localSec = days * 86_400L + hour.toLong() * 3600L + minute.toLong() * 60L
    val utcSec = localSec - utcOffsetMinutes.toLong() * 60L
    return utcSec * 1000L
}

fun parseHourMinute(raw: String): Pair<Int, Int>? {
    val s = raw.trim()
    if (s.length != 5 || s[2] != ':') return null
    val h = s.substring(0, 2).toIntOrNull() ?: return null
    val m = s.substring(3, 5).toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h to m
}

fun buildMonthGrid(year: Int, month: Int): List<CalendarDate?> {
    val totalDays = daysInMonth(year, month)
    if (totalDays <= 0) return emptyList()
    val firstDay = CalendarDate(year, month, 1)
    val leadingNulls = dayOfWeekSundayFirst(firstDay)
    val cells = ArrayList<CalendarDate?>(42)
    repeat(leadingNulls) { cells.add(null) }
    for (day in 1..totalDays) {
        cells.add(CalendarDate(year, month, day))
    }
    while (cells.size % 7 != 0) {
        cells.add(null)
    }
    return cells
}

fun monthNamePt(month: Int): String = when (month) {
    1 -> "Janeiro"
    2 -> "Fevereiro"
    3 -> "Março"
    4 -> "Abril"
    5 -> "Maio"
    6 -> "Junho"
    7 -> "Julho"
    8 -> "Agosto"
    9 -> "Setembro"
    10 -> "Outubro"
    11 -> "Novembro"
    12 -> "Dezembro"
    else -> ""
}

fun fullDateLabelPt(date: CalendarDate): String {
    val weekday = isoDayLabel(dayOfWeekIso(date))
    val monthName = monthNamePt(date.month).lowercase()
    return "$weekday, ${date.day} de $monthName"
}

fun buildCalendarAgendaItems(
    userEvents: List<UserCalendarEvent>,
    virtualOverview: VirtualOverviewResponse?,
    virtualCourseDetails: Map<Long, VirtualCourseDetailResponse> = emptyMap(),
    utcOffsetMinutes: Int = -180
): List<CalendarAgendaItem> {
    val out = mutableListOf<CalendarAgendaItem>()
    val courseDeadlinesByDate = mutableMapOf<CalendarDate, MutableList<String>>()

    // 1. Eventos pessoais do usuário
    for (ev in userEvents) {
        val date = parseIsoDate(ev.date)
            ?: ev.startAtMillis.takeIf { it > 0L }?.let { epochMillisToLocalDate(it, utcOffsetMinutes) }
            ?: continue
        val parsedHm = if (!ev.allDay) parseHourMinute(ev.time) else null
        val sortMs = when {
            ev.startAtMillis > 0L -> ev.startAtMillis
            ev.allDay -> localDateTimeToEpochMillis(date, 8, 0, utcOffsetMinutes)
            parsedHm != null -> localDateTimeToEpochMillis(date, parsedHm.first, parsedHm.second, utcOffsetMinutes)
            else -> localDateTimeToEpochMillis(date, 23, 59, utcOffsetMinutes)
        }
        val timeLabel = when {
            ev.allDay -> "Dia inteiro"
            ev.time.isNotBlank() -> ev.time
            ev.startAtMillis > 0L -> epochMillisToLocalTime(ev.startAtMillis, utcOffsetMinutes)
            else -> ""
        }
        val reminderCount = ev.reminderMinutes.size
        val badge = when {
            reminderCount == 1 -> formatCalendarReminderChipLabel(ev.reminderMinutes.first())
            reminderCount > 1 -> "$reminderCount alertas"
            else -> ""
        }
        out += CalendarAgendaItem(
            id = "user_${ev.id}",
            source = CalendarItemSource.PERSONAL,
            date = date,
            sortMillis = sortMs,
            timeLabel = timeLabel,
            title = ev.title,
            subtitle = ev.disciplineName.takeIf { it.isNotBlank() }?.toDisplayTitle().orEmpty(),
            description = ev.description,
            colorKey = ev.colorKey.ifBlank { "accent" },
            statusBadge = badge,
            allDay = ev.allDay,
            userEvent = ev
        )
    }

    // 2. Prazos de atividades da Sala Virtual (detalhes carregados da disciplina têm precedência)
    val allAssignments = (
        virtualCourseDetails.values.flatMap { it.assignments } +
            virtualOverview?.upcomingAssignments.orEmpty()
        ).distinctBy { "${it.orgUnitId}:${it.id}" }

    for (a in allAssignments) {
        val dueMs = resolveAssignmentDueAtMillis(a)
            .takeIf { it > 0L }
            ?: parseIso8601ToEpochMillis(a.endDate)
            ?: continue
        val date = epochMillisToLocalDate(dueMs, utcOffsetMinutes)
        val timeLabel = epochMillisToLocalTime(dueMs, utcOffsetMinutes)
        val color = when (a.status) {
            "graded", "submitted" -> "green"
            "expired" -> "red"
            else -> "orange"
        }
        courseDeadlinesByDate.getOrPut(date) { mutableListOf() }.add(normalizeSearchText(a.name))
        out += CalendarAgendaItem(
            id = "va_${a.orgUnitId}_${a.id}",
            source = CalendarItemSource.VIRTUAL_ASSIGNMENT,
            date = date,
            sortMillis = dueMs,
            timeLabel = timeLabel,
            title = a.name,
            subtitle = a.orgUnitName.ifBlank { "Sala Virtual" }.toDisplayTitle(),
            description = a.instructions,
            colorKey = color,
            statusBadge = a.statusLabel.ifBlank { "Atividade" },
            assignment = a,
            webUrl = a.webUrl
        )
    }

    // 3. Questionários da Sala Virtual com prazo definido
    val courseQuizzes = virtualCourseDetails.values.flatMap { detail ->
        detail.quizzes.map { q ->
            if (q.orgUnitId == 0L) {
                q.copy(orgUnitId = detail.course.orgUnitId, orgUnitName = detail.course.name)
            } else q
        }
    }
    val allQuizzes = (virtualOverview?.upcomingQuizzes.orEmpty() + courseQuizzes)
        .distinctBy { "${it.orgUnitId}:${it.quizId}" }

    for (q in allQuizzes) {
        val rawDue = q.dueDate.ifBlank { q.endDate }
        val dueMs = parseIso8601ToEpochMillis(rawDue) ?: continue
        val date = epochMillisToLocalDate(dueMs, utcOffsetMinutes)
        val timeLabel = epochMillisToLocalTime(dueMs, utcOffsetMinutes)
        courseDeadlinesByDate.getOrPut(date) { mutableListOf() }.add(normalizeSearchText(q.name))
        out += CalendarAgendaItem(
            id = "vq_${q.orgUnitId}_${q.quizId}",
            source = CalendarItemSource.VIRTUAL_QUIZ,
            date = date,
            sortMillis = dueMs,
            timeLabel = timeLabel,
            title = q.name,
            subtitle = q.orgUnitName.ifBlank { "Sala Virtual" }.toDisplayTitle(),
            description = q.description,
            colorKey = "indigo",
            statusBadge = "Questionário",
            quiz = q,
            webUrl = q.webUrl
        )
    }

    // 4. Eventos do calendário do Brightspace (deduplicados contra atividades/questionários no mesmo dia)
    val allVirtualEvents = (
        virtualOverview?.calendarEvents.orEmpty() +
            virtualCourseDetails.values.flatMap { it.calendar }
        ).distinctBy { "${it.orgUnitId}:${it.id}" }

    for (ev in allVirtualEvents) {
        val parsedMs = parseIso8601ToEpochMillis(ev.startDateTime)
        val date = parsedMs?.let { epochMillisToLocalDate(it, utcOffsetMinutes) }
            ?: parseIsoDate(ev.startDateTime.take(10))
            ?: continue
        val normTitle = normalizeSearchText(ev.title)
        val sameDayDeadlines = courseDeadlinesByDate[date].orEmpty()
        val isDuplicate = normTitle.isNotBlank() && sameDayDeadlines.any { existing ->
            existing == normTitle || existing.contains(normTitle) || normTitle.contains(existing)
        }
        if (isDuplicate) continue

        val sortMs = parsedMs ?: localDateTimeToEpochMillis(date, if (ev.allDay) 8 else 23, if (ev.allDay) 0 else 59, utcOffsetMinutes)
        val timeLabel = when {
            ev.allDay -> "Dia inteiro"
            parsedMs != null -> epochMillisToLocalTime(parsedMs, utcOffsetMinutes)
            else -> ""
        }
        out += CalendarAgendaItem(
            id = "ve_${ev.orgUnitId}_${ev.id}",
            source = CalendarItemSource.VIRTUAL_EVENT,
            date = date,
            sortMillis = sortMs,
            timeLabel = timeLabel,
            title = ev.title,
            subtitle = ev.orgUnitName.ifBlank { "Sala Virtual" }.toDisplayTitle(),
            description = ev.description,
            colorKey = "teal",
            statusBadge = "Sala Virtual",
            allDay = ev.allDay,
            virtualEvent = ev,
            webUrl = ev.webUrl
        )
    }

    return out.sortedWith(
        compareBy<CalendarAgendaItem> { it.date }
            .thenBy { it.sortMillis }
            .thenBy { it.title }
    )
}

fun filterCalendarItems(items: List<CalendarAgendaItem>, filter: CalendarFilter): List<CalendarAgendaItem> =
    when (filter) {
        CalendarFilter.ALL -> items
        CalendarFilter.VIRTUAL -> items.filter { it.source != CalendarItemSource.PERSONAL }
        CalendarFilter.PERSONAL -> items.filter { it.source == CalendarItemSource.PERSONAL }
    }

fun groupCalendarItemsByDate(items: List<CalendarAgendaItem>): Map<CalendarDate, List<CalendarAgendaItem>> =
    items.groupBy { it.date }.mapValues { (_, list) ->
        list.sortedWith(compareBy<CalendarAgendaItem> { it.sortMillis }.thenBy { it.title })
    }

fun upcomingCalendarItems(
    items: List<CalendarAgendaItem>,
    fromDate: CalendarDate,
    days: Int = 14
): List<CalendarAgendaItem> {
    val endDate = addDays(fromDate, days.coerceAtLeast(0))
    return items
        .filter { it.date >= fromDate && it.date <= endDate }
        .sortedWith(compareBy<CalendarAgendaItem> { it.date }.thenBy { it.sortMillis }.thenBy { it.title })
}

/**
 * Builds deduplicated subject suggestions for the calendar event editor, prioritizing
 * current gradebook subjects and skipping redundant Brightspace course suffixes such as
 * `"Eletrônica - 4° Termo - 2/2026..."` when `"Eletrônica"` is already present.
 */
fun buildCalendarSubjectSuggestions(
    gradeNames: List<String>,
    virtualCourseNames: List<String>,
    limit: Int = 20
): List<String> {
    val out = mutableListOf<String>()
    val seenNorm = mutableSetOf<String>()

    for (raw in gradeNames) {
        val clean = raw.trim()
        if (clean.isBlank()) continue
        val norm = normalizeSearchText(clean)
        if (norm.isNotBlank() && seenNorm.add(norm)) {
            out += clean
        }
    }

    for (raw in virtualCourseNames) {
        val clean = raw.trim()
        if (clean.isBlank()) continue
        val normFull = normalizeSearchText(clean)
        if (normFull.isBlank() || seenNorm.contains(normFull)) continue
        val normPrefix = normalizeSearchText(clean.substringBefore(" - ").trim())
        if (normPrefix.isNotBlank() && seenNorm.contains(normPrefix)) continue
        seenNorm.add(normFull)
        out += clean
    }

    return if (limit > 0) out.take(limit) else out
}

/**
 * Filters and ranks subject suggestion chips dynamically as the user types.
 */
fun filterSubjectSuggestions(suggestions: List<String>, query: String): List<String> {
    val normQuery = normalizeSearchText(query)
    if (normQuery.isBlank()) return suggestions
    val tokens = normQuery.split(' ').filter { it.isNotBlank() }

    return suggestions
        .mapNotNull { subj ->
            val norm = normalizeSearchText(subj)
            val rank = when {
                norm == normQuery -> 0
                norm.startsWith(normQuery) -> 1
                norm.contains(normQuery) -> 2
                tokens.isNotEmpty() && tokens.all { tok -> norm.contains(tok) } -> 3
                else -> return@mapNotNull null
            }
            subj to rank
        }
        .sortedBy { it.second }
        .map { it.first }
}
