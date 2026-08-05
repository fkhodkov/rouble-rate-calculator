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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.text.NumberFormat

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
    val loadingDescription = stringResource(R.string.loading_rates)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.subtitle), color = MaterialTheme.colorScheme.primary)
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
            label = { Text(stringResource(R.string.currency)) },
            supportingText = { Text(stringResource(R.string.currency_hint)) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalculationMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.mode == mode,
                    onClick = { rateViewModel.selectMode(mode) },
                    enabled = !state.loading,
                    label = { Text(mode.localizedLabel()) },
                )
            }
        }
        when (state.mode) {
            CalculationMode.PERIODS -> {
                DateField(
                    stringResource(R.string.end_date), state.endDate,
                    rateViewModel::updateEndDate, !state.loading,
                )
                PeriodField(
                    state.periods, rateViewModel::updatePeriods, !state.loading, true,
                    rateViewModel::calculate,
                )
            }
            CalculationMode.INTERVAL -> {
                DateField(
                    stringResource(R.string.start_date), state.startDate,
                    rateViewModel::updateStartDate, !state.loading,
                )
                DateField(
                    stringResource(R.string.end_date_optional), state.endDate,
                    rateViewModel::updateEndDate, !state.loading,
                )
                PeriodField(
                    state.periods, rateViewModel::updatePeriods, !state.loading, false,
                    rateViewModel::calculate,
                )
                Text(stringResource(R.string.interval_hint))
            }
            CalculationMode.TODAY -> Text(stringResource(R.string.today_hint))
        }
        Button(
            onClick = rateViewModel::calculate,
            enabled = !state.loading,
        ) {
            Text(stringResource(R.string.calculate))
        }
        when {
            state.loading -> CircularProgressIndicator(
                modifier = Modifier.semantics {
                    contentDescription = loadingDescription
                },
            )
            state.error != null -> {
                Text(
                    state.error.localizedMessage().orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
        }
        state.periodResults.forEach { result ->
            Text(
                stringResource(
                    R.string.period_range, result.period,
                    localizedDate(result.startDate), localizedDate(result.endDate),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            if (result.average == null) {
                Text(stringResource(R.string.no_rates))
            } else {
                Text(
                    stringResource(R.string.rate_rub, localizedNumber(result.average)),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(pluralStringResource(
                    R.plurals.published_rates, result.observations, result.observations,
                ))
                Text(stringResource(
                    R.string.published_range,
                    localizedDate(result.firstDate), localizedDate(result.lastDate),
                ))
            }
        }
        state.intervalResult?.let { result ->
            Text(
                stringResource(
                    R.string.date_range,
                    localizedDate(result.startDate), localizedDate(result.endDate),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.rate_rub, localizedNumber(result.average)),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(pluralStringResource(
                R.plurals.published_rates, result.observations, result.observations,
            ))
            Text(stringResource(
                R.string.published_range,
                localizedDate(result.firstDate), localizedDate(result.lastDate),
            ))
        }
        state.currentResult?.let { result ->
            Text(
                stringResource(R.string.effective_date, localizedDate(result.effectiveDate)),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.rate_rub, localizedNumber(result.rate)),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(stringResource(R.string.per_currency, state.currency))
        }
        }
    }
}

@Composable
private fun CalculationMode.localizedLabel(): String =
    stringResource(when (this) {
        CalculationMode.PERIODS -> R.string.mode_periods
        CalculationMode.INTERVAL -> R.string.mode_interval
        CalculationMode.TODAY -> R.string.mode_today
    })

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
        supportingText = { Text(stringResource(R.string.date_format)) },
        trailingIcon = {
            TextButton(onClick = { showPicker = true }, enabled = enabled) {
                Text(stringResource(R.string.pick_date))
            }
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
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
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
        label = { Text(stringResource(if (multiple) R.string.periods else R.string.period_optional)) },
        supportingText = {
            Text(stringResource(if (multiple) R.string.periods_hint else R.string.period_hint))
        },
    )
}

private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(trim()) }.getOrNull()

private fun LocalDate.toEpochMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun RateError?.localizedMessage(): String? = this?.let {
    stringResource(when (it) {
        RateError.INVALID_CURRENCY -> R.string.error_invalid_currency
        RateError.PERIOD_REQUIRED -> R.string.error_period_required
        RateError.INVALID_PERIOD -> R.string.error_invalid_period
        RateError.START_DATE_REQUIRED -> R.string.error_start_required
        RateError.END_DATE_REQUIRED -> R.string.error_end_required
        RateError.START_DATE_FORMAT -> R.string.error_start_format
        RateError.END_DATE_FORMAT -> R.string.error_end_format
        RateError.INTERVAL_CONFLICT -> R.string.error_interval_conflict
        RateError.SINGLE_PERIOD_REQUIRED -> R.string.error_single_period
        RateError.LOAD_FAILED -> R.string.error_load_failed
    })
}

@Composable
private fun localizedDate(date: LocalDate?): String {
    if (date == null) return ""
    val locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)
}

@Composable
private fun localizedNumber(value: String?): String {
    if (value == null) return ""
    val locale = LocalConfiguration.current.locales[0]
    return NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        maximumFractionDigits = 10
    }.format(value.toBigDecimal())
}
