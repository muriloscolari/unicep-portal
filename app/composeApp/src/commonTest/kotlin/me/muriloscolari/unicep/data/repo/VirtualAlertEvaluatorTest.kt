package me.muriloscolari.unicep.data.repo

import me.muriloscolari.unicep.data.api.VirtualAssignmentItem
import me.muriloscolari.unicep.platform.AppTabIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VirtualAlertEvaluatorTest {

    @Test
    fun parseIso8601HandlesOffsetsAndRejectsInvalidDates() {
        val utcMs = parseIso8601ToEpochMillis("2026-11-20T23:59:00Z")
        val brtMs = parseIso8601ToEpochMillis("2026-11-20T20:59:00-03:00")
        assertNotNull(utcMs)
        assertEquals(utcMs, brtMs)

        val withFraction = parseIso8601ToEpochMillis("2026-11-20T23:59:00.123Z")
        assertEquals(utcMs, withFraction)

        assertNotNull(parseIso8601ToEpochMillis("2024-02-29T10:00:00Z"))
        assertNull(parseIso8601ToEpochMillis("2026-02-29T10:00:00Z"))
        assertNull(parseIso8601ToEpochMillis("2026-13-01T10:00:00Z"))
        assertNull(parseIso8601ToEpochMillis("invalid"))
    }

    @Test
    fun formatLeadHoursLabelFormatsDaysAndHours() {
        assertEquals("6h", formatLeadHoursLabel(6))
        assertEquals("24h (1 dia)", formatLeadHoursLabel(24))
        assertEquals("3 dias", formatLeadHoursLabel(72))
        assertEquals("36h", formatLeadHoursLabel(36))
    }

    @Test
    fun evaluateVirtualDeadlineAlertsTriggersTightestBucketAndDeduplicates() {
        val store = mutableMapOf<String, String>()
        val notifications = mutableListOf<Triple<Int, String, Int>>()

        val nowMs = parseIso8601ToEpochMillis("2026-11-18T12:00:00Z")!!
        val due20hMs = nowMs + 20L * 3_600_000L

        val pending = VirtualAssignmentItem(
            id = 101L,
            orgUnitId = 50001L,
            orgUnitName = "DSW",
            name = "Projeto Final",
            dueAtMillis = due20hMs,
            status = "not_submitted"
        )
        val alreadySubmitted = VirtualAssignmentItem(
            id = 102L,
            orgUnitId = 50001L,
            orgUnitName = "DSW",
            name = "Lista 1",
            dueAtMillis = due20hMs,
            status = "submitted"
        )
        val expired = VirtualAssignmentItem(
            id = 103L,
            orgUnitId = 50001L,
            orgUnitName = "DSW",
            name = "Antiga",
            dueAtMillis = nowMs - 3_600_000L,
            status = "not_submitted"
        )

        evaluateVirtualDeadlineAlerts(
            assignments = listOf(pending, alreadySubmitted, expired),
            leadHoursList = listOf(24, 72),
            nowMillis = nowMs,
            storageGet = store::get,
            storagePut = store::put,
            onNotify = { id, title, _, openTab ->
                notifications += Triple(id, title, openTab)
                true
            }
        )

        assertEquals(1, notifications.size)
        assertTrue(notifications[0].second.contains("Projeto Final"))
        assertEquals(AppTabIndex.VIRTUAL, notifications[0].third)

        // Segunda execução no mesmo prazo não deve notificar nem para o bucket 72h já englobado
        evaluateVirtualDeadlineAlerts(
            assignments = listOf(pending),
            leadHoursList = listOf(24, 72),
            nowMillis = nowMs + 60_000L,
            storageGet = store::get,
            storagePut = store::put,
            onNotify = { id, title, _, openTab ->
                notifications += Triple(id, title, openTab)
                true
            }
        )
        assertEquals(1, notifications.size)

        // Quando a atividade é entregue, o estado armazenado é podado
        evaluateVirtualDeadlineAlerts(
            assignments = listOf(pending.copy(status = "submitted")),
            leadHoursList = listOf(24, 72),
            nowMillis = nowMs + 120_000L,
            storageGet = store::get,
            storagePut = store::put,
            onNotify = { _, _, _, _ -> true }
        )
        assertEquals("{}", store[PrefKeys.VIRTUAL_DEADLINE_NOTIFIED])
    }
}
