package info.fkhodkov.rates.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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
internal fun RateScreen(
    calculator: info.fkhodkov.rates.core.ExchangeRateCalculator,
    rateViewModel: RateViewModel = viewModel(factory = RateViewModel.factory(calculator)),
) {
    val state by rateViewModel.state.collectAsState()
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
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
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
            ),
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
                PeriodField(
                    state.periods, rateViewModel::updatePeriods, !state.loading, true,
                    rateViewModel::calculate,
                )
            }
            CalculationMode.INTERVAL -> {
                DateField("Start date", state.startDate, rateViewModel::updateStartDate, !state.loading)
                DateField("End date (optional)", state.endDate, rateViewModel::updateEndDate, !state.loading)
                PeriodField(
                    state.periods, rateViewModel::updatePeriods, !state.loading, false,
                    rateViewModel::calculate,
                )
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
            state.loading -> CircularProgressIndicator(
                modifier = Modifier.semantics { contentDescription = "Loading rates" },
            )
            state.error != null -> {
                Text(
                    state.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
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
}

private val CalculationMode.label: String
    get() = when (this) {
        CalculationMode.PERIODS -> "Periods"
        CalculationMode.INTERVAL -> "Interval"
        CalculationMode.TODAY -> "Today"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Next,
        ),
        label = { Text(label) },
        supportingText = { Text("YYYY-MM-DD") },
        trailingIcon = {
            TextButton(onClick = { showPicker = true }, enabled = enabled) { Text("Pick") }
        },
    )
    if (showPicker) {
        val initialDate = value.toLocalDateOrNull()
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = initialDate?.toEpochMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onValueChange(it.toLocalDate().toString()) }
                        showPicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun PeriodField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    multiple: Boolean,
    onDone: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        label = { Text(if (multiple) "Periods" else "Period (optional)") },
        supportingText = {
            Text(if (multiple) "Comma-separated: 3m,7d,1w" else "One period, for example 3m")
        },
    )
}

private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(trim()) }.getOrNull()

private fun LocalDate.toEpochMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
