package me.muriloscolari.unicep.data.repo

import androidx.compose.runtime.Immutable
import me.muriloscolari.unicep.data.api.VirtualAssignmentItem
import me.muriloscolari.unicep.data.api.VirtualCourseDetailResponse
import me.muriloscolari.unicep.data.api.VirtualCourseSummary
import me.muriloscolari.unicep.data.api.VirtualOverviewResponse
import me.muriloscolari.unicep.ui.screens.UnifiedSubject

@Immutable
data class SubjectVirtualLink(
    val course: VirtualCourseSummary? = null,
    val pendingAssignments: List<VirtualAssignmentItem> = emptyList()
)

private val matchStopWords = setOf(
    "de", "da", "do", "das", "dos", "e", "em", "para", "com", "a", "o", "as", "os",
    "no", "na", "nos", "nas", "ao", "aos", "por", "sem", "turma", "ead", "presencial",
    "semipresencial", "semestre", "modulo", "disciplina", "noturno", "diurno", "matutino", "vespertino"
)

private val discriminatorTokens = setOf(
    "i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x",
    "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"
)

private val semesterPatternRegex = Regex("""\b(?:\d{4}[/.\-]\d{1,2}|\d{1,2}[/.\-]\d{4})\b""")
private val longCodeRegex = Regex("""\b\d{5,}\b""")
private val modalityNoiseRegex = Regex("""\b(?:a\s+distancia|ead|presencial|semipresencial|turma\s+[a-z0-9]+|noturno|diurno|matutino|vespertino)\b""")
private val nonAlphaNumRegex = Regex("""[^a-z0-9]+""")

internal fun cleanSubjectMatchName(raw: String): String {
    var norm = normalizeSearchText(raw)
    if (norm.isBlank()) return ""
    norm = semesterPatternRegex.replace(norm, " ")
    norm = longCodeRegex.replace(norm, " ")
    norm = nonAlphaNumRegex.replace(norm, " ")
    norm = modalityNoiseRegex.replace(norm, " ")
    return norm.replace(Regex("""\s+"""), " ").trim()
}

private fun extractDiscriminators(cleanName: String): Set<String> =
    cleanName.split(' ').filter { it in discriminatorTokens }.toSet()

private fun extractSignificantTokens(cleanName: String): Set<String> =
    cleanName.split(' ').filter { token ->
        token.isNotBlank() && token !in matchStopWords && (token.length >= 3 || token in discriminatorTokens)
    }.toSet()

private fun semesterOrderKey(name: String): String {
    val parts = name.trim().split('/')
    if (parts.size == 2) {
        val first = parts[0].trim().toIntOrNull()
        val second = parts[1].trim().toIntOrNull()
        if (first != null && second != null) {
            return if (first > 1000) {
                "${first.toString().padStart(4, '0')}-${second.toString().padStart(2, '0')}"
            } else {
                "${second.toString().padStart(4, '0')}-${first.toString().padStart(2, '0')}"
            }
        }
    }
    return name.trim()
}

private fun rankCoursesForMatching(courses: List<VirtualCourseSummary>): List<VirtualCourseSummary> =
    courses.sortedWith(
        compareByDescending<VirtualCourseSummary> { it.canAccess }
            .thenByDescending { it.isActive }
            .thenByDescending { semesterOrderKey(it.semesterName) }
            .thenByDescending { it.orgUnitId }
    )

private fun scoreCourseMatch(
    subjectCode: String,
    subjectClean: String,
    subjectTokens: Set<String>,
    subjectDiscriminators: Set<String>,
    course: VirtualCourseSummary
): Double {
    val cleanCode = normalizeSearchText(subjectCode).replace(" ", "")
    val courseCodeNorm = normalizeSearchText(course.code).replace(" ", "")
    val courseRawNorm = normalizeSearchText(course.name).replace(" ", "")
    val courseClean = cleanSubjectMatchName(course.name)

    if (cleanCode.length >= 4 && (courseCodeNorm.contains(cleanCode) || courseRawNorm.contains(cleanCode))) {
        return 10.0
    }
    if (subjectClean.isBlank() || courseClean.isBlank()) return 0.0

    val courseDiscriminators = extractDiscriminators(courseClean)
    val discriminatorsConflict = subjectDiscriminators.isNotEmpty() &&
        courseDiscriminators.isNotEmpty() &&
        subjectDiscriminators != courseDiscriminators
    if (discriminatorsConflict) return 0.0

    if (subjectClean == courseClean) return 9.0

    if (subjectClean.length >= 5 && courseClean.length >= 5) {
        val contained = courseClean.contains(subjectClean) || subjectClean.contains(courseClean)
        if (contained && !discriminatorsConflict && (subjectDiscriminators == courseDiscriminators || subjectDiscriminators.isEmpty())) {
            val lenRatio = minOf(subjectClean.length, courseClean.length).toDouble() /
                maxOf(subjectClean.length, courseClean.length).toDouble()
            return 7.0 + lenRatio
        }
    }

    val courseTokens = extractSignificantTokens(courseClean)
    if (subjectTokens.isEmpty() || courseTokens.isEmpty()) return 0.0

    val common = subjectTokens.intersect(courseTokens)
    if (common.isEmpty()) return 0.0

    val nonDiscCommon = common.count { it !in discriminatorTokens }
    if (nonDiscCommon == 0) return 0.0

    val maxCount = maxOf(subjectTokens.size, courseTokens.size).toDouble()
    val minCount = minOf(subjectTokens.size, courseTokens.size).toDouble()
    val jaccard = common.size.toDouble() / maxCount
    val coverage = common.size.toDouble() / minCount

    return when {
        nonDiscCommon >= 2 && jaccard >= 0.5 -> 5.0 + jaccard
        nonDiscCommon >= 2 && coverage >= 0.75 -> 4.0 + jaccard
        nonDiscCommon == 1 && subjectTokens.size == 1 && courseTokens.size == 1 -> 6.0
        nonDiscCommon == 1 && coverage >= 1.0 && common.first().length >= 5 -> 4.5
        else -> 0.0
    }
}

private fun sortPendingAssignments(items: List<VirtualAssignmentItem>): List<VirtualAssignmentItem> =
    items.sortedWith(
        compareBy<VirtualAssignmentItem> { it.dueAtMillis <= 0L }
            .thenBy { if (it.dueAtMillis > 0L) it.dueAtMillis else Long.MAX_VALUE }
            .thenBy { it.dueDate.ifBlank { "9999-12-31" } }
            .thenBy { it.name.lowercase() }
    )

fun matchSubjectToVirtual(
    subject: UnifiedSubject,
    virtualOverview: VirtualOverviewResponse?,
    courseDetails: Map<Long, VirtualCourseDetailResponse> = emptyMap()
): SubjectVirtualLink {
    val overview = virtualOverview ?: return SubjectVirtualLink()
    if (overview.courses.isEmpty()) return SubjectVirtualLink()

    val rankedCourses = rankCoursesForMatching(overview.courses)
    val subjectClean = cleanSubjectMatchName(subject.name)
    val subjectTokens = extractSignificantTokens(subjectClean)
    val subjectDiscriminators = extractDiscriminators(subjectClean)

    var bestCourse: VirtualCourseSummary? = null
    var bestScore = 0.0
    for (course in rankedCourses) {
        val score = scoreCourseMatch(
            subjectCode = subject.code,
            subjectClean = subjectClean,
            subjectTokens = subjectTokens,
            subjectDiscriminators = subjectDiscriminators,
            course = course
        )
        if (score > bestScore) {
            bestScore = score
            bestCourse = course
        }
    }

    val matched = bestCourse ?: return SubjectVirtualLink()
    val sourceAssignments = courseDetails[matched.orgUnitId]?.assignments
        ?: overview.upcomingAssignments.filter { it.orgUnitId == matched.orgUnitId }

    val pending = sortPendingAssignments(
        sourceAssignments
            .filter { it.status == "not_submitted" }
            .distinctBy { it.id }
    )
    return SubjectVirtualLink(
        course = matched,
        pendingAssignments = pending
    )
}

fun buildSubjectVirtualLinks(
    subjects: List<UnifiedSubject>,
    virtualOverview: VirtualOverviewResponse?,
    courseDetails: Map<Long, VirtualCourseDetailResponse> = emptyMap()
): Map<String, SubjectVirtualLink> {
    if (subjects.isEmpty() || virtualOverview == null) return emptyMap()
    return subjects.associate { subject ->
        subject.code to matchSubjectToVirtual(subject, virtualOverview, courseDetails)
    }
}

fun collectAllPendingVirtualAssignments(
    virtualOverview: VirtualOverviewResponse?,
    courseDetails: Map<Long, VirtualCourseDetailResponse> = emptyMap()
): List<VirtualAssignmentItem> {
    if (virtualOverview == null && courseDetails.isEmpty()) return emptyList()

    val byKey = linkedMapOf<Pair<Long, Long>, VirtualAssignmentItem>()
    val overviewCourses = virtualOverview?.courses?.associateBy { it.orgUnitId }.orEmpty()

    virtualOverview?.upcomingAssignments.orEmpty().forEach { item ->
        if (item.status == "not_submitted") {
            byKey[item.orgUnitId to item.id] = item
        }
    }

    courseDetails.forEach { (ou, detail) ->
        byKey.keys.removeAll { it.first == ou }
        val courseName = detail.course.name.ifBlank { overviewCourses[ou]?.name.orEmpty() }
        detail.assignments.forEach { item ->
            if (item.status == "not_submitted") {
                val normalized = if (item.orgUnitName.isBlank() && courseName.isNotBlank()) {
                    item.copy(orgUnitName = courseName)
                } else {
                    item
                }
                byKey[ou to item.id] = normalized
            }
        }
    }

    return sortPendingAssignments(byKey.values.toList())
}
