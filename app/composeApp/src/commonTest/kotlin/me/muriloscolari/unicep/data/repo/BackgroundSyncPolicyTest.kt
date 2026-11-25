package me.muriloscolari.unicep.data.repo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BackgroundSyncPolicyTest {

    @Test
    fun normalizeClampsToSupportedPresets() {
        assertEquals(0, normalizeBackgroundSyncIntervalMinutes(-10))
        assertEquals(0, normalizeBackgroundSyncIntervalMinutes(0))
        assertEquals(30, normalizeBackgroundSyncIntervalMinutes(15))
        assertEquals(30, normalizeBackgroundSyncIntervalMinutes(30))
        assertEquals(60, normalizeBackgroundSyncIntervalMinutes(45))
        assertEquals(60, normalizeBackgroundSyncIntervalMinutes(60))
        assertEquals(120, normalizeBackgroundSyncIntervalMinutes(90))
        assertEquals(120, normalizeBackgroundSyncIntervalMinutes(240))
    }

    @Test
    fun readAndSaveIntervalRoundTripsAndDefaultsTo60Minutes() {
        val store = mutableMapOf<String, String>()
        assertEquals(DEFAULT_BG_SYNC_INTERVAL_MINUTES, readBackgroundSyncIntervalMinutes(store::get))

        val saved = saveBackgroundSyncIntervalMinutes(120) { k, v -> store[k] = v }
        assertEquals(120, saved)
        assertEquals(120, readBackgroundSyncIntervalMinutes(store::get))

        val disabled = saveBackgroundSyncIntervalMinutes(0) { k, v -> store[k] = v }
        assertEquals(0, disabled)
        assertEquals(0, readBackgroundSyncIntervalMinutes(store::get))
    }

    @Test
    fun flexIntervalMatchesWorkManagerBatchingWindows() {
        assertEquals(0L, computeFlexIntervalMinutes(0))
        assertEquals(10L, computeFlexIntervalMinutes(30))
        assertEquals(15L, computeFlexIntervalMinutes(60))
        assertEquals(30L, computeFlexIntervalMinutes(120))
    }

    @Test
    fun quietHoursCoverOvernightWindowOnly() {
        for (h in 0..6) {
            assertTrue(isQuietHoursForFullSync(h), "Expected hour $h to be quiet")
        }
        for (h in 7..23) {
            assertFalse(isQuietHoursForFullSync(h), "Expected hour $h not to be quiet")
        }
    }

    @Test
    fun calendarTriggerSignatureDetectsChangesAndStaysStable() {
        assertEquals("", buildCalendarTriggerSignature(emptyList()))

        val a = ScheduledCalendarReminder(
            eventId = 1L,
            reminderKey = "ev1_60",
            notifId = 90001,
            triggerAtMillis = 1_700_000_000_000L,
            title = "Prova P1",
            body = "Em 1 hora"
        )
        val b = a.copy(triggerAtMillis = 1_700_000_060_000L)

        assertEquals(buildCalendarTriggerSignature(listOf(a)), buildCalendarTriggerSignature(listOf(a)))
        assertNotEquals(buildCalendarTriggerSignature(listOf(a)), buildCalendarTriggerSignature(listOf(b)))
    }

    @Test
    fun formatLabelsAreHumanReadable() {
        assertEquals("Manual", formatBackgroundSyncIntervalLabel(0))
        assertEquals("30 min", formatBackgroundSyncIntervalLabel(30))
        assertEquals("1 hora", formatBackgroundSyncIntervalLabel(60))
        assertEquals("2 horas", formatBackgroundSyncIntervalLabel(120))
    }
}
