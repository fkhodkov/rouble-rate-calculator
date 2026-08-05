package info.fkhodkov.rates.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class PreferencesRateStateStore(context: Context) : RateStateStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): RateUiState? = runCatching {
        preferences.getString(STATE_KEY, null)?.let(::decode)
    }.getOrNull()

    override fun save(state: RateUiState) {
        preferences.edit().putString(STATE_KEY, encode(state)).apply()
    }

    private fun encode(state: RateUiState) = JSONObject().apply {
        put("mode", state.mode.name)
        put("currency", state.currency)
        put("periods", state.periods)
        put("startDate", state.startDate)
        put("endDate", state.endDate)
        put("periodResults", JSONArray().apply {
            state.periodResults.forEach { result ->
                put(JSONObject().apply {
                    put("period", result.period)
                    put("startDate", result.startDate.toString())
                    put("endDate", result.endDate.toString())
                    put("average", result.average)
                    put("observations", result.observations)
                    put("firstDate", result.firstDate?.toString())
                    put("lastDate", result.lastDate?.toString())
                })
            }
        })
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
        val periodResultsJson = root.getJSONArray("periodResults")
        val periodResults = buildList {
            repeat(periodResultsJson.length()) { index ->
                val result = periodResultsJson.getJSONObject(index)
                add(AverageUi(
                    period = result.getString("period"),
                    startDate = LocalDate.parse(result.getString("startDate")),
                    endDate = LocalDate.parse(result.getString("endDate")),
                    average = result.optionalString("average"),
                    observations = result.getInt("observations"),
                    firstDate = result.optionalDate("firstDate"),
                    lastDate = result.optionalDate("lastDate"),
                ))
            }
        }
        return RateUiState(
            mode = CalculationMode.valueOf(root.getString("mode")),
            currency = root.getString("currency"),
            periods = root.getString("periods"),
            startDate = root.getString("startDate"),
            endDate = root.getString("endDate"),
            periodResults = periodResults,
            intervalResult = root.optJSONObject("intervalResult")?.let { result ->
                IntervalUi(
                    startDate = LocalDate.parse(result.getString("startDate")),
                    endDate = LocalDate.parse(result.getString("endDate")),
                    average = result.getString("average"),
                    observations = result.getInt("observations"),
                    firstDate = LocalDate.parse(result.getString("firstDate")),
                    lastDate = LocalDate.parse(result.getString("lastDate")),
                )
            },
            currentResult = root.optJSONObject("currentResult")?.let { result ->
                CurrentRateUi(
                    effectiveDate = LocalDate.parse(result.getString("effectiveDate")),
                    rate = result.getString("rate"),
                )
            },
        )
    }

    private fun JSONObject.optionalString(name: String): String? =
        if (isNull(name)) null else getString(name)

    private fun JSONObject.optionalDate(name: String): LocalDate? =
        optionalString(name)?.let(LocalDate::parse)

    private companion object {
        const val PREFERENCES_NAME = "last_rate_state"
        const val STATE_KEY = "rate_ui_state"
    }
}
