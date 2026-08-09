package info.fkhodkov.rates.android

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject
import java.time.LocalDate

class PreferencesRateStateStore(context: Context) : RateStateStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): RateUiState? = runCatching {
        preferences.getString(STATE_KEY, null)?.let(::decode)
    }.getOrNull()

    override fun save(state: RateUiState) {
        preferences.edit { putString(STATE_KEY, encode(state)) }
    }

    private fun encode(state: RateUiState) = JSONObject().apply {
        put("mode", state.mode.name)
        put("currency", state.currency)
        put("startDate", state.startDate)
        put("endDate", state.endDate)
        state.intervalResult?.let { result ->
            put("intervalResult", JSONObject().apply {
                put("startDate", result.startDate.toString())
                put("endDate", result.endDate.toString())
                put("average", result.average)
                put("observations", result.observations)
                put("firstDate", result.firstDate.toString())
                put("lastDate", result.lastDate.toString())
            })
        }
        state.currentResult?.let { result ->
            put("currentResult", JSONObject().apply {
                put("effectiveDate", result.effectiveDate.toString())
                put("rate", result.rate)
            })
        }
    }.toString()

    private fun decode(json: String): RateUiState {
        val root = JSONObject(json)
        val legacyPeriodResult = root.optJSONArray("periodResults")?.let { results ->
            (0 until results.length())
                .map(results::getJSONObject)
                .minByOrNull { LocalDate.parse(it.getString("startDate")) }
        }
        return RateUiState(
            mode = if (root.optString("mode") == CalculationMode.TODAY.name) {
                CalculationMode.TODAY
            } else {
                CalculationMode.INTERVAL
            },
            currency = root.getString("currency"),
            startDate = root.optString("startDate").ifBlank {
                legacyPeriodResult?.getString("startDate").orEmpty()
            },
            endDate = root.optString("endDate"),
            intervalResult = root.optJSONObject("intervalResult")?.toIntervalUi()
                ?: legacyPeriodResult?.takeUnless { it.isNull("average") }?.toIntervalUi(),
            currentResult = root.optJSONObject("currentResult")?.let { result ->
                CurrentRateUi(
                    effectiveDate = LocalDate.parse(result.getString("effectiveDate")),
                    rate = result.getString("rate"),
                )
            },
        )
    }

    private fun JSONObject.toIntervalUi() =
        IntervalUi(
            startDate = LocalDate.parse(getString("startDate")),
            endDate = LocalDate.parse(getString("endDate")),
            average = getString("average"),
            observations = getInt("observations"),
            firstDate = LocalDate.parse(getString("firstDate")),
            lastDate = LocalDate.parse(getString("lastDate")),
        )

    private companion object {
        const val PREFERENCES_NAME = "last_rate_state"
        const val STATE_KEY = "rate_ui_state"
    }
}
