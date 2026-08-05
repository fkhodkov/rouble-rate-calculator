package info.fkhodkov.rates.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Rouble Rate Calculator", style = MaterialTheme.typography.headlineMedium)
        Text("Official Bank of Russia rates", color = MaterialTheme.colorScheme.primary)
        Text("Currency: ${state.currency}")
        Text("Default period: ${state.period}")
        Text("${state.startDate} through ${state.endDate}")
        when {
            state.loading -> CircularProgressIndicator()
            state.average != null -> {
                Text(
                    "${state.average} RUB",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text("${state.observations} published rates")
                Text("${state.firstDate} to ${state.lastDate}")
            }
            state.error != null -> {
                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                Button(onClick = rateViewModel::refresh) { Text("Retry") }
            }
        }
    }
}
