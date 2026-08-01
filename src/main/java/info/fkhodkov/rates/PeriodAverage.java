package info.fkhodkov.rates;

import java.math.BigDecimal;
import java.time.LocalDate;

record PeriodAverage(Period period, BigDecimal value, int observations, LocalDate firstDate, LocalDate lastDate) {
  static PeriodAverage noData(Period period) {
    return new PeriodAverage(period, null, 0, null, null);
  }

  boolean hasData() {
    return observations > 0;
  }
}
