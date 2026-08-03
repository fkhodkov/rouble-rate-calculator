package info.fkhodkov.rates;

import java.math.BigDecimal;
import java.time.LocalDate;

record IntervalCalculation(
    String currency,
    LocalDate requestedStart,
    LocalDate requestedEnd,
    BigDecimal value,
    int observations,
    LocalDate firstDate,
    LocalDate lastDate) {
}
