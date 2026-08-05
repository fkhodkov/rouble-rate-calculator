package info.fkhodkov.rates.android

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RateViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = Clock.fixed(
        Instant.parse("2026-08-05T08:00:00Z"),
        ZoneId.of("Europe/Moscow"),
    )
    private lateinit var viewModel: RateViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val rates = listOf(
            Rate(LocalDate.of(2026, 8, 1), BigDecimal("75.0")),
            Rate(LocalDate.of(2026, 8, 4), BigDecimal("77.0")),
        )
        val store = FakeStore(rates)
        val calculator = ExchangeRateCalculator(clock, { store }, FakeSource())
        viewModel = RateViewModel(calculator, clock, dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun calculatesExplicitInterval() {
        viewModel.selectMode(CalculationMode.INTERVAL)
        viewModel.updateStartDate("2026-08-01")
        viewModel.updateEndDate("2026-08-04")

        viewModel.calculate()

        val result = viewModel.state.value.intervalResult
        assertEquals("76", result?.average)
        assertEquals(2, result?.observations)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun calculatesCurrentRate() {
        viewModel.selectMode(CalculationMode.TODAY)

        viewModel.calculate()

        assertEquals("78.5", viewModel.state.value.currentResult?.rate)
        assertEquals(LocalDate.of(2026, 8, 5), viewModel.state.value.currentResult?.effectiveDate)
    }

    @Test
    fun rejectsEndDateAndPeriodForInterval() {
        viewModel.selectMode(CalculationMode.INTERVAL)
        viewModel.updateStartDate("2026-08-01")
        viewModel.updateEndDate("2026-08-04")
        viewModel.updatePeriods("1w")

        viewModel.calculate()

        assertEquals(
            "Start date, end date, and period cannot be used together.",
            viewModel.state.value.error,
        )
    }

    @Test
    fun calculatesStartDateThroughYesterday() {
        viewModel.selectMode(CalculationMode.INTERVAL)
        viewModel.updateStartDate("2026-08-01")

        viewModel.calculate()

        val result = viewModel.state.value.intervalResult
        assertEquals(LocalDate.of(2026, 8, 4), result?.endDate)
        assertEquals("76", result?.average)
    }

    @Test
    fun calculatesStartDatePlusOnePeriod() {
        viewModel.selectMode(CalculationMode.INTERVAL)
        viewModel.updateStartDate("2026-08-01")
        viewModel.updatePeriods("1w")

        viewModel.calculate()

        val result = viewModel.state.value.intervalResult
        assertEquals(LocalDate.of(2026, 8, 8), result?.endDate)
        assertEquals(2, result?.observations)
    }

    @Test
    fun rejectsMalformedDate() {
        viewModel.updateEndDate("08/04/2026")

        viewModel.calculate()

        assertEquals("end date must use YYYY-MM-DD format.", viewModel.state.value.error)
        assertNull(viewModel.state.value.intervalResult)
    }

    private class FakeSource : ExchangeRateSource {
        override fun currentRate(currency: String) = CurrentRate(
            currency,
            LocalDate.of(2026, 8, 5),
            BigDecimal("78.5"),
        )

        override fun historicalRates(
            currency: String,
            from: LocalDate,
            to: LocalDate,
        ): List<Rate> = error("The populated store should avoid downloads")
    }

    private class FakeStore(private val rates: List<Rate>) : ExchangeRateStore {
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
