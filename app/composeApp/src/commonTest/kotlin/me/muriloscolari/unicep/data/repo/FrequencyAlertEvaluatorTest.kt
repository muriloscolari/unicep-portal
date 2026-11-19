package me.muriloscolari.unicep.data.repo

import me.muriloscolari.unicep.data.api.ReportCardItem
import me.muriloscolari.unicep.platform.AppTabIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrequencyAlertEvaluatorTest {

    @Test
    fun computeReportFreqPercentUsesExplicitOrFallbackFromAbsences() {
        val explicit = ReportCardItem(code = "A", name = "A", hours = 80, absences = 10, freqPercent = 87.5)
        assertEquals(87.5, computeReportFreqPercent(explicit))

        val computed = ReportCardItem(code = "B", name = "B", hours = 80, absences = 16, freqPercent = null)
        assertEquals(80.0, computeReportFreqPercent(computed))
    }

    @Test
    fun evaluateFrequencyAlertsNotifiesOnDropAndClearsOnRecovery() {
        val store = mutableMapOf<String, String>()
        val fired = mutableListOf<Triple<Int, String, Int>>()

        val at80 = ReportCardItem(
            code = "MAT101",
            name = "Cálculo I",
            hours = 80,
            absences = 16,
            remainingAbsences = 4,
            freqPercent = 80.0
        )

        // Com threshold 0 (desativado), não dispara
        evaluateFrequencyAlerts(
            items = listOf(at80),
            thresholdPercent = 0,
            storageGet = store::get,
            storagePut = store::put,
            onNotify = { id, title, _, tab ->
                fired += Triple(id, title, tab)
                true
            }
        )
        assertEquals(0, fired.size)

        // Com threshold 80, dispara ao atingir 80%
        evaluateFrequencyAlerts(
            items = listOf(at80),
            thresholdPercent = 80,
            storageGet = store::get,
            storagePut = store::put,
            onNotify = { id, title, _, tab ->
                fired += Triple(id, title, tab)
                true
            }
        )
        assertEquals(1, fired.size)
        assertTrue(fired[0].second.contains("80%"))
        assertEquals(AppTabIndex.SEMESTER, fired[0].third)

        // Mesmo valor não re-dispara
        evaluateFrequencyAlerts(
            items = listOf(at80),
            thresholdPercent = 80,
            storageGet = store::get,
            storagePut = store::put,
            onNotify = { id, title, _, tab ->
                fired += Triple(id, title, tab)
                true
            }
        )
        assertEquals(1, fired.size)

        // Se cair ainda mais (77%), dispara novamente
        val at77 = at80.copy(absences = 18, remainingAbsences = 2, freqPercent = 77.0)
        evaluateFrequencyAlerts(
            items = listOf(at77),
            thresholdPercent = 80,
            storageGet = store::get,
            storagePut = store::put,
            onNotify = { id, title, _, tab ->
                fired += Triple(id, title, tab)
                true
            }
        )
        assertEquals(2, fired.size)
        assertTrue(fired[1].second.contains("77%"))

        // Se recuperar acima do limite (85%), limpa o registro para poder alertar no futuro
        val recovered = at80.copy(absences = 12, remainingAbsences = 8, freqPercent = 85.0)
        evaluateFrequencyAlerts(
            items = listOf(recovered),
            thresholdPercent = 80,
            storageGet = store::get,
            storagePut = store::put,
            onNotify = { id, title, _, tab ->
                fired += Triple(id, title, tab)
                true
            }
        )
        assertEquals("{}", store[PrefKeys.FREQ_ALERT_NOTIFIED])
    }
}
