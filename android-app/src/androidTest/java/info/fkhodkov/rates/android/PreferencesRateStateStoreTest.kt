package info.fkhodkov.rates.android

import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreferencesRateStateStoreTest {
    @Test
    fun savesAndLoadsLastResult() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("last_rate_state", 0)
        preferences.edit().clear().commit()
        val store = PreferencesRateStateStore(context)
        val expected = RateUiState(
            mode = CalculationMode.TODAY,
            currency = "EUR",
            startDate = "",
            endDate = "",
            currentResult = CurrentRateUi(LocalDate.of(2026, 8, 5), "93.25"),
        )

        try {
            store.save(expected)

            val restored = store.load()
            assertEquals(expected, restored)
            assertNull(restored?.error)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test
    fun migratesLastPeriodResultToHistory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("last_rate_state", 0)
        preferences.edit().putString("rate_ui_state", """
            {
              "mode": "PERIODS",
              "currency": "USD",
              "periods": "3m",
              "startDate": "",
              "endDate": "2026-08-04",
              "periodResults": [{
                "period": "3m",
                "startDate": "2026-05-04",
                "endDate": "2026-08-04",
                "average": "77.5",
                "observations": 64,
                "firstDate": "2026-05-04",
                "lastDate": "2026-08-04"
              }]
            }
        """.trimIndent()).commit()
        val store = PreferencesRateStateStore(context)

        try {
            val restored = store.load()

            assertEquals(CalculationMode.INTERVAL, restored?.mode)
            assertEquals("2026-05-04", restored?.startDate)
            assertEquals("77.5", restored?.intervalResult?.average)
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
