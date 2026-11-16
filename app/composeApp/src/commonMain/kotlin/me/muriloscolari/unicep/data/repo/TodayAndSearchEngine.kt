package me.muriloscolari.unicep.data.repo

import androidx.compose.runtime.Immutable
import me.muriloscolari.unicep.data.api.CurriculumResponse
import me.muriloscolari.unicep.data.api.ScheduleResponse
import me.muriloscolari.unicep.data.api.VirtualAnnouncement
import me.muriloscolari.unicep.data.api.VirtualAssignmentItem
import me.muriloscolari.unicep.data.api.VirtualContentModule
import me.muriloscolari.unicep.data.api.VirtualContentTopic
import me.muriloscolari.unicep.data.api.VirtualCourseDetailResponse
import me.muriloscolari.unicep.data.api.VirtualOverviewResponse
import me.muriloscolari.unicep.platform.LocalDateTimeInfo
import me.muriloscolari.unicep.ui.screens.UnifiedSubject
import me.muriloscolari.unicep.ui.screens.isFrequencyAtLimit
import me.muriloscolari.unicep.ui.util.toDisplayTitle

@Immutable
data class ScheduleClassEntry(
    val dayOfWeekIso: Int, // 1 = Segunda .. 7 = Domingo, 0 = Desconhecido
    val dayLabel: String,
    val startMinutes: Int?, // minutos desde 00:00
    val endMinutes: Int?,
    val timeLabel: String,
    val subjectName: String,
    val roomOrMeta: String,
    val matchedSubjectCode: String? = null,
    val remainingAbsences: Int? = null,
    val isFrequencyAtLimit: Boolean = false
)

@Immutable
data class TodaySummary(
    val currentDayLabel: String,
    val todayClasses: List<ScheduleClassEntry> = emptyList(),
    val currentClass: ScheduleClassEntry? = null,
    val nextTodayClass: ScheduleClassEntry? = null,
    val nextWeekClass: ScheduleClassEntry? = null,
    val upcomingDeadlines: List<VirtualAssignmentItem> = emptyList()
)

enum class GlobalSearchFilter(val label: String) {
    ALL("Tudo"),
    SEMESTER("Semestre"),
    VIRTUAL("Virtual"),
    COURSE("Curso")
}

enum class GlobalSearchResultKind(val badge: String) {
    SUBJECT("Disciplina"),
    SCHEDULE("Horário"),
    CURRICULUM("Curso"),
    VIRTUAL_COURSE("Sala Virtual"),
    VIRTUAL_ASSIGNMENT("Tarefa"),
    VIRTUAL_ANNOUNCEMENT("Aviso"),
    VIRTUAL_MATERIAL("Material")
}

@Immutable
data class GlobalSearchResult(
    val id: String,
    val kind: GlobalSearchResultKind,
    val filterGroup: GlobalSearchFilter,
    val title: String,
    val subtitle: String,
    val badge: String = kind.badge,
    val isAlert: Boolean = false,
    val subjectCode: String? = null,
    val courseSubTab: Int? = null,
    val virtualCourseId: Long? = null,
    val virtualAssignment: VirtualAssignmentItem? = null,
    val virtualAnnouncement: VirtualAnnouncement? = null,
    val virtualTopic: VirtualContentTopic? = null,
    val virtualCourseName: String? = null
)

fun normalizeSearchText(raw: String): String {
    if (raw.isBlank()) return ""
    val sb = StringBuilder(raw.length)
    var lastSpace = true
    for (ch in raw.lowercase()) {
        val mapped = when (ch) {
            'á', 'à', 'â', 'ã', 'ä' -> 'a'
            'é', 'è', 'ê', 'ë' -> 'e'
            'í', 'ì', 'î', 'ï' -> 'i'
            'ó', 'ò', 'ô', 'õ', 'ö' -> 'o'
            'ú', 'ù', 'û', 'ü' -> 'u'
            'ç' -> 'c'
            'ñ' -> 'n'
            else -> ch
        }
        if (mapped.isWhitespace()) {
            if (!lastSpace) {
                sb.append(' ')
                lastSpace = true
            }
        } else {
            sb.append(mapped)
            lastSpace = false
        }
    }
    return sb.toString().trim()
}

fun isoDayLabel(dayOfWeekIso: Int): String = when (dayOfWeekIso) {
    1 -> "Segunda-feira"
    2 -> "Terça-feira"
    3 -> "Quarta-feira"
    4 -> "Quinta-feira"
    5 -> "Sexta-feira"
    6 -> "Sábado"
    7 -> "Domingo"
    else -> ""
}

private fun detectDayOfWeekIso(raw: String): Int? {
    val n = normalizeSearchText(raw)
    if (n.isBlank()) return null
    return when {
        n.contains("segunda") || Regex("\\bseg\\b").containsMatchIn(n) -> 1
        n.contains("terca") || Regex("\\bter\\b").containsMatchIn(n) -> 2
        n.contains("quarta") || Regex("\\bqua\\b").containsMatchIn(n) -> 3
        n.contains("quinta") || Regex("\\bqui\\b").containsMatchIn(n) -> 4
        n.contains("sexta") || Regex("\\bsex\\b").containsMatchIn(n) -> 5
        n.contains("sabado") || Regex("\\bsab\\b").containsMatchIn(n) -> 6
        n.contains("domingo") || Regex("\\bdom\\b").containsMatchIn(n) -> 7
        else -> null
    }
}

private val timeRegex = Regex("""(\d{1,2})\s*[:hH]\s*(\d{2})""")

private fun parseTimeRangeMinutes(raw: String): Pair<Int?, Int?> {
    val matches = timeRegex.findAll(raw).toList()
    if (matches.isEmpty()) return null to null
    val startH = matches[0].groupValues[1].toIntOrNull()
    val startM = matches[0].groupValues[2].toIntOrNull()
    val start = if (startH != null && startM != null && startH in 0..23 && startM in 0..59) {
        startH * 60 + startM
    } else null
    val end = if (matches.size >= 2) {
        val endH = matches[1].groupValues[1].toIntOrNull()
        val endM = matches[1].groupValues[2].toIntOrNull()
        if (endH != null && endM != null && endH in 0..23 && endM in 0..59) {
            endH * 60 + endM
        } else null
    } else null
    return start to end
}

private fun formatMinutes(minutes: Int): String {
    val h = (minutes / 60).toString().padStart(2, '0')
    val m = (minutes % 60).toString().padStart(2, '0')
    return "$h:$m"
}

private fun matchSubjectByName(subjectName: String, subjects: List<UnifiedSubject>): UnifiedSubject? {
    val target = normalizeSearchText(subjectName)
    if (target.isBlank() || subjects.isEmpty()) return null

    // 1) Exact or containment match
    subjects.firstOrNull {
        val n = normalizeSearchText(it.name)
        n == target || (n.length >= 5 && (target.contains(n) || n.contains(target)))
    }?.let { return it }

    // 2) Token overlap match
    val stopWords = setOf("de", "da", "do", "das", "dos", "e", "em", "para", "com", "a", "o", "i", "ii", "iii", "iv")
    val targetTokens = target.split(' ').filter { it.length >= 3 && it !in stopWords }.toSet()
    if (targetTokens.isEmpty()) return null

    var best: UnifiedSubject? = null
    var bestScore = 0.0
    for (subj in subjects) {
        val subjTokens = normalizeSearchText(subj.name)
            .split(' ')
            .filter { it.length >= 3 && it !in stopWords }
            .toSet()
        if (subjTokens.isEmpty()) continue
        val common = targetTokens.intersect(subjTokens).size
        val score = common.toDouble() / maxOf(targetTokens.size, subjTokens.size).toDouble()
        if (common >= 2 && score > bestScore && score >= 0.5) {
            bestScore = score
            best = subj
        }
    }
    return best
}

fun parseScheduleEntries(
    schedule: ScheduleResponse?,
    subjects: List<UnifiedSubject>
): List<ScheduleClassEntry> {
    if (schedule == null || schedule.rows.isEmpty()) return emptyList()

    val headers = schedule.headers
    val headerDays = headers.map { detectDayOfWeekIso(it) }
    val isMatrixLayout = headerDays.count { it != null } >= 3

    val rawEntries = mutableListOf<ScheduleClassEntry>()

    if (isMatrixLayout) {
        // Layout matriz: col 0 = horário, cols 1..N = dias da semana
        for (row in schedule.rows) {
            val timeRaw = row.firstOrNull().orEmpty().trim()
            val (startMin, endMin) = parseTimeRangeMinutes(timeRaw)
            val timeLabel = when {
                startMin != null && endMin != null -> "${formatMinutes(startMin)} - ${formatMinutes(endMin)}"
                startMin != null -> formatMinutes(startMin)
                else -> timeRaw
            }
            for (colIdx in 1 until row.size) {
                val dayIso = headerDays.getOrNull(colIdx) ?: continue
                val cellText = row[colIdx].trim()
                if (cellText.isBlank() || cellText == "-" || cellText == "--") continue
                val eadRegex = Regex("""\s*\(\s*a\s+dist[aâã]ncia\s*\)\s*$""", RegexOption.IGNORE_CASE)
                val isEad = eadRegex.containsMatchIn(cellText)
                val cleanSubjText = if (isEad) cellText.replace(eadRegex, "").trim() else cellText
                val matched = matchSubjectByName(cleanSubjText, subjects)
                rawEntries += ScheduleClassEntry(
                    dayOfWeekIso = dayIso,
                    dayLabel = isoDayLabel(dayIso),
                    startMinutes = startMin,
                    endMinutes = endMin,
                    timeLabel = timeLabel,
                    subjectName = (matched?.name ?: cleanSubjText).toDisplayTitle(),
                    roomOrMeta = if (isEad) "Modalidade a distância" else "",
                    matchedSubjectCode = matched?.code,
                    remainingAbsences = matched?.report?.remainingAbsences,
                    isFrequencyAtLimit = matched?.report?.isFrequencyAtLimit() == true
                )
            }
        }
    } else {
        // Layout tabular: tenta identificar colunas pelos headers ou heurística
        val dayColIdx = headers.indexOfFirst {
            val n = normalizeSearchText(it)
            n.contains("dia") || n.contains("semana")
        }
        val timeColIdx = headers.indexOfFirst {
            val n = normalizeSearchText(it)
            n.contains("hor") || n.contains("inicio")
        }
        val subjColIdx = headers.indexOfFirst {
            val n = normalizeSearchText(it)
            n.contains("disc") || n.contains("mater") || n.contains("aula")
        }

        for (row in schedule.rows) {
            if (row.all { it.isBlank() }) continue

            var dayIso: Int? = if (dayColIdx in row.indices) detectDayOfWeekIso(row[dayColIdx]) else null
            var dayCellIdx = if (dayIso != null) dayColIdx else -1
            if (dayIso == null) {
                for (i in row.indices) {
                    val found = detectDayOfWeekIso(row[i])
                    if (found != null) {
                        dayIso = found
                        dayCellIdx = i
                        break
                    }
                }
            }

            var timeCellIdx = if (timeColIdx in row.indices && parseTimeRangeMinutes(row[timeColIdx]).first != null) {
                timeColIdx
            } else -1
            var startMin: Int? = null
            var endMin: Int? = null
            if (timeCellIdx >= 0) {
                val parsed = parseTimeRangeMinutes(row[timeCellIdx])
                startMin = parsed.first
                endMin = parsed.second
            } else {
                for (i in row.indices) {
                    val parsed = parseTimeRangeMinutes(row[i])
                    if (parsed.first != null) {
                        startMin = parsed.first
                        endMin = parsed.second
                        timeCellIdx = i
                        break
                    }
                }
            }

            val timeLabel = when {
                startMin != null && endMin != null -> "${formatMinutes(startMin)} - ${formatMinutes(endMin)}"
                startMin != null -> formatMinutes(startMin)
                timeCellIdx in row.indices -> row[timeCellIdx].trim()
                else -> ""
            }

            val resolvedSubjIdx = when {
                subjColIdx in row.indices && row[subjColIdx].isNotBlank() -> subjColIdx
                else -> {
                    val candidates = row.indices.filter { idx ->
                        idx != dayCellIdx && idx != timeCellIdx && row[idx].isNotBlank()
                    }
                    candidates.firstOrNull { row[it].length > 5 } ?: candidates.firstOrNull() ?: 1.coerceAtMost(row.lastIndex)
                }
            }

            val subjectRaw = row.getOrNull(resolvedSubjIdx).orEmpty().trim()
            if (subjectRaw.isBlank()) continue

            val metaParts = row.indices
                .filter { idx ->
                    idx != dayCellIdx &&
                        idx != timeCellIdx &&
                        idx != resolvedSubjIdx &&
                        row[idx].isNotBlank()
                }
                .map { row[it].trim() }

            val matched = matchSubjectByName(subjectRaw, subjects)
            val finalDayIso = dayIso ?: 0
            rawEntries += ScheduleClassEntry(
                dayOfWeekIso = finalDayIso,
                dayLabel = if (finalDayIso > 0) isoDayLabel(finalDayIso) else row.getOrNull(0).orEmpty().trim(),
                startMinutes = startMin,
                endMinutes = endMin,
                timeLabel = timeLabel,
                subjectName = subjectRaw.toDisplayTitle(),
                roomOrMeta = metaParts.joinToString(" · "),
                matchedSubjectCode = matched?.code,
                remainingAbsences = matched?.report?.remainingAbsences,
                isFrequencyAtLimit = matched?.report?.isFrequencyAtLimit() == true
            )
        }
    }

    val sorted = rawEntries.sortedWith(
        compareBy<ScheduleClassEntry> { if (it.dayOfWeekIso == 0) 99 else it.dayOfWeekIso }
            .thenBy { it.startMinutes ?: Int.MAX_VALUE }
            .thenBy { it.subjectName }
    )

    // Merge consecutive slots of the same subject on the same day (e.g. 19:30-20:20, 20:20-21:10, 21:10-22:00 -> 19:30-22:00)
    val merged = mutableListOf<ScheduleClassEntry>()
    for (entry in sorted) {
        val last = merged.lastOrNull()
        val canMerge = last != null &&
            last.dayOfWeekIso == entry.dayOfWeekIso &&
            normalizeSearchText(last.subjectName) == normalizeSearchText(entry.subjectName) &&
            last.endMinutes != null &&
            entry.startMinutes != null &&
            entry.endMinutes != null &&
            entry.startMinutes >= last.endMinutes &&
            (entry.startMinutes - last.endMinutes) <= 20
        if (canMerge && last != null) {
            val newEnd = maxOf(last.endMinutes!!, entry.endMinutes!!)
            val newStart = last.startMinutes!!
            merged[merged.lastIndex] = last.copy(
                endMinutes = newEnd,
                timeLabel = "${formatMinutes(newStart)} - ${formatMinutes(newEnd)}"
            )
        } else {
            merged += entry
        }
    }
    return merged
}

fun buildTodaySummary(
    schedule: ScheduleResponse?,
    subjects: List<UnifiedSubject>,
    virtualOverview: VirtualOverviewResponse?,
    nowLocal: LocalDateTimeInfo,
    nowMillis: Long
): TodaySummary {
    val entries = parseScheduleEntries(schedule, subjects)
    val nowMinutes = nowLocal.hour * 60 + nowLocal.minute

    val todayClasses = entries
        .filter { it.dayOfWeekIso == nowLocal.dayOfWeekIso || it.dayOfWeekIso == 0 }
        .sortedBy { it.startMinutes ?: Int.MAX_VALUE }

    val currentClass = todayClasses.firstOrNull { entry ->
        val start = entry.startMinutes ?: return@firstOrNull false
        val end = entry.endMinutes ?: (start + 100)
        nowMinutes in start..end
    }

    val nextTodayClass = if (currentClass == null) {
        todayClasses.firstOrNull { entry ->
            val start = entry.startMinutes ?: return@firstOrNull false
            start > nowMinutes
        }
    } else {
        todayClasses.firstOrNull { entry ->
            val start = entry.startMinutes ?: return@firstOrNull false
            start > (currentClass.startMinutes ?: nowMinutes)
        }
    }

    val allTodayEnded = todayClasses.isEmpty() || (currentClass == null && nextTodayClass == null)
    val nextWeekClass = if (allTodayEnded) {
        var found: ScheduleClassEntry? = null
        for (offset in 1..7) {
            val candidateDay = ((nowLocal.dayOfWeekIso - 1 + offset) % 7) + 1
            val dayEntries = entries
                .filter { it.dayOfWeekIso == candidateDay }
                .sortedBy { it.startMinutes ?: Int.MAX_VALUE }
            if (dayEntries.isNotEmpty()) {
                found = dayEntries.first()
                break
            }
        }
        found
    } else null

    val sevenDaysMs = 7L * 24L * 60L * 60L * 1000L
    val gracePastMs = 2L * 60L * 60L * 1000L
    val upcomingDeadlines = virtualOverview?.upcomingAssignments
        .orEmpty()
        .asSequence()
        .filter { item ->
            val pending = item.status != "submitted" && item.status != "graded"
            pending && item.dueAtMillis > 0L &&
                item.dueAtMillis >= (nowMillis - gracePastMs) &&
                item.dueAtMillis <= (nowMillis + sevenDaysMs)
        }
        .sortedBy { it.dueAtMillis }
        .take(5)
        .toList()

    return TodaySummary(
        currentDayLabel = isoDayLabel(nowLocal.dayOfWeekIso),
        todayClasses = todayClasses,
        currentClass = currentClass,
        nextTodayClass = nextTodayClass,
        nextWeekClass = nextWeekClass,
        upcomingDeadlines = upcomingDeadlines
    )
}

private fun flattenTopics(
    modules: List<VirtualContentModule>,
    courseName: String,
    orgUnitId: Long
): List<Triple<String, VirtualContentTopic, Long>> {
    val result = mutableListOf<Triple<String, VirtualContentTopic, Long>>()
    fun walk(mod: VirtualContentModule) {
        val modPrefix = mod.title.trim()
        for (t in mod.topics) {
            val ctx = if (modPrefix.isNotBlank()) "$courseName · $modPrefix" else courseName
            result += Triple(ctx, t, orgUnitId)
        }
        for (sub in mod.modules) walk(sub)
    }
    for (m in modules) walk(m)
    return result
}

fun searchPortalData(
    query: String,
    filter: GlobalSearchFilter,
    subjects: List<UnifiedSubject>,
    schedule: ScheduleResponse?,
    curriculum: CurriculumResponse?,
    virtualOverview: VirtualOverviewResponse?,
    virtualCourseDetails: Map<Long, VirtualCourseDetailResponse>
): List<GlobalSearchResult> {
    val nq = normalizeSearchText(query)
    if (nq.length < 2) return emptyList()
    val tokens = nq.split(' ').filter { it.isNotBlank() }

    fun matches(vararg fields: String): Boolean {
        if (tokens.isEmpty()) return false
        val combined = normalizeSearchText(fields.joinToString(" "))
        return tokens.all { combined.contains(it) }
    }

    val results = mutableListOf<GlobalSearchResult>()

    // 1. Semestre: Disciplinas (Notas e Faltas)
    if (filter == GlobalSearchFilter.ALL || filter == GlobalSearchFilter.SEMESTER) {
        for (s in subjects) {
            if (matches(s.name, s.code, s.report?.statusLabel.orEmpty())) {
                val avgText = s.grade?.average?.display?.takeIf { it.isNotBlank() }
                    ?: s.report?.finalAverage?.takeIf { it.isNotBlank() }
                    ?: "—"
                val absText = s.report?.let { r ->
                    if (r.hours > 0) "${r.absences}/${r.maxAbsences} faltas (restam ${r.remainingAbsences})"
                    else "${r.absences} faltas"
                } ?: "Sem faltas registradas"
                results += GlobalSearchResult(
                    id = "subj_${s.code}",
                    kind = GlobalSearchResultKind.SUBJECT,
                    filterGroup = GlobalSearchFilter.SEMESTER,
                    title = s.name.toDisplayTitle(),
                    subtitle = "Média: $avgText · $absText",
                    isAlert = s.report?.isFrequencyAtLimit() == true || s.grade?.belowPassing == true,
                    subjectCode = s.code
                )
            }
        }
    }

    // 2. Sala Virtual (Cursos, Tarefas, Avisos, Materiais)
    if (filter == GlobalSearchFilter.ALL || filter == GlobalSearchFilter.VIRTUAL) {
        val ov = virtualOverview
        if (ov != null) {
            for (c in ov.courses) {
                if (matches(c.name, c.code, c.semesterName)) {
                    val sub = buildString {
                        if (c.semesterName.isNotBlank()) append(c.semesterName)
                        if (c.pendingCount > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("${c.pendingCount} pendente(s)")
                        }
                    }.ifBlank { c.code }
                    results += GlobalSearchResult(
                        id = "vc_${c.orgUnitId}",
                        kind = GlobalSearchResultKind.VIRTUAL_COURSE,
                        filterGroup = GlobalSearchFilter.VIRTUAL,
                        title = c.name.toDisplayTitle(),
                        subtitle = sub,
                        isAlert = c.pendingCount > 0,
                        virtualCourseId = c.orgUnitId,
                        virtualCourseName = c.name
                    )
                }
            }

            val allAssignments = (ov.upcomingAssignments + virtualCourseDetails.values.flatMap { it.assignments })
                .distinctBy { "${it.orgUnitId}_${it.id}" }
            for (a in allAssignments) {
                if (matches(a.name, a.orgUnitName, a.statusLabel)) {
                    val due = a.dueDate.takeIf { it.isNotBlank() } ?: "Sem prazo"
                    results += GlobalSearchResult(
                        id = "va_${a.orgUnitId}_${a.id}",
                        kind = GlobalSearchResultKind.VIRTUAL_ASSIGNMENT,
                        filterGroup = GlobalSearchFilter.VIRTUAL,
                        title = a.name,
                        subtitle = "${a.orgUnitName.toDisplayTitle()} · ${a.statusLabel} · $due",
                        isAlert = a.status != "submitted" && a.status != "graded",
                        virtualCourseId = a.orgUnitId,
                        virtualAssignment = a,
                        virtualCourseName = a.orgUnitName
                    )
                }
            }

            val allAnnouncements = (ov.announcements + virtualCourseDetails.values.flatMap { it.announcements })
                .distinctBy { "${it.orgUnitId}_${it.id}" }
            for (ann in allAnnouncements) {
                if (matches(ann.title, ann.orgUnitName, ann.body)) {
                    results += GlobalSearchResult(
                        id = "vann_${ann.orgUnitId}_${ann.id}",
                        kind = GlobalSearchResultKind.VIRTUAL_ANNOUNCEMENT,
                        filterGroup = GlobalSearchFilter.VIRTUAL,
                        title = ann.title,
                        subtitle = ann.orgUnitName.toDisplayTitle(),
                        virtualCourseId = ann.orgUnitId,
                        virtualAnnouncement = ann,
                        virtualCourseName = ann.orgUnitName
                    )
                }
            }
        }

        for ((ou, detail) in virtualCourseDetails) {
            val cName = detail.course.name.ifBlank { "Sala Virtual" }.toDisplayTitle()
            val topics = flattenTopics(detail.modules, cName, ou)
            for ((ctx, topic, orgId) in topics) {
                if (matches(topic.title, ctx, topic.typeIdentifier)) {
                    results += GlobalSearchResult(
                        id = "vm_${orgId}_${topic.topicId}",
                        kind = GlobalSearchResultKind.VIRTUAL_MATERIAL,
                        filterGroup = GlobalSearchFilter.VIRTUAL,
                        title = topic.title,
                        subtitle = ctx,
                        virtualCourseId = orgId,
                        virtualTopic = topic,
                        virtualCourseName = cName
                    )
                }
            }
        }
    }

    // 3. Curso (Horário, Histórico, Matriz)
    if (filter == GlobalSearchFilter.ALL || filter == GlobalSearchFilter.COURSE) {
        val schedEntries = parseScheduleEntries(schedule, subjects)
        for ((idx, entry) in schedEntries.withIndex()) {
            if (matches(entry.subjectName, entry.dayLabel, entry.timeLabel, entry.roomOrMeta)) {
                val sub = listOf(entry.dayLabel, entry.timeLabel, entry.roomOrMeta)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                results += GlobalSearchResult(
                    id = "sched_${idx}_${entry.subjectName}",
                    kind = GlobalSearchResultKind.SCHEDULE,
                    filterGroup = GlobalSearchFilter.COURSE,
                    title = entry.subjectName,
                    subtitle = sub,
                    courseSubTab = 2
                )
            }
        }

        curriculum?.transcript?.forEachIndexed { idx, t ->
            if (matches(t.name, t.yearSem, t.statusLabel)) {
                val sub = listOfNotNull(
                    t.yearSem.takeIf { it.isNotBlank() },
                    t.average.takeIf { it.isNotBlank() }?.let { "Média $it" },
                    t.statusLabel.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                results += GlobalSearchResult(
                    id = "tr_${idx}_${t.name}",
                    kind = GlobalSearchResultKind.CURRICULUM,
                    filterGroup = GlobalSearchFilter.COURSE,
                    title = t.name.toDisplayTitle(),
                    subtitle = sub.ifBlank { "Histórico escolar" },
                    badge = "Histórico",
                    courseSubTab = 0
                )
            }
        }

        curriculum?.matrix?.forEachIndexed { idx, m ->
            if (matches(m.name, m.code, m.period)) {
                val sub = listOfNotNull(
                    m.period.takeIf { it.isNotBlank() },
                    m.hours.takeIf { it.isNotBlank() }?.let { "${it}h" },
                    m.code.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                results += GlobalSearchResult(
                    id = "mat_${idx}_${m.code}",
                    kind = GlobalSearchResultKind.CURRICULUM,
                    filterGroup = GlobalSearchFilter.COURSE,
                    title = m.name.toDisplayTitle(),
                    subtitle = sub.ifBlank { "Matriz curricular" },
                    badge = "Matriz",
                    courseSubTab = 1
                )
            }
        }
    }

    return results.take(40)
}
