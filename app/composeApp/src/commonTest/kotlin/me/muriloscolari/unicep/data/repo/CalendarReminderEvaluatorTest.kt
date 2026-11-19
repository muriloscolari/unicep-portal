package me.muriloscolari.unicep.data.repo

import me.muriloscolari.unicep.data.api.UserCalendarEvent
import me.muriloscolari.unicep.platform.AppTabIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarReminderEvaluatorTest {

    @Test
    fun computeUpcomingReminderTriggersSchedulesFutureReminders() {
        val eventStartMs = localDateTimeToEpochMillis(CalendarDate(2026, 11, 20), 20, 0, utcOffsetMinutes = -180)
        val nowMs = localDateTimeToEpochMillis(CalendarDate(2026, 11, 18), 12, 0, utcOffsetMinutes = -180)

        val ev = UserCalendarEvent(
            id = 42L,
            title = "Prova P2",
            date = "2026-11-20",
            time = "20:00",
            startAtMillis = eventStartMs,
            disciplineName = "Compiladores",
            reminderMinutes = listOf(0, 60, 1440, 10080) // 1 semana antes já passou!
        )

        val triggers = computeUpcomingReminderTriggers(listOf(ev), nowMillis = nowMs, utcOffsetMinutes = -180)
        assertEquals(3, triggers.size) // 1440 (19/11 20:00), 60 (20/11 19:00), 0 (20/11 20:00)
        assertTrue(triggers[0].title.startsWith("Amanhã:"))
        assertTrue(triggers[1].title.startsWith("Em 1 hora:"))
        assertTrue(triggers[2].title.startsWith("Agora:"))
        assertTrue(triggers[0].triggerAtMillis < triggers[1].triggerAtMillis)
        assertTrue(triggers[1].triggerAtMillis < triggers[2].triggerAtMillis)
    }

    @Test
    fun evaluateCalendarRemindersFiresOnceAndPrunesRemovedEvents() {
        val store = mutableMapOf<String, String>()
        val fired = mutableListOf<Triple<Int, String, Int>>()

        val eventStartMs = localDateTimeToEpochMillis(CalendarDate(2026, 11, 18), 21, 0, utcOffsetMinutes = -180)
        val ev = UserCalendarEvent(
            id = 7L,
            title = "Entrega Semanal",
            date = "2026-11-18",
            time = "21:00",
            startAtMillis = eventStartMs,
            reminderMinutes = listOf(60, 15)
        )

        // Às 20:10 BRT, o lembrete de 60 min (20:00) já venceu e o de 15 min (20:45) ainda não
        val at2010 = localDateTimeToEpochMillis(CalendarDate(2026, 11, 18), 20, 10, utcOffsetMinutes = -180)
        evaluateCalendarReminders(
            events = listOf(ev),
            nowMillis = at2010,
            utcOffsetMinutes = -180,
            storageGet = store::get,
            storagePut = store::put,
            onNotify = { id, title, _, tab ->
                fired += Triple(id, title, tab)
                true
            }
        )
        assertEquals(1, fired.size)
        assertTrue(fired[0].second.contains("Em 1 hora"))
        assertEquals(AppTabIndex.CALENDAR, fired[0].third)

        // Rodar novamente no mesmo instante não pode duplicar
        evaluateCalendarReminders(
            events = listOf(ev),
            nowMillis = at2010,
            utcOffsetMinutes = -180,
            storageGet = store::get,
            storagePut = store::put,
            onNotify = { id, title, _, tab ->
                fired += Triple(id, title, tab)
                true
            }
        )
        assertEquals(1, fired.size)

        // Às 20:46 BRT, o lembrete de 15 min dispara
        val at2046 = localDateTimeToEpochMillis(CalendarDate(2026, 11, 18), 20, 46, utcOffsetMinutes = -180)
        evaluateCalendarReminders(
            events = listOf(ev),
            nowMillis = at2046,
            utcOffsetMinutes = -180,
            storageGet = store::get,
            storagePut = store::put,
            onNotify = { id, title, _, tab ->
                fired += Triple(id, title, tab)
                true
            }
        )
        assertEquals(2, fired.size)
        assertTrue(fired[1].second.contains("Em 15 min"))

        // Ao remover o evento, as chaves antigas devem ser limpas
        evaluateCalendarReminders(
            events = emptyList(),
            nowMillis = at2046,
            utcOffsetMinutes = -180,
            storageGet = store::get,
            storagePut = store::put,
            onNotify = { _, _, _, _ -> true }
        )
        assertEquals("{}", store[PrefKeys.CALENDAR_REMINDERS_NOTIFIED])
    }
}
