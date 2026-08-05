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
            periods = "",
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
}
