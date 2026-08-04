package info.fkhodkov.rates.core;

import java.math.BigDecimal;
import java.time.LocalDate;

public record IntervalCalculation(
    String currency,
    LocalDate requestedStart,
    LocalDate requestedEnd,
    BigDecimal value,
    int observations,
    LocalDate firstDate,
    LocalDate lastDate) {
}
