package info.fkhodkov.rates.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import info.fkhodkov.rates.core.ExchangeRateCalculator
import info.fkhodkov.rates.core.Period
import info.fkhodkov.rates.core.PeriodAverage
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CalculationMode { PERIODS, INTERVAL, TODAY }

data class RateUiState(
    val mode: CalculationMode = CalculationMode.PERIODS,
    val currency: String = "USD",
    val periods: String = "3m",
    val startDate: String = "",
    val endDate: String,
    val loading: Boolean = false,
    val periodResults: List<AverageUi> = emptyList(),
    val intervalResult: IntervalUi? = null,
    val currentResult: CurrentRateUi? = null,
    val error: String? = null,
)

data class AverageUi(
    val period: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val average: String?,
    val observations: Int = 0,
    val firstDate: LocalDate? = null,
    val lastDate: LocalDate? = null,
)

data class IntervalUi(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val average: String,
    val observations: Int,
    val firstDate: LocalDate,
    val lastDate: LocalDate,
)

data class CurrentRateUi(val effectiveDate: LocalDate, val rate: String)

class RateViewModel(
    private val calculator: ExchangeRateCalculator,
    clock: Clock = Clock.system(ZoneId.of("Europe/Moscow")),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val yesterday = LocalDate.now(clock).minusDays(1)
    private val mutableState = MutableStateFlow(RateUiState(endDate = yesterday.toString()))

    val state: StateFlow<RateUiState> = mutableState.asStateFlow()

    init {
        calculate()
    }

    fun selectMode(mode: CalculationMode) {
        val state = mutableState.value
        mutableState.value = when (mode) {
            CalculationMode.PERIODS -> state.copy(
                mode = mode,
                periods = state.periods.ifBlank { "3m" },
                endDate = state.endDate.ifBlank { yesterday.toString() },
                error = null,
            )
            CalculationMode.INTERVAL -> state.copy(
                mode = mode,
                periods = "",
                startDate = "",
                endDate = "",
                error = null,
            )
            CalculationMode.TODAY -> state.copy(mode = mode, error = null)
        }.withoutResults()
    }

    fun updateCurrency(value: String) = update { copy(currency = value, error = null) }
    fun updatePeriods(value: String) = update { copy(periods = value, error = null) }
    fun updateStartDate(value: String) = update { copy(startDate = value, error = null) }
    fun updateEndDate(value: String) = update { copy(endDate = value, error = null) }

    fun calculate() {
        if (mutableState.value.loading) return
        val input = mutableState.value
        val currency = input.currency.trim().uppercase(Locale.ROOT)
        if (!currency.matches(Regex("[A-Z]{3}"))) {
            showError("Currency must be a three-letter code, such as USD or EUR.")
            return
        }
        val request = try {
            buildRequest(input)
        } catch (error: IllegalArgumentException) {
            showError(error.message ?: "Invalid input.")
            return
        }
        mutableState.value = input.copy(currency = currency, loading = true).withoutResults()
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { request.execute(calculator, currency) }
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    error = error.message ?: "Could not load exchange rates.",
                )
            }
        }
    }

    private fun buildRequest(input: RateUiState): RateRequest = when (input.mode) {
        CalculationMode.TODAY -> TodayRequest()
        CalculationMode.PERIODS -> PeriodsRequest(
            parseDate(input.endDate, "end date"),
            parsePeriods(input.periods),
        )
        CalculationMode.INTERVAL -> {
            val start = parseDate(input.startDate, "start date")
            val hasEnd = input.endDate.isNotBlank()
            val hasPeriod = input.periods.isNotBlank()
            require(!(hasEnd && hasPeriod)) {
                "Start date, end date, and period cannot be used together."
            }
            val end = when {
                hasEnd -> parseDate(input.endDate, "end date")
                hasPeriod -> {
                    val periods = parsePeriods(input.periods)
                    require(periods.size == 1) { "Enter exactly one period with a start date." }
                    periods.first().endDate(start)
                }
                else -> yesterday
            }
            IntervalRequest(start, end)
        }
    }

    private fun parsePeriods(value: String): List<Period> {
        require(value.isNotBlank()) { "Enter at least one period." }
        return value.split(',').map { Period.parse(it) }
    }

    private fun parseDate(value: String, label: String): LocalDate {
        require(value.isNotBlank()) { "Enter a $label." }
        return try {
            LocalDate.parse(value.trim())
        } catch (_: DateTimeParseException) {
            throw IllegalArgumentException("$label must use YYYY-MM-DD format.")
        }
    }

    private fun update(change: RateUiState.() -> RateUiState) {
        mutableState.value = mutableState.value.change()
    }

    private fun showError(message: String) {
        mutableState.value = mutableState.value.copy(error = message).withoutResults()
    }

    private fun RateUiState.withoutResults() = copy(
        periodResults = emptyList(),
        intervalResult = null,
        currentResult = null,
    )

    private sealed interface RateRequest {
        fun execute(calculator: ExchangeRateCalculator, currency: String)
    }

    private inner class PeriodsRequest(
        private val endDate: LocalDate,
        private val periods: List<Period>,
    ) : RateRequest {
        override fun execute(calculator: ExchangeRateCalculator, currency: String) {
            val calculation = calculator.calculate(currency, endDate, periods)
            val results = periods.zip(calculation.averages()).map { (period, average) ->
                when (average) {
                    is PeriodAverage.Data -> AverageUi(
                        period.toString(), period.startDate(endDate), endDate,
                        average.value().stripTrailingZeros().toPlainString(),
                        average.observations(), average.firstDate(), average.lastDate(),
                    )
                    is PeriodAverage.NoData -> AverageUi(
                        period.toString(), period.startDate(endDate), endDate, null,
                    )
                }
            }
            mutableState.value = mutableState.value.copy(loading = false, periodResults = results)
        }
    }

    private inner class IntervalRequest(
        private val startDate: LocalDate,
        private val endDate: LocalDate,
    ) : RateRequest {
        override fun execute(calculator: ExchangeRateCalculator, currency: String) {
            val result = calculator.calculateInterval(currency, startDate, endDate)
            mutableState.value = mutableState.value.copy(
                loading = false,
                intervalResult = IntervalUi(
                    result.requestedStart(), result.requestedEnd(),
                    result.value().stripTrailingZeros().toPlainString(), result.observations(),
                    result.firstDate(), result.lastDate(),
                ),
            )
        }
    }

    private inner class TodayRequest : RateRequest {
        override fun execute(calculator: ExchangeRateCalculator, currency: String) {
            val result = calculator.currentRate(currency)
            mutableState.value = mutableState.value.copy(
                loading = false,
                currentResult = CurrentRateUi(
                    result.effectiveDate(),
                    result.rublesPerUnit().stripTrailingZeros().toPlainString(),
                ),
            )
        }
    }

    companion object {
        fun factory(calculator: ExchangeRateCalculator): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    RateViewModel(calculator) as T
            }
    }
}
