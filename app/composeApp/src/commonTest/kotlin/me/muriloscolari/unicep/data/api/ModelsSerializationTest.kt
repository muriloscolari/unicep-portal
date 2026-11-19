package me.muriloscolari.unicep.data.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelsSerializationTest {

    @Test
    fun virtualOverviewDecodesNullArraysIntoEmptyLists() {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
        val payload = """
            {
              "baseUrl": "https://salavirtual.unicep.edu.br",
              "userId": "10001",
              "userName": "ALUNO EXEMPLO",
              "complete": true,
              "semesters": null,
              "courses": null,
              "upcomingAssignments": null,
              "upcomingQuizzes": null,
              "announcements": null,
              "calendarEvents": null
            }
        """.trimIndent()

        val decoded = json.decodeFromString<VirtualOverviewResponse>(payload)
        assertEquals("10001", decoded.userId)
        assertEquals("ALUNO EXEMPLO", decoded.userName)
        assertTrue(decoded.semesters.isEmpty())
        assertTrue(decoded.courses.isEmpty())
        assertTrue(decoded.upcomingAssignments.isEmpty())
        assertTrue(decoded.upcomingQuizzes.isEmpty())
        assertTrue(decoded.announcements.isEmpty())
        assertTrue(decoded.calendarEvents.isEmpty())
    }
}
