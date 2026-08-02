package info.fkhodkov.rates;

import info.fkhodkov.rates.data.PeriodAverage;
import java.time.LocalDate;
import java.util.List;

record Calculation(String currency, LocalDate endDate, List<PeriodAverage> averages) {
}
