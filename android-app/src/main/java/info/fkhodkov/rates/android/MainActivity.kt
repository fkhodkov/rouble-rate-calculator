package info.fkhodkov.rates.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
                    RateScreen()
                }
            }
        }
    }
}

@Composable
private fun RateScreen(rateViewModel: RateViewModel = viewModel()) {
    val state by rateViewModel.state.collectAsState()
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Rouble Rate Calculator", style = MaterialTheme.typography.headlineMedium)
        Text("Shared core connected", color = MaterialTheme.colorScheme.primary)
        Text("Currency: ${state.currency}")
        Text("Default period: ${state.period}")
        Text("${state.startDate} through ${state.endDate}")
        Text(
            "CBR networking and persistent cache will be added in the next phase.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
