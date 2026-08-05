package info.fkhodkov.rates.android

interface RateStateStore {
    fun load(): RateUiState?

    fun save(state: RateUiState)
}

internal object NoOpRateStateStore : RateStateStore {
    override fun load(): RateUiState? = null

    override fun save(state: RateUiState) = Unit
}
