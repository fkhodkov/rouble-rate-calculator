package info.fkhodkov.rates.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import org.junit.jupiter.api.Test;

class RateAveragesTest {
  @Test
  void calculatesAnIntervalWithoutInfrastructure() {
    LocalDate start = LocalDate.of(2026, Month.JANUARY, 1);
    IntervalCalculation result = RateAverages.forInterval("USD", start, start.plusDays(2), List.of(
        new Rate(start, new BigDecimal("80")),
        new Rate(start.plusDays(2), new BigDecimal("82"))));

    assertEquals(new BigDecimal("81.000000"), result.value());
    assertEquals(2, result.observations());
  }

  @Test
  void reportsNoDataForAPeriodWithNoPublishedRates() {
    Calculation result = RateAverages.forPeriods(
        "USD", LocalDate.of(2026, Month.JANUARY, 31), List.of(new Period(1, PeriodUnit.MONTH)), List.of());

    assertInstanceOf(PeriodAverage.NoData.class, result.averages().getFirst());
  }
}
