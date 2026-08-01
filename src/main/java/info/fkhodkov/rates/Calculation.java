package info.fkhodkov.rates;

import java.time.LocalDate;
import java.util.List;

record Calculation(String currency, LocalDate endDate, List<PeriodAverage> averages) {
}
