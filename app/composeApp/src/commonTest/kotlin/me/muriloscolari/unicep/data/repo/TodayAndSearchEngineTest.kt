package me.muriloscolari.unicep.data.repo

import me.muriloscolari.unicep.data.api.CurriculumItem
import me.muriloscolari.unicep.data.api.CurriculumResponse
import me.muriloscolari.unicep.data.api.GradeCell
import me.muriloscolari.unicep.data.api.PartialGradeItem
import me.muriloscolari.unicep.data.api.ReportCardItem
import me.muriloscolari.unicep.data.api.ScheduleResponse
import me.muriloscolari.unicep.data.api.UserCalendarEvent
import me.muriloscolari.unicep.data.api.VirtualAssignmentItem
import me.muriloscolari.unicep.data.api.VirtualOverviewResponse
import me.muriloscolari.unicep.platform.LocalDateTimeInfo
import me.muriloscolari.unicep.ui.screens.UnifiedSubject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TodayAndSearchEngineTest {

    @Test
    fun normalizeSearchTextStripsAccentsAndExtraSpaces() {
        assertEquals("programacao web ii", normalizeSearchText("  Programação   Web   II "))
        assertEquals("calculo integral", normalizeSearchText("CÁLCULO   Integral"))
    }

    @Test
    fun parseScheduleEntriesMatrixMergesConsecutiveSlotsAndMatchesSubjects() {
        val schedule = ScheduleResponse(
            headers = listOf("Horário", "Segunda", "Terça", "Quarta"),
            rows = listOf(
                listOf("19:30 - 20:20", "ENGENHARIA DE SOFTWARE", "", ""),
                listOf("20:20 - 21:10", "ENGENHARIA DE SOFTWARE", "", "REDES DE COMPUTADORES (A Distância)")
            )
        )
        val subjects = listOf(
            UnifiedSubject(
                code = "ES01",
                name = "ENGENHARIA DE SOFTWARE",
                grade = PartialGradeItem(code = "ES01", name = "ENGENHARIA DE SOFTWARE", average = GradeCell("8,5", 8.5)),
                report = ReportCardItem(code = "ES01", name = "ENGENHARIA DE SOFTWARE", hours = 80, absences = 18, maxAbsences = 20, remainingAbsences = 2)
            )
        )

        val entries = parseScheduleEntries(schedule, subjects)
        assertEquals(2, entries.size)
        val eng = entries.first { it.dayOfWeekIso == 1 }
        assertEquals("19:30 - 21:10", eng.timeLabel)
        assertEquals("ES01", eng.matchedSubjectCode)
        assertEquals(2, eng.remainingAbsences)
        assertTrue(eng.isFrequencyAtLimit)

        val redes = entries.first { it.dayOfWeekIso == 3 }
        assertEquals("Modalidade a distância", redes.roomOrMeta)
    }

    @Test
    fun buildTodaySummaryDetectsCurrentNextAndUpcomingDeadlines() {
        val schedule = ScheduleResponse(
            headers = listOf("Dia", "Horário", "Disciplina", "Sala"),
            rows = listOf(
                listOf("Segunda-feira", "19:30 - 21:10", "Banco de Dados", "Lab 3"),
                listOf("Segunda-feira", "21:20 - 22:10", "Sistemas Operacionais", "Sala 12"),
                listOf("Quarta-feira", "19:30 - 21:10", "Redes", "Sala 8")
            )
        )
        val nowMs = 1_795_000_000_000L
        val ov = VirtualOverviewResponse(
            upcomingAssignments = listOf(
                VirtualAssignmentItem(id = 1, orgUnitId = 10, name = "Projeto BD", dueAtMillis = nowMs + 2 * 86_400_000L, status = "not_submitted"),
                VirtualAssignmentItem(id = 2, orgUnitId = 10, name = "Já enviada", dueAtMillis = nowMs + 86_400_000L, status = "submitted"),
                VirtualAssignmentItem(id = 3, orgUnitId = 10, name = "Muito distante", dueAtMillis = nowMs + 15 * 86_400_000L, status = "not_submitted")
            )
        )

        val summary = buildTodaySummary(
            schedule = schedule,
            subjects = emptyList(),
            virtualOverview = ov,
            nowLocal = LocalDateTimeInfo(dayOfWeekIso = 1, hour = 20, minute = 0),
            nowMillis = nowMs
        )

        assertEquals("Segunda-feira", summary.currentDayLabel)
        assertNotNull(summary.currentClass)
        assertEquals("Banco de Dados", summary.currentClass?.subjectName)
        assertNotNull(summary.nextTodayClass)
        assertEquals("Sistemas Operacionais", summary.nextTodayClass?.subjectName)
        assertNull(summary.nextWeekClass)
        assertEquals(1, summary.upcomingDeadlines.size)
        assertEquals("Projeto BD", summary.upcomingDeadlines[0].name)
    }

    @Test
    fun searchPortalDataFindsSubjectsCurriculumAndCalendarEvents() {
        val subjects = listOf(
            UnifiedSubject(
                code = "D1",
                name = "DESENVOLVIMENTO MOBILE",
                grade = PartialGradeItem(code = "D1", name = "DESENVOLVIMENTO MOBILE", average = GradeCell("9,0", 9.0)),
                report = null
            )
        )
        val curriculum = CurriculumResponse(
            matrix = listOf(CurriculumItem(period = "6º Sem", code = "M1", name = "ARQUITETURA DE SOFTWARE", hours = "80"))
        )
        val calendarEvents = listOf(
            UserCalendarEvent(id = 9, title = "Apresentação Mobile", date = "2026-11-25", time = "20:00", reminderMinutes = listOf(60))
        )

        val res = searchPortalData(
            query = "mobile",
            filter = GlobalSearchFilter.ALL,
            subjects = subjects,
            schedule = null,
            curriculum = curriculum,
            virtualOverview = null,
            virtualCourseDetails = emptyMap(),
            calendarEvents = calendarEvents
        )

        assertEquals(2, res.size)
        assertTrue(res.any { it.kind == GlobalSearchResultKind.SUBJECT })
        assertTrue(res.any { it.kind == GlobalSearchResultKind.CALENDAR_EVENT && it.isAlert })
    }
}
