package info.fkhodkov.rates.data;

import java.math.BigDecimal;
import java.time.LocalDate;

public sealed interface PeriodAverage permits PeriodAverage.NoData, PeriodAverage.Data {

  static PeriodAverage noData(Period period) {
    return new NoData(period);
  }
  static PeriodAverage data(Period period,
                            BigDecimal value,
                            int observations,
                            LocalDate firstDate,
                            LocalDate lastDate) {
    return new Data(period, value, observations, firstDate, lastDate);
  }

  record NoData(Period period) implements PeriodAverage {}

  record Data(
      Period period,
      BigDecimal value,
      int observations,
      LocalDate firstDate,
      LocalDate lastDate
  ) implements PeriodAverage {
  }
}
