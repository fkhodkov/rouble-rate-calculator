package info.fkhodkov.rates.android

import androidx.lifecycle.ViewModel
import info.fkhodkov.rates.core.Period
import info.fkhodkov.rates.core.PeriodUnit
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RateUiState(
    val currency: String = "USD",
    val period: String = "3m",
    val startDate: LocalDate,
    val endDate: LocalDate,
)

class RateViewModel(
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
}
