package info.fkhodkov.rates.android

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import info.fkhodkov.rates.core.ExchangeRateCalculator
import info.fkhodkov.rates.core.Period
import info.fkhodkov.rates.core.PeriodAverage
import java.io.Serializable
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CalculationMode { PERIODS, INTERVAL, TODAY }

enum class RateError {
    INVALID_CURRENCY,
    PERIOD_REQUIRED,
    INVALID_PERIOD,
    START_DATE_REQUIRED,
    END_DATE_REQUIRED,
    START_DATE_FORMAT,
    END_DATE_FORMAT,
    INTERVAL_CONFLICT,
    SINGLE_PERIOD_REQUIRED,
    LOAD_FAILED,
}

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
    val error: RateError? = null,
) : Serializable

data class AverageUi(
    val period: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val average: String?,
    val observations: Int = 0,
    val firstDate: LocalDate? = null,
    val lastDate: LocalDate? = null,
) : Serializable

data class IntervalUi(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val average: String,
    val observations: Int,
    val firstDate: LocalDate,
    val lastDate: LocalDate,
) : Serializable

data class CurrentRateUi(val effectiveDate: LocalDate, val rate: String) : Serializable

class RateViewModel(
    private val calculator: ExchangeRateCalculator,
    clock: Clock = Clock.system(ZoneId.of("Europe/Moscow")),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val stateStore: RateStateStore = NoOpRateStateStore,
) : ViewModel() {
    private val yesterday = LocalDate.now(clock).minusDays(1)
    private val mutableState = MutableStateFlow(
        savedStateHandle.get<RateUiState>(STATE_KEY)?.copy(loading = false)
            ?: stateStore.load()?.copy(loading = false, error = null)
            ?: RateUiState(endDate = yesterday.toString()),
    )

    val state: StateFlow<RateUiState> = mutableState.asStateFlow()

    init {
        mutableState.onEach { savedStateHandle[STATE_KEY] = it }.launchIn(viewModelScope)
        if (!mutableState.value.hasOutcome()) calculate()
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
            showError(RateError.INVALID_CURRENCY)
            return
        }
        val request = try {
            buildRequest(input)
        } catch (error: InputException) {
            showError(error.error)
            return
        }
        mutableState.value = input.copy(currency = currency, loading = true, error = null)
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { request.execute(calculator, currency) }
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    error = RateError.LOAD_FAILED,
                )
            }
        }
    }

    private fun buildRequest(input: RateUiState): RateRequest = when (input.mode) {
        CalculationMode.TODAY -> TodayRequest()
        CalculationMode.PERIODS -> PeriodsRequest(
            parseDate(input.endDate, RateError.END_DATE_REQUIRED, RateError.END_DATE_FORMAT),
            parsePeriods(input.periods),
        )
        CalculationMode.INTERVAL -> {
            val start = parseDate(
                input.startDate, RateError.START_DATE_REQUIRED, RateError.START_DATE_FORMAT,
            )
            val hasEnd = input.endDate.isNotBlank()
            val hasPeriod = input.periods.isNotBlank()
            if (hasEnd && hasPeriod) throw InputException(RateError.INTERVAL_CONFLICT)
            val end = when {
                hasEnd -> parseDate(
                    input.endDate, RateError.END_DATE_REQUIRED, RateError.END_DATE_FORMAT,
                )
                hasPeriod -> {
                    val periods = parsePeriods(input.periods)
                    if (periods.size != 1) throw InputException(RateError.SINGLE_PERIOD_REQUIRED)
                    periods.first().endDate(start)
                }
                else -> yesterday
            }
            IntervalRequest(start, end)
        }
    }

    private fun parsePeriods(value: String): List<Period> {
        if (value.isBlank()) throw InputException(RateError.PERIOD_REQUIRED)
        return try {
            value.split(',').map { Period.parse(it) }
        } catch (_: IllegalArgumentException) {
            throw InputException(RateError.INVALID_PERIOD)
        }
    }

    private fun parseDate(
        value: String,
        requiredError: RateError,
        formatError: RateError,
    ): LocalDate {
        if (value.isBlank()) throw InputException(requiredError)
        return try {
            LocalDate.parse(value.trim())
        } catch (_: DateTimeParseException) {
            throw InputException(formatError)
        }
    }

    private fun update(change: RateUiState.() -> RateUiState) {
        mutableState.value = mutableState.value.change()
    }

    private fun showError(error: RateError) {
        mutableState.value = mutableState.value.copy(error = error).withoutResults()
    }

    private fun RateUiState.withoutResults() = copy(
        periodResults = emptyList(),
        intervalResult = null,
        currentResult = null,
    )

    private fun RateUiState.hasOutcome() =
        periodResults.isNotEmpty() || intervalResult != null || currentResult != null || error != null

    private class InputException(val error: RateError) : IllegalArgumentException()

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
            showResult(mutableState.value.copy(loading = false, periodResults = results))
        }
    }

    private inner class IntervalRequest(
        private val startDate: LocalDate,
        private val endDate: LocalDate,
    ) : RateRequest {
        override fun execute(calculator: ExchangeRateCalculator, currency: String) {
            val result = calculator.calculateInterval(currency, startDate, endDate)
            showResult(mutableState.value.copy(
                loading = false,
                intervalResult = IntervalUi(
                    result.requestedStart(), result.requestedEnd(),
                    result.value().stripTrailingZeros().toPlainString(), result.observations(),
                    result.firstDate(), result.lastDate(),
                ),
            ))
        }
    }

    private inner class TodayRequest : RateRequest {
        override fun execute(calculator: ExchangeRateCalculator, currency: String) {
            val result = calculator.currentRate(currency)
            showResult(mutableState.value.copy(
                loading = false,
                currentResult = CurrentRateUi(
                    result.effectiveDate(),
                    result.rublesPerUnit().stripTrailingZeros().toPlainString(),
                ),
            ))
        }
    }

    private fun showResult(result: RateUiState) {
        mutableState.value = result
        stateStore.save(result)
    }

    companion object {
        private const val STATE_KEY = "rateUiState"

        fun factory(
            calculator: ExchangeRateCalculator,
            stateStore: RateStateStore,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RateViewModel(
                    calculator = calculator,
                    savedStateHandle = createSavedStateHandle(),
                    stateStore = stateStore,
                )
            }
        }
    }
}
