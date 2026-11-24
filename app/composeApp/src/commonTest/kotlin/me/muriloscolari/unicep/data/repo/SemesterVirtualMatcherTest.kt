package me.muriloscolari.unicep.data.repo

import me.muriloscolari.unicep.data.api.VirtualAssignmentItem
import me.muriloscolari.unicep.data.api.VirtualCourseDetailResponse
import me.muriloscolari.unicep.data.api.VirtualCourseSummary
import me.muriloscolari.unicep.data.api.VirtualOverviewResponse
import me.muriloscolari.unicep.ui.screens.UnifiedSubject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SemesterVirtualMatcherTest {

    @Test
    fun matchesSubjectIgnoringAccentsPrefixesAndPrefersLatestSemester() {
        val overview = VirtualOverviewResponse(
            courses = listOf(
                VirtualCourseSummary(
                    orgUnitId = 101L,
                    name = "2025/2 - Eletronica Analogica - Turma A",
                    semesterName = "2/2025",
                    isActive = false,
                    canAccess = true
                ),
                VirtualCourseSummary(
                    orgUnitId = 202L,
                    name = "2026/2 - Eletrônica Analógica - Turma A",
                    semesterName = "2/2026",
                    isActive = true,
                    canAccess = true
                )
            ),
            upcomingAssignments = listOf(
                VirtualAssignmentItem(
                    id = 1L,
                    orgUnitId = 202L,
                    orgUnitName = "Eletrônica Analógica",
                    name = "Lista 2",
                    dueDate = "2026-11-30T23:59:00Z",
                    dueAtMillis = 2_000L,
                    status = "not_submitted"
                ),
                VirtualAssignmentItem(
                    id = 2L,
                    orgUnitId = 202L,
                    orgUnitName = "Eletrônica Analógica",
                    name = "Lista 1",
                    dueDate = "2026-11-26T23:59:00Z",
                    dueAtMillis = 1_000L,
                    status = "not_submitted"
                ),
                VirtualAssignmentItem(
                    id = 3L,
                    orgUnitId = 202L,
                    orgUnitName = "Eletrônica Analógica",
                    name = "Relatório Enviado",
                    dueAtMillis = 500L,
                    status = "submitted"
                )
            )
        )

        val subject = UnifiedSubject(
            code = "19280403",
            name = "ELETRÔNICA ANALÓGICA",
            grade = null,
            report = null
        )

        val link = matchSubjectToVirtual(subject, overview)
        assertNotNull(link.course)
        assertEquals(202L, link.course.orgUnitId)
        assertEquals(listOf(2L, 1L), link.pendingAssignments.map { it.id })
    }

    @Test
    fun matchesSubjectsWithTrailingSemesterSuffixesAndAbbreviatedNames() {
        val overview = VirtualOverviewResponse(
            courses = listOf(
                VirtualCourseSummary(
                    orgUnitId = 401L,
                    name = "EC - Eletrônica - 2/2026 (Noturno)",
                    semesterName = "2/2026",
                    isActive = true,
                    canAccess = true
                ),
                VirtualCourseSummary(
                    orgUnitId = 402L,
                    name = "DESENVOLVIMENTO WEB - 2/2026",
                    semesterName = "2/2026",
                    isActive = true,
                    canAccess = true
                ),
                VirtualCourseSummary(
                    orgUnitId = 403L,
                    name = "Modelagem de Sistemas (2/2026)",
                    semesterName = "2/2026",
                    isActive = true,
                    canAccess = true
                )
            )
        )

        val eletronica = UnifiedSubject(code = "19280403", name = "ELETRÔNICA", grade = null, report = null)
        val web = UnifiedSubject(code = "19280402", name = "DESENVOLVIMENTO DE SISTEMAS WEB", grade = null, report = null)
        val modelagem = UnifiedSubject(code = "19280404", name = "MODELAGEM DE SISTEMAS", grade = null, report = null)

        assertEquals(401L, matchSubjectToVirtual(eletronica, overview).course?.orgUnitId)
        assertEquals(402L, matchSubjectToVirtual(web, overview).course?.orgUnitId)
        assertEquals(403L, matchSubjectToVirtual(modelagem, overview).course?.orgUnitId)
    }

    @Test
    fun distinguishesRomanNumeralSubjects() {
        val overview = VirtualOverviewResponse(
            courses = listOf(
                VirtualCourseSummary(
                    orgUnitId = 301L,
                    name = "Cálculo Diferencial e Integral II",
                    semesterName = "2/2026",
                    isActive = true,
                    canAccess = true
                ),
                VirtualCourseSummary(
                    orgUnitId = 302L,
                    name = "Cálculo Diferencial e Integral I",
                    semesterName = "2/2026",
                    isActive = true,
                    canAccess = true
                )
            )
        )

        val calc1 = UnifiedSubject(code = "1001", name = "CÁLCULO DIFERENCIAL E INTEGRAL I", grade = null, report = null)
        val calc2 = UnifiedSubject(code = "1002", name = "CÁLCULO DIFERENCIAL E INTEGRAL II", grade = null, report = null)
        val calc3 = UnifiedSubject(code = "1003", name = "CÁLCULO DIFERENCIAL E INTEGRAL III", grade = null, report = null)

        assertEquals(302L, matchSubjectToVirtual(calc1, overview).course?.orgUnitId)
        assertEquals(301L, matchSubjectToVirtual(calc2, overview).course?.orgUnitId)
        assertNull(matchSubjectToVirtual(calc3, overview).course)
    }

    @Test
    fun collectAllPendingReconcilesFreshCourseDetail() {
        val course = VirtualCourseSummary(
            orgUnitId = 500L,
            name = "Sistemas Digitais",
            semesterName = "2/2026",
            isActive = true,
            canAccess = true
        )
        val overview = VirtualOverviewResponse(
            courses = listOf(course),
            upcomingAssignments = listOf(
                VirtualAssignmentItem(id = 10L, orgUnitId = 500L, name = "Lab 1", dueAtMillis = 1000L, status = "not_submitted"),
                VirtualAssignmentItem(id = 20L, orgUnitId = 600L, orgUnitName = "Redes", name = "Trabalho 1", dueAtMillis = 2000L, status = "not_submitted")
            )
        )
        // Fresh detail marks Lab 1 as submitted and adds Lab 2 as pending
        val details = mapOf(
            500L to VirtualCourseDetailResponse(
                course = course,
                assignments = listOf(
                    VirtualAssignmentItem(id = 10L, orgUnitId = 500L, name = "Lab 1", dueAtMillis = 1000L, status = "submitted"),
                    VirtualAssignmentItem(id = 11L, orgUnitId = 500L, name = "Lab 2", dueAtMillis = 1500L, status = "not_submitted")
                )
            )
        )

        val allPending = collectAllPendingVirtualAssignments(overview, details)
        assertEquals(listOf(11L, 20L), allPending.map { it.id })
        assertEquals("Sistemas Digitais", allPending.first().orgUnitName)
    }
}
