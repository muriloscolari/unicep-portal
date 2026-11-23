package me.muriloscolari.unicep.data.repo

import me.muriloscolari.unicep.data.api.VirtualAssignmentItem
import me.muriloscolari.unicep.data.api.VirtualCourseDetailResponse
import me.muriloscolari.unicep.data.api.VirtualCourseSummary
import me.muriloscolari.unicep.data.api.VirtualOverviewResponse
import me.muriloscolari.unicep.data.api.VirtualSemesterOption

private fun resolveEffectiveDeadlineMillis(item: VirtualAssignmentItem): Long =
    resolveAssignmentDueAtMillis(item).takeIf { it > 0L }
        ?: parseIso8601ToEpochMillis(item.endDate)
        ?: 0L

private fun isExpiredAt(item: VirtualAssignmentItem, nowMillis: Long): Boolean {
    if (item.status == "expired") return true
    if (item.status != "not_submitted" || nowMillis <= 0L) return false
    val deadlineMs = resolveEffectiveDeadlineMillis(item)
    return deadlineMs > 0L && deadlineMs < nowMillis
}

private fun statusPriorityRank(status: String): Int = when (status) {
    "not_submitted" -> 0
    "submitted" -> 1
    "graded" -> 2
    "expired" -> 3
    else -> 4
}

private val assignmentPriorityComparator = Comparator<VirtualAssignmentItem> { a, b ->
    val rankCmp = statusPriorityRank(a.status).compareTo(statusPriorityRank(b.status))
    if (rankCmp != 0) return@Comparator rankCmp

    val aDue = a.dueDate.trim().ifBlank { a.endDate.trim() }
    val bDue = b.dueDate.trim().ifBlank { b.endDate.trim() }
    if (aDue != bDue) {
        if (aDue.isEmpty()) return@Comparator 1
        if (bDue.isEmpty()) return@Comparator -1
        return@Comparator aDue.compareTo(bDue)
    }

    a.name.compareTo(b.name)
}

private fun semesterSortKey(name: String): String {
    val parts = name.split('/')
    if (parts.size == 2) {
        val semStr = parts[0].trim()
        val yearStr = parts[1].trim()
        val semNum = semStr.toIntOrNull()
        if (semNum != null) {
            return "$yearStr-${semNum.toString().padStart(2, '0')}"
        }
        return "$yearStr-$semStr"
    }
    return name
}

private fun pickLatestSemesterName(
    sems: List<VirtualSemesterOption>,
    courses: List<VirtualCourseSummary>
): String {
    var bestName = ""
    var bestKey = ""
    fun consider(raw: String) {
        val name = raw.trim()
        if (name.isEmpty()) return
        val key = semesterSortKey(name)
        if (key > bestKey) {
            bestKey = key
            bestName = name
        }
    }
    courses.forEach { c ->
        if (c.canAccess) consider(c.semesterName)
    }
    if (bestName.isNotEmpty()) return bestName
    sems.forEach { s -> consider(s.name) }
    return bestName
}

private fun selectActiveCourseIds(
    sems: List<VirtualSemesterOption>,
    courses: List<VirtualCourseSummary>
): Set<Long> {
    val targetSem = pickLatestSemesterName(sems, courses)
    val active = linkedSetOf<Long>()
    courses.forEach { c ->
        if (!c.canAccess) return@forEach
        val sem = c.semesterName.trim()
        val isCurrentSem = (targetSem.isNotEmpty() && sem == targetSem) ||
            (targetSem.isEmpty() && c.isActive) ||
            (sem.isEmpty() && c.isActive)
        if (isCurrentSem) {
            active.add(c.orgUnitId)
        }
    }
    if (active.isEmpty()) {
        courses.forEach { c ->
            if (c.canAccess && c.isActive) active.add(c.orgUnitId)
        }
    }
    if (active.isEmpty()) {
        courses.forEach { c ->
            if (c.canAccess) active.add(c.orgUnitId)
        }
    }
    return active
}

private fun isCourseInOverviewScope(
    overview: VirtualOverviewResponse,
    courseId: Long
): Boolean {
    if (courseId in selectActiveCourseIds(overview.semesters, overview.courses)) return true
    if (overview.upcomingAssignments.any { it.orgUnitId == courseId }) return true
    return overview.courses.any { it.orgUnitId == courseId && (it.assignmentCount > 0 || it.pendingCount > 0) }
}

fun normalizeVirtualOverviewForTime(
    overview: VirtualOverviewResponse,
    nowMillis: Long
): VirtualOverviewResponse {
    if (overview.upcomingAssignments.isEmpty()) return overview

    val affectedCourseIds = mutableSetOf<Long>()
    val remaining = ArrayList<VirtualAssignmentItem>(overview.upcomingAssignments.size)
    for (item in overview.upcomingAssignments) {
        if (isExpiredAt(item, nowMillis)) {
            affectedCourseIds.add(item.orgUnitId)
        } else {
            remaining.add(item)
        }
    }
    if (affectedCourseIds.isEmpty()) return overview

    val byCourse = remaining.groupBy { it.orgUnitId }
    val updatedCourses = overview.courses.map { course ->
        if (course.orgUnitId !in affectedCourseIds) {
            course
        } else {
            val courseAssigns = byCourse[course.orgUnitId].orEmpty()
            course.copy(
                assignmentCount = courseAssigns.size,
                pendingCount = courseAssigns.count { it.status == "not_submitted" }
            )
        }
    }

    return overview.copy(
        courses = updatedCourses,
        upcomingAssignments = remaining
    )
}

fun reconcileVirtualOverviewWithCourseDetail(
    overview: VirtualOverviewResponse,
    detail: VirtualCourseDetailResponse,
    nowMillis: Long
): VirtualOverviewResponse {
    val base = normalizeVirtualOverviewForTime(overview, nowMillis)
    val courseId = detail.course.orgUnitId
    if (!isCourseInOverviewScope(base, courseId)) {
        return base
    }

    val normalizedDetailAssigns = detail.assignments.map { item ->
        val resolvedOrgId = if (item.orgUnitId != 0L) item.orgUnitId else courseId
        val resolvedOrgName = item.orgUnitName.ifBlank { detail.course.name }
        val deadlineMs = resolveEffectiveDeadlineMillis(item)
        if (item.status == "not_submitted" && nowMillis > 0L && deadlineMs > 0L && deadlineMs < nowMillis) {
            item.copy(
                orgUnitId = resolvedOrgId,
                orgUnitName = resolvedOrgName,
                status = "expired",
                statusLabel = "Encerrada",
                dueAtMillis = if (item.dueAtMillis > 0L) item.dueAtMillis else deadlineMs
            )
        } else {
            item.copy(
                orgUnitId = resolvedOrgId,
                orgUnitName = resolvedOrgName
            )
        }
    }

    val visibleCourseAssignments = normalizedDetailAssigns.filter { it.status != "expired" }
    val newPendingCount = visibleCourseAssignments.count { it.status == "not_submitted" }
    val newAssignmentCount = visibleCourseAssignments.size

    val updatedCourses = base.courses.map { course ->
        if (course.orgUnitId == courseId) {
            course.copy(
                pendingCount = newPendingCount,
                assignmentCount = newAssignmentCount
            )
        } else {
            course
        }
    }

    val mergedAssignments = (
        base.upcomingAssignments.filter { it.orgUnitId != courseId } + visibleCourseAssignments
        ).sortedWith(assignmentPriorityComparator)

    return base.copy(
        courses = updatedCourses,
        upcomingAssignments = mergedAssignments
    )
}
