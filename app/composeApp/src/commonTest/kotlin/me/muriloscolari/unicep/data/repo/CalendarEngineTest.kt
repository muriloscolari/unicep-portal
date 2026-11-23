package me.muriloscolari.unicep.data.repo

import me.muriloscolari.unicep.data.api.UserCalendarEvent
import me.muriloscolari.unicep.data.api.VirtualAssignmentItem
import me.muriloscolari.unicep.data.api.VirtualCalendarEvent
import me.muriloscolari.unicep.data.api.VirtualCourseDetailResponse
import me.muriloscolari.unicep.data.api.VirtualCourseSummary
import me.muriloscolari.unicep.data.api.VirtualOverviewResponse
import me.muriloscolari.unicep.data.api.VirtualQuizItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarEngineTest {

    @Test
    fun leapYearsAndDaysInMonthAreAccurate() {
        assertTrue(isLeapYear(2024))
        assertTrue(isLeapYear(2000))
        assertFalse(isLeapYear(1900))
        assertFalse(isLeapYear(2026))
        assertEquals(29, daysInMonth(2024, 2))
        assertEquals(28, daysInMonth(2026, 2))
        assertEquals(30, daysInMonth(2026, 11))
        assertEquals(31, daysInMonth(2026, 12))
    }

    @Test
    fun civilDaysAndEpochConversionsRoundTripInBrt() {
        val epochDay = CalendarDate(1970, 1, 1)
        assertEquals(0L, daysFromCivil(1970, 1, 1))
        assertEquals(epochDay, civilFromDays(0L))
        assertEquals(4, dayOfWeekIso(epochDay)) // Quinta-feira
        assertEquals(4, dayOfWeekSundayFirst(epochDay))

        // 2026-11-18 é Quarta-feira (ISO 3)
        val nov18 = CalendarDate(2026, 11, 18)
        assertEquals(3, dayOfWeekIso(nov18))
        assertEquals(3, dayOfWeekSundayFirst(nov18))

        // 2026-11-18 23:59 BRT (-180) -> 2026-11-19 02:59 UTC
        val utcMs = localDateTimeToEpochMillis(nov18, 23, 59, utcOffsetMinutes = -180)
        assertEquals(nov18, epochMillisToLocalDate(utcMs, utcOffsetMinutes = -180))
        assertEquals("23:59", epochMillisToLocalTime(utcMs, utcOffsetMinutes = -180))
        assertEquals(CalendarDate(2026, 11, 19), epochMillisToLocalDate(utcMs, utcOffsetMinutes = 0))
    }

    @Test
    fun parseAndFormatDatesValidateBoundaries() {
        assertEquals(CalendarDate(2026, 11, 18), parseIsoDate("2026-11-18"))
        assertEquals(CalendarDate(2026, 11, 18), parseBrDate("18/11/2026"))
        assertEquals("2026-11-18", formatIsoDate(CalendarDate(2026, 11, 18)))
        assertEquals("18/11/2026", formatBrDate(CalendarDate(2026, 11, 18)))

        assertNull(parseIsoDate("2026-02-29"))
        assertNotNull(parseIsoDate("2024-02-29"))
        assertNull(parseBrDate("31/04/2026"))
        assertNull(parseHourMinute("25:00"))
        assertEquals(19 to 30, parseHourMinute("19:30"))
    }

    @Test
    fun buildMonthGridStartsSundayAndPadsFullWeeks() {
        // Novembro de 2026 começa no domingo (dayOfWeekSundayFirst == 0), 30 dias -> 35 células
        val novGrid = buildMonthGrid(2026, 11)
        assertEquals(35, novGrid.size)
        assertEquals(CalendarDate(2026, 11, 1), novGrid.first())
        assertEquals(CalendarDate(2026, 11, 30), novGrid[29])
        assertNull(novGrid[30])

        // Outubro de 2026 começa na quinta-feira (4 nulls antes do dia 1)
        val octGrid = buildMonthGrid(2026, 10)
        assertTrue(octGrid.size % 7 == 0)
        assertNull(octGrid[0])
        assertNull(octGrid[3])
        assertEquals(CalendarDate(2026, 10, 1), octGrid[4])

        assertEquals(2025 to 12, previousMonth(2026, 1))
        assertEquals(2027 to 1, nextMonth(2026, 12))
    }

    @Test
    fun buildCalendarAgendaItemsMergesAndDeduplicatesSources() {
        val nov20 = CalendarDate(2026, 11, 20)
        val due20UtcMs = localDateTimeToEpochMillis(nov20, 23, 59, utcOffsetMinutes = -180)

        val userEvent = UserCalendarEvent(
            id = 10L,
            title = "Revisar Engenharia",
            date = "2026-11-20",
            time = "18:00",
            allDay = false,
            disciplineName = "ENGENHARIA DE SOFTWARE",
            colorKey = "green",
            reminderMinutes = listOf(60, 1440)
        )

        val assignment = VirtualAssignmentItem(
            id = 55L,
            orgUnitId = 50001L,
            orgUnitName = "DESENVOLVIMENTO WEB",
            name = "Entrega Trabalho 1",
            dueDate = "2026-11-21T02:59:00Z", // 23:59 BRT on 20/11
            dueAtMillis = due20UtcMs,
            status = "not_submitted",
            statusLabel = "Não enviado"
        )

        val quiz = VirtualQuizItem(
            quizId = 77L,
            orgUnitId = 50001L,
            orgUnitName = "DESENVOLVIMENTO WEB",
            name = "Questionário P2",
            isActive = true,
            dueDate = "2026-11-22T23:59:00-03:00"
        )

        // Evento de calendário D2L duplicado da mesma atividade no mesmo dia deve ser deduplicado
        val duplicateD2LEvent = VirtualCalendarEvent(
            id = 900L,
            orgUnitId = 50001L,
            orgUnitName = "DESENVOLVIMENTO WEB",
            title = "Entrega Trabalho 1 - Prazo",
            startDateTime = "2026-11-21T02:59:00Z"
        )
        // Evento distinto do D2L deve ser mantido
        val distinctD2LEvent = VirtualCalendarEvent(
            id = 901L,
            orgUnitId = 50001L,
            orgUnitName = "DESENVOLVIMENTO WEB",
            title = "Aula síncrona de revisão",
            startDateTime = "2026-11-25T19:30:00-03:00"
        )

        val overview = VirtualOverviewResponse(
            upcomingAssignments = listOf(assignment),
            upcomingQuizzes = listOf(quiz),
            calendarEvents = listOf(duplicateD2LEvent, distinctD2LEvent)
        )
        val details = mapOf(
            50001L to VirtualCourseDetailResponse(
                course = VirtualCourseSummary(orgUnitId = 50001L, name = "DESENVOLVIMENTO WEB"),
                assignments = listOf(assignment), // duplicado entre overview e detail
                quizzes = listOf(quiz)
            )
        )

        val all = buildCalendarAgendaItems(
            userEvents = listOf(userEvent),
            virtualOverview = overview,
            virtualCourseDetails = details,
            utcOffsetMinutes = -180
        )

        assertEquals(4, all.size)
        assertEquals(CalendarItemSource.PERSONAL, all[0].source)
        assertEquals(CalendarDate(2026, 11, 20), all[0].date)
        assertEquals(CalendarItemSource.VIRTUAL_ASSIGNMENT, all[1].source)
        assertEquals(CalendarDate(2026, 11, 20), all[1].date)
        assertEquals("23:59", all[1].timeLabel)
        assertEquals(CalendarItemSource.VIRTUAL_QUIZ, all[2].source)
        assertEquals(CalendarDate(2026, 11, 22), all[2].date)
        assertEquals(CalendarItemSource.VIRTUAL_EVENT, all[3].source)
        assertEquals(CalendarDate(2026, 11, 25), all[3].date)

        assertEquals(3, filterCalendarItems(all, CalendarFilter.VIRTUAL).size)
        assertEquals(1, filterCalendarItems(all, CalendarFilter.PERSONAL).size)

        val grouped = groupCalendarItemsByDate(all)
        assertEquals(2, grouped[nov20]?.size)

        val upcoming = upcomingCalendarItems(all, fromDate = CalendarDate(2026, 11, 18), days = 14)
        assertEquals(4, upcoming.size)
        val onlyNext3Days = upcomingCalendarItems(all, fromDate = CalendarDate(2026, 11, 18), days = 3)
        assertEquals(2, onlyNext3Days.size)
    }

    @Test
    fun buildCalendarSubjectSuggestionsDeduplicatesD2LSuffixesWithoutCollidingDistinctCourses() {
        val suggestions = buildCalendarSubjectSuggestions(
            gradeNames = listOf(
                "Eletrônica",
                "Cálculo I",
                "Sistemas Operacionais",
                "Desenvolvimento de Sistemas WEB"
            ),
            virtualCourseNames = listOf(
                "Eletrônica - 4° Termo - 2/2026 - Turma A - Ecomp. Not.",
                "Sistemas Operacionais - 4° Termo - 2/2026 - Turma A",
                "Cálculo II - 4° Termo - 2/2026",
                "Ehser - 2/2026 - Adm, Biom, Ccont..."
            )
        )

        assertEquals(
            listOf(
                "Eletrônica",
                "Cálculo I",
                "Sistemas Operacionais",
                "Desenvolvimento de Sistemas WEB",
                "Cálculo II - 4° Termo - 2/2026",
                "Ehser - 2/2026 - Adm, Biom, Ccont..."
            ),
            suggestions
        )
    }

    @Test
    fun filterSubjectSuggestionsFiltersDynamicallyAndRanksMatches() {
        val suggestions = listOf(
            "Atividades Complementares I",
            "Desenvolvimento de Sistemas WEB",
            "Eletrônica",
            "Modelagem de Sistemas",
            "Sistemas Operacionais",
            "Ehser - 2/2026 - Adm, Biom"
        )

        assertEquals(suggestions, filterSubjectSuggestions(suggestions, ""))
        assertEquals(suggestions, filterSubjectSuggestions(suggestions, "   "))

        // Sem acento deve encontrar "Eletrônica" e remover todas as demais
        assertEquals(listOf("Eletrônica"), filterSubjectSuggestions(suggestions, "Eletro"))

        // Busca com múltiplos tokens deve filtrar apenas a disciplina correspondente
        assertEquals(
            listOf("Desenvolvimento de Sistemas WEB"),
            filterSubjectSuggestions(suggestions, "sist web")
        )

        // Ordenação por relevância: exato > prefixo > substring > tokens
        val ranked = filterSubjectSuggestions(
            listOf(
                "Microeletrônica Aplicada",
                "Eletrônica",
                "Eletrônica Digital"
            ),
            "eletronica"
        )
        assertEquals(
            listOf(
                "Eletrônica",
                "Eletrônica Digital",
                "Microeletrônica Aplicada"
            ),
            ranked
        )

        // Sem correspondência retorna lista vazia
        assertTrue(filterSubjectSuggestions(suggestions, "quimica").isEmpty())
    }

    @Test
    fun buildCalendarAgendaItemsPrefersFreshCourseDetailAssignmentOverStaleOverview() {
        val nov20 = CalendarDate(2026, 11, 20)
        val dueMs = localDateTimeToEpochMillis(nov20, 23, 59, utcOffsetMinutes = -180)
        val stale = VirtualAssignmentItem(
            id = 2L,
            orgUnitId = 73611L,
            orgUnitName = "DESENVOLVIMENTO WEB",
            name = "Camadas WEB",
            dueDate = "2026-11-21T02:59:00Z",
            dueAtMillis = dueMs,
            status = "not_submitted",
            statusLabel = "Não enviado"
        )
        val fresh = stale.copy(status = "submitted", statusLabel = "Enviado")
        val items = buildCalendarAgendaItems(
            userEvents = emptyList(),
            virtualOverview = VirtualOverviewResponse(upcomingAssignments = listOf(stale)),
            virtualCourseDetails = mapOf(
                73611L to VirtualCourseDetailResponse(
                    course = VirtualCourseSummary(orgUnitId = 73611L, name = "DESENVOLVIMENTO WEB"),
                    assignments = listOf(fresh)
                )
            ),
            utcOffsetMinutes = -180
        )

        assertEquals(1, items.size)
        assertEquals("Enviado", items[0].statusBadge)
        assertEquals("green", items[0].colorKey)
    }
}
