package info.fkhodkov.rates.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import info.fkhodkov.rates.core.ExchangeRateCalculator
import info.fkhodkov.rates.core.Period
import info.fkhodkov.rates.core.PeriodAverage
import info.fkhodkov.rates.core.PeriodUnit
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RateUiState(
    val currency: String = "USD",
    val period: String = "3m",
    val startDate: LocalDate,
    val endDate: LocalDate,
    val loading: Boolean = false,
    val average: String? = null,
    val observations: Int? = null,
    val firstDate: LocalDate? = null,
    val lastDate: LocalDate? = null,
    val error: String? = null,
)

class RateViewModel(
    private val calculator: ExchangeRateCalculator,
    clock: Clock = Clock.system(ZoneId.of("Europe/Moscow")),
) : ViewModel() {
    private val endDate = LocalDate.now(clock).minusDays(1)
    private val defaultPeriod = Period(3, PeriodUnit.MONTH)
    private val mutableState = MutableStateFlow(
        RateUiState(
            startDate = defaultPeriod.startDate(endDate),
            endDate = endDate,
        ),
    )

    val state: StateFlow<RateUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (mutableState.value.loading) return
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    calculator.calculate("USD", endDate, listOf(defaultPeriod))
                }
                when (val average = result.averages().first()) {
                    is PeriodAverage.Data -> mutableState.value = mutableState.value.copy(
                        loading = false,
                        average = average.value().stripTrailingZeros().toPlainString(),
                        observations = average.observations(),
                        firstDate = average.firstDate(),
                        lastDate = average.lastDate(),
                    )
                    is PeriodAverage.NoData -> mutableState.value = mutableState.value.copy(
                        loading = false,
                        error = "The CBR returned no published rates for this period.",
                    )
                }
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    error = error.message ?: "Could not load exchange rates.",
                )
            }
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
