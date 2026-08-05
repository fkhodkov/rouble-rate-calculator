package info.fkhodkov.rates.android

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import info.fkhodkov.rates.core.CurrentRate
import info.fkhodkov.rates.core.DateRange
import info.fkhodkov.rates.core.ExchangeRateCalculator
import info.fkhodkov.rates.core.ExchangeRateSource
import info.fkhodkov.rates.core.ExchangeRateStore
import info.fkhodkov.rates.core.Rate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class RateScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val clock = Clock.fixed(
        Instant.parse("2026-08-05T08:00:00Z"),
        ZoneId.of("Europe/Moscow"),
    )

    @Test
    fun intervalModeShowsValidationErrorForConflictingInputs() {
        val viewModel = viewModel()
        show(viewModel)
        compose.waitUntil { !viewModel.state.value.loading }

        compose.onNodeWithText("Interval").performClick()
        compose.onNode(hasSetTextAction() and hasText("Start date"))
            .performTextReplacement("2026-08-01")
        compose.onNode(hasSetTextAction() and hasText("End date (optional)"))
            .performTextReplacement("2026-08-04")
        compose.onNode(hasSetTextAction() and hasText("Period (optional)"))
            .performTextReplacement("1w")
        compose.onNodeWithText("Calculate").performClick()

        compose.onNodeWithText("Start date, end date, and period cannot be used together.")
            .assertIsDisplayed()
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun todayModeDisplaysCurrentRate() {
        val viewModel = viewModel()
        show(viewModel)
        compose.waitUntil { !viewModel.state.value.loading }

        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithText("Calculate").performClick()
        compose.waitUntil { !viewModel.state.value.loading }

        compose.onNodeWithText("Effective Aug 5, 2026").assertIsDisplayed()
        compose.onNodeWithText("78.5 RUB").assertIsDisplayed()
        compose.onNodeWithText("per 1 USD").assertIsDisplayed()
        compose.onNodeWithText("Refresh").assertIsDisplayed()
    }

    @Test
    fun displaysRussianTranslation() {
        val viewModel = viewModel()
        show(viewModel, Locale.forLanguageTag("ru"))
        compose.waitUntil { !viewModel.state.value.loading }

        compose.onNodeWithText("Калькулятор курса рубля").assertIsDisplayed()
        compose.onAllNodesWithText("Периоды").assertCountEquals(2)
        compose.onNodeWithText("Интервал").assertIsDisplayed()
        compose.onNodeWithText("Сегодня").assertIsDisplayed()
        compose.onNodeWithText("Обновить").assertIsDisplayed()
    }

    private fun show(viewModel: RateViewModel, locale: Locale = Locale.ENGLISH) {
        val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(baseContext.resources.configuration).apply {
            setLocale(locale)
        }
        val localizedContext = baseContext.createConfigurationContext(configuration)
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
            ) {
                MaterialTheme {
                    RateScreen(calculator(), rateViewModel = viewModel)
                }
            }
        }
    }

    private fun viewModel() = RateViewModel(calculator(), clock)

    private fun calculator() = ExchangeRateCalculator(
        clock,
        { FakeStore() },
        FakeSource(),
    )

    private class FakeSource : ExchangeRateSource {
        override fun currentRate(currency: String) = CurrentRate(
            currency,
            LocalDate.of(2026, 8, 5),
            BigDecimal("78.5"),
        )

        override fun historicalRates(currency: String, from: LocalDate, to: LocalDate): List<Rate> =
            error("The populated store should avoid downloads")
    }

    private class FakeStore : ExchangeRateStore {
        private val rates = listOf(
            Rate(LocalDate.of(2026, 8, 1), BigDecimal("75")),
            Rate(LocalDate.of(2026, 8, 4), BigDecimal("77")),
        )

        override fun missingRanges(
            currency: String,
            requestedFrom: LocalDate,
            requestedTo: LocalDate,
            today: LocalDate,
        ): List<DateRange> = emptyList()

        override fun loadRates(currency: String, from: LocalDate, to: LocalDate): List<Rate> =
            rates.filter { !it.date().isBefore(from) && !it.date().isAfter(to) }

        override fun storeDownload(
            currency: String,
            downloaded: DateRange,
            historicalThrough: LocalDate,
            rates: List<Rate>,
        ) = Unit

        override fun close() = Unit
    }
}
