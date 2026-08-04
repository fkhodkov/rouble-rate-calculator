package info.fkhodkov.rates.core;

import java.time.LocalDate;
import java.util.List;

public record Calculation(String currency, LocalDate endDate, List<PeriodAverage> averages) {
}
