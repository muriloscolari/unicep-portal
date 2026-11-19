package me.muriloscolari.unicep.ui.util

import me.muriloscolari.unicep.data.api.PartialGradeItem
import me.muriloscolari.unicep.data.api.ReportCardItem
import me.muriloscolari.unicep.data.api.ReportCardResponse
import me.muriloscolari.unicep.ui.screens.isFrequencyAtLimit
import me.muriloscolari.unicep.ui.screens.mergeUnifiedSubjects
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextAndTimeUtilTest {

    @Test
    fun toDisplayTitlePreservesConnectorsRomansAndAcronyms() {
        assertEquals(
            "Introdução à Engenharia de Software II",
            "INTRODUÇÃO À ENGENHARIA DE SOFTWARE II".toDisplayTitle()
        )
        assertEquals(
            "Banco de Dados SQL e POO",
            "BANCO DE DADOS SQL E POO".toDisplayTitle()
        )
        assertEquals("TCC: Projeto Final (EAD)", "TCC: PROJETO FINAL (EAD)".toDisplayTitle())
    }

    @Test
    fun initialsFromNameHandlesSingleMultiAndEmptyNames() {
        assertEquals("CS", initialsFromName("Carlos Eduardo Silva"))
        assertEquals("CA", initialsFromName("Carlos"))
        assertEquals("AL", initialsFromName("   "))
    }

    @Test
    fun formatRelativeTimeFormatsIntervals() {
        val now = 1_800_000_000_000L // epoch arbitrary reference
        // ISO for now - 30s
        val base = "2026-11-18T12:00:00Z"
        val baseMs = me.muriloscolari.unicep.data.repo.parseIso8601ToEpochMillis(base)!!
        assertEquals("agora", formatRelativeTime(base, nowMillis = baseMs + 30_000L))
        assertEquals("há 15 min", formatRelativeTime(base, nowMillis = baseMs + 15 * 60_000L))
        assertEquals("há 3 h", formatRelativeTime(base, nowMillis = baseMs + 3 * 3_600_000L))
        assertEquals("ontem", formatRelativeTime(base, nowMillis = baseMs + 28 * 3_600_000L))
        assertEquals("há 5 dias", formatRelativeTime(base, nowMillis = baseMs + 5 * 86_400_000L))
        assertEquals("18/11", formatRelativeTime(base, nowMillis = baseMs + 10 * 86_400_000L))
        assertTrue(now > 0L)
    }

    @Test
    fun mergeUnifiedSubjectsAndFrequencyLimitWorkTogether() {
        val grades = listOf(
            PartialGradeItem(code = "101", name = "CÁLCULO I")
        )
        val report = ReportCardResponse(
            items = listOf(
                ReportCardItem(code = "101", name = "CÁLCULO I", hours = 80, absences = 17, maxAbsences = 20, remainingAbsences = 3),
                ReportCardItem(code = "102", name = "FÍSICA I", hours = 80, absences = 4, maxAbsences = 20, remainingAbsences = 16)
            )
        )
        val merged = mergeUnifiedSubjects(grades, report)
        assertEquals(2, merged.size)
        assertTrue(merged[0].report!!.isFrequencyAtLimit())
        assertFalse(merged[1].report!!.isFrequencyAtLimit())
    }
}
