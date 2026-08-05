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
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RateUiState(
    val currency: String = "USD",
    val periods: String = "3m",
    val endDate: LocalDate,
    val loading: Boolean = false,
    val results: List<AverageUi> = emptyList(),
    val error: String? = null,
)

data class AverageUi(
    val period: String,
    val startDate: LocalDate,
    val average: String?,
    val observations: Int = 0,
    val firstDate: LocalDate? = null,
    val lastDate: LocalDate? = null,
)

class RateViewModel(
    private val calculator: ExchangeRateCalculator,
    clock: Clock = Clock.system(ZoneId.of("Europe/Moscow")),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val endDate = LocalDate.now(clock).minusDays(1)
    private val mutableState = MutableStateFlow(
        RateUiState(
            endDate = endDate,
        ),
    )

    val state: StateFlow<RateUiState> = mutableState.asStateFlow()

    init {
        calculate()
    }

    fun updateCurrency(value: String) {
        mutableState.value = mutableState.value.copy(currency = value, error = null)
    }

    fun updatePeriods(value: String) {
        mutableState.value = mutableState.value.copy(periods = value, error = null)
    }

    fun calculate() {
        if (mutableState.value.loading) return
        val currency = mutableState.value.currency.trim().uppercase(Locale.ROOT)
        val periods = try {
            parsePeriods(mutableState.value.periods)
        } catch (error: IllegalArgumentException) {
            mutableState.value = mutableState.value.copy(error = error.message, results = emptyList())
            return
        }
        if (!currency.matches(Regex("[A-Z]{3}"))) {
            mutableState.value = mutableState.value.copy(
                error = "Currency must be a three-letter code, such as USD or EUR.",
                results = emptyList(),
            )
            return
        }
        mutableState.value = mutableState.value.copy(
            loading = true,
            results = emptyList(),
            error = null,
        )
        viewModelScope.launch {
            try {
                val result = withContext(ioDispatcher) {
                    calculator.calculate(currency, endDate, periods)
                }
                val results = periods.zip(result.averages()).map { (period, average) ->
                    when (average) {
                        is PeriodAverage.Data -> AverageUi(
                            period = period.toString(),
                            startDate = period.startDate(endDate),
                            average = average.value().stripTrailingZeros().toPlainString(),
                            observations = average.observations(),
                            firstDate = average.firstDate(),
                            lastDate = average.lastDate(),
                        )
                        is PeriodAverage.NoData -> AverageUi(
                            period = period.toString(),
                            startDate = period.startDate(endDate),
                            average = null,
                        )
                    }
                }
                mutableState.value = mutableState.value.copy(
                    currency = currency,
                    loading = false,
                    results = results,
                )
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    error = error.message ?: "Could not load exchange rates.",
                )
            }
        }
    }

    private fun parsePeriods(value: String): List<Period> {
        require (value.isNotBlank()) { "Enter at least one period." }
        return value.split(',').map { Period.parse(it) }
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
