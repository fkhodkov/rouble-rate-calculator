package info.fkhodkov.rates.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RateScreen((application as RateApplication).calculator)
                }
            }
        }
    }
}

@Composable
private fun RateScreen(
    calculator: info.fkhodkov.rates.core.ExchangeRateCalculator,
    rateViewModel: RateViewModel = viewModel(factory = RateViewModel.factory(calculator)),
) {
    val state by rateViewModel.state.collectAsState()
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Rouble Rate Calculator", style = MaterialTheme.typography.headlineMedium)
        Text("Official Bank of Russia rates", color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(
            value = state.currency,
            onValueChange = rateViewModel::updateCurrency,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
            singleLine = true,
            label = { Text("Currency") },
            supportingText = { Text("Three-letter code, for example USD") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalculationMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.mode == mode,
                    onClick = { rateViewModel.selectMode(mode) },
                    enabled = !state.loading,
                    label = { Text(mode.label) },
                )
            }
        }
        when (state.mode) {
            CalculationMode.PERIODS -> {
                DateField("End date", state.endDate, rateViewModel::updateEndDate, !state.loading)
                PeriodField(state.periods, rateViewModel::updatePeriods, !state.loading, true)
            }
            CalculationMode.INTERVAL -> {
                DateField("Start date", state.startDate, rateViewModel::updateStartDate, !state.loading)
                DateField("End date (optional)", state.endDate, rateViewModel::updateEndDate, !state.loading)
                PeriodField(state.periods, rateViewModel::updatePeriods, !state.loading, false)
                Text("Leave end date and period empty to calculate through yesterday.")
            }
            CalculationMode.TODAY -> Text("Show the currently effective official CBR rate.")
        }
        Button(
            onClick = rateViewModel::calculate,
            enabled = !state.loading,
        ) {
            Text("Calculate")
        }
        when {
            state.loading -> CircularProgressIndicator()
            state.error != null -> {
                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
        }
        state.periodResults.forEach { result ->
            Text(
                "${result.period}: ${result.startDate} through ${result.endDate}",
                style = MaterialTheme.typography.titleMedium,
            )
            if (result.average == null) {
                Text("No published rates")
            } else {
                Text(
                    "${result.average} RUB",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text("${result.observations} published rates")
                Text("${result.firstDate} to ${result.lastDate}")
            }
        }
        state.intervalResult?.let { result ->
            Text(
                "${result.startDate} through ${result.endDate}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text("${result.average} RUB", style = MaterialTheme.typography.headlineLarge)
            Text("${result.observations} published rates")
            Text("${result.firstDate} to ${result.lastDate}")
        }
        state.currentResult?.let { result ->
            Text("Effective ${result.effectiveDate}", style = MaterialTheme.typography.titleMedium)
            Text("${result.rate} RUB", style = MaterialTheme.typography.headlineLarge)
            Text("per 1 ${state.currency}")
        }
    }
}

private val CalculationMode.label: String
    get() = when (this) {
        CalculationMode.PERIODS -> "Periods"
        CalculationMode.INTERVAL -> "Interval"
        CalculationMode.TODAY -> "Today"
    }

@Composable
private fun DateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        label = { Text(label) },
        supportingText = { Text("YYYY-MM-DD") },
    )
}

@Composable
private fun PeriodField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    multiple: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        label = { Text(if (multiple) "Periods" else "Period (optional)") },
        supportingText = {
            Text(if (multiple) "Comma-separated: 3m,7d,1w" else "One period, for example 3m")
        },
    )
}
