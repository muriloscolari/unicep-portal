package me.muriloscolari.unicep.data.repo

import me.muriloscolari.unicep.data.api.VirtualAssignmentItem
import me.muriloscolari.unicep.data.api.VirtualCourseDetailResponse
import me.muriloscolari.unicep.data.api.VirtualCourseSummary
import me.muriloscolari.unicep.data.api.VirtualOverviewResponse
import me.muriloscolari.unicep.data.api.VirtualSemesterOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VirtualOverviewReconcilerTest {

    @Test
    fun normalizeVirtualOverviewForTimeDropsPastDuePendingWithoutTouchingSubmittedOrFutureCourses() {
        val nowMs = parseIso8601ToEpochMillis("2026-11-22T16:26:00-03:00")!!
        val pastDueMs = parseIso8601ToEpochMillis("2026-11-16T23:59:00-03:00")!!
        val earlierSubmittedMs = parseIso8601ToEpochMillis("2026-10-28T19:30:00-03:00")!!
        val futureDueMs = parseIso8601ToEpochMillis("2026-11-28T23:59:00-03:00")!!

        val dswCourse = VirtualCourseSummary(
            orgUnitId = 73611L,
            name = "Desenvolvimento de Sistemas WEB",
            semesterName = "2/2026",
            isActive = true,
            pendingCount = 1,
            assignmentCount = 2
        )
        val modelCourse = VirtualCourseSummary(
            orgUnitId = 73612L,
            name = "Modelagem de Sistemas",
            semesterName = "2/2026",
            isActive = true,
            pendingCount = 1,
            assignmentCount = 1
        )

        val ov = VirtualOverviewResponse(
            semesters = listOf(VirtualSemesterOption(id = "48648", name = "2/2026")),
            courses = listOf(dswCourse, modelCourse),
            upcomingAssignments = listOf(
                VirtualAssignmentItem(
                    id = 101L,
                    orgUnitId = 73611L,
                    orgUnitName = dswCourse.name,
                    name = "ATIVIDADE- CAMADAS PARA WEB",
                    dueAtMillis = pastDueMs,
                    status = "not_submitted",
                    statusLabel = "Não enviado"
                ),
                VirtualAssignmentItem(
                    id = 100L,
                    orgUnitId = 73611L,
                    orgUnitName = dswCourse.name,
                    name = "Atividade avaliativa complementar 01",
                    dueAtMillis = earlierSubmittedMs,
                    status = "submitted",
                    statusLabel = "Enviado"
                ),
                VirtualAssignmentItem(
                    id = 200L,
                    orgUnitId = 73612L,
                    orgUnitName = modelCourse.name,
                    name = "Diagrama de Sequência",
                    dueAtMillis = futureDueMs,
                    status = "not_submitted",
                    statusLabel = "Não enviado"
                )
            )
        )

        val normalized = normalizeVirtualOverviewForTime(ov, nowMs)
        val updatedDsw = normalized.courses.first { it.orgUnitId == 73611L }
        val updatedModel = normalized.courses.first { it.orgUnitId == 73612L }

        assertEquals(0, updatedDsw.pendingCount)
        assertEquals(1, updatedDsw.assignmentCount)
        assertEquals(1, updatedModel.pendingCount)
        assertEquals(1, updatedModel.assignmentCount)

        assertEquals(2, normalized.upcomingAssignments.size)
        assertFalse(normalized.upcomingAssignments.any { it.id == 101L })
        assertTrue(normalized.upcomingAssignments.any { it.id == 100L && it.status == "submitted" })
        assertTrue(normalized.upcomingAssignments.any { it.id == 200L && it.status == "not_submitted" })
    }

    @Test
    fun reconcileVirtualOverviewWithCourseDetailClearsStalePendingAndIgnoresOldSemester() {
        val nowMs = parseIso8601ToEpochMillis("2026-11-22T16:26:00-03:00")!!
        val pastDue1Ms = parseIso8601ToEpochMillis("2026-10-28T19:30:00-03:00")!!
        val pastDue2Ms = parseIso8601ToEpochMillis("2026-11-16T23:59:00-03:00")!!
        val futureMs = parseIso8601ToEpochMillis("2026-11-29T23:59:00-03:00")!!

        val dswCourse = VirtualCourseSummary(
            orgUnitId = 73611L,
            name = "Desenvolvimento de Sistemas WEB - 4° Termo - 2/2026",
            semesterName = "2/2026",
            isActive = true,
            canAccess = true,
            pendingCount = 1,
            assignmentCount = 2
        )
        val modelCourse = VirtualCourseSummary(
            orgUnitId = 73612L,
            name = "Modelagem de Sistemas",
            semesterName = "2/2026",
            isActive = true,
            canAccess = true,
            pendingCount = 0,
            assignmentCount = 1
        )
        val oldCourse = VirtualCourseSummary(
            orgUnitId = 50001L,
            name = "Mcti - 1/2026",
            semesterName = "1/2026",
            isActive = true,
            canAccess = true,
            pendingCount = 0,
            assignmentCount = 0
        )

        val staleOverview = VirtualOverviewResponse(
            semesters = listOf(
                VirtualSemesterOption(id = "48648", name = "2/2026"),
                VirtualSemesterOption(id = "40000", name = "1/2026")
            ),
            courses = listOf(dswCourse, modelCourse, oldCourse),
            upcomingAssignments = listOf(
                VirtualAssignmentItem(
                    id = 2L,
                    orgUnitId = 73611L,
                    orgUnitName = dswCourse.name,
                    name = "ATIVIDADE- CAMADAS PARA WEB",
                    dueDate = "2026-11-16T23:59:00-03:00",
                    dueAtMillis = pastDue2Ms,
                    status = "not_submitted",
                    statusLabel = "Não enviado"
                ),
                VirtualAssignmentItem(
                    id = 1L,
                    orgUnitId = 73611L,
                    orgUnitName = dswCourse.name,
                    name = "Atividade avaliativa complementar 01",
                    dueDate = "2026-10-28T19:30:00-03:00",
                    dueAtMillis = pastDue1Ms,
                    status = "submitted",
                    statusLabel = "Enviado"
                ),
                VirtualAssignmentItem(
                    id = 30L,
                    orgUnitId = 73612L,
                    orgUnitName = modelCourse.name,
                    name = "Caso de Uso",
                    dueDate = "2026-11-29T23:59:00-03:00",
                    dueAtMillis = futureMs,
                    status = "submitted",
                    statusLabel = "Enviado"
                )
            )
        )

        val freshDswDetail = VirtualCourseDetailResponse(
            course = dswCourse.copy(pendingCount = 0, assignmentCount = 2),
            assignments = listOf(
                VirtualAssignmentItem(
                    id = 1L,
                    orgUnitId = 73611L,
                    orgUnitName = dswCourse.name,
                    name = "Atividade avaliativa complementar 01",
                    dueDate = "2026-10-28T19:30:00-03:00",
                    dueAtMillis = pastDue1Ms,
                    status = "submitted",
                    statusLabel = "Enviado"
                ),
                VirtualAssignmentItem(
                    id = 2L,
                    orgUnitId = 73611L,
                    orgUnitName = dswCourse.name,
                    name = "ATIVIDADE- CAMADAS PARA WEB",
                    dueDate = "2026-11-16T23:59:00-03:00",
                    dueAtMillis = pastDue2Ms,
                    status = "submitted",
                    statusLabel = "Enviado"
                )
            )
        )

        val reconciled = reconcileVirtualOverviewWithCourseDetail(staleOverview, freshDswDetail, nowMs)
        val reconciledDsw = reconciled.courses.first { it.orgUnitId == 73611L }
        assertEquals(0, reconciledDsw.pendingCount)
        assertEquals(2, reconciledDsw.assignmentCount)
        assertEquals(0, reconciled.upcomingAssignments.count { it.status == "not_submitted" })
        assertEquals(3, reconciled.upcomingAssignments.size)
        assertTrue(reconciled.upcomingAssignments.any { it.orgUnitId == 73611L && it.id == 2L && it.status == "submitted" })
        assertTrue(reconciled.upcomingAssignments.any { it.orgUnitId == 73612L && it.id == 30L })

        // Abrir uma disciplina de semestre anterior (1/2026) não deve vazar atividades para o resumo atual (2/2026)
        val oldCourseDetail = VirtualCourseDetailResponse(
            course = oldCourse.copy(pendingCount = 1, assignmentCount = 1),
            assignments = listOf(
                VirtualAssignmentItem(
                    id = 999L,
                    orgUnitId = 50001L,
                    orgUnitName = oldCourse.name,
                    name = "Atividade Antiga",
                    dueDate = "2026-11-30T23:59:00-03:00",
                    dueAtMillis = futureMs + 86_400_000L,
                    status = "not_submitted",
                    statusLabel = "Não enviado"
                )
            )
        )
        val afterOld = reconcileVirtualOverviewWithCourseDetail(reconciled, oldCourseDetail, nowMs)
        assertFalse(afterOld.upcomingAssignments.any { it.orgUnitId == 50001L })
        assertEquals(0, afterOld.courses.first { it.orgUnitId == 50001L }.pendingCount)
    }
}
