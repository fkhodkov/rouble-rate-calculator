package info.fkhodkov.rates.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
        OutlinedTextField(
            value = state.periods,
            onValueChange = rateViewModel::updatePeriods,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
            singleLine = true,
            label = { Text("Periods") },
            supportingText = { Text("Comma-separated: 3m,7d,1w") },
        )
        Button(
            onClick = rateViewModel::calculate,
            enabled = !state.loading,
        ) {
            Text("Calculate")
        }
        Text("Rates through ${state.endDate}")
        when {
            state.loading -> CircularProgressIndicator()
            state.error != null -> {
                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
        }
        state.results.forEach { result ->
            Text(
                "${result.period}: ${result.startDate} through ${state.endDate}",
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
    }
}
