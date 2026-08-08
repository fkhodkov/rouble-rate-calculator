package info.fkhodkov.rates.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/** Platform-neutral exchange-rate arithmetic shared by all front ends. */
public final class RateAverages {
  private RateAverages() {
  }

  // SequencedCollection methods require Android API 35; this library supports API 26.
  @SuppressWarnings("SequencedCollectionMethodCanBeUsed")
  public static Calculation forPeriods(
      String currency, LocalDate endDate, List<Period> periods, List<Rate> rates) {
    List<Rate> notTooLate = rates.stream().filter(rate -> !rate.date().isAfter(endDate)).toList();
    List<PeriodAverage> averages = periods.stream().map(period -> {
      LocalDate from = period.startDate(endDate);
      List<Rate> periodRates = notTooLate.stream()
          .filter(rate -> !rate.date().isBefore(from))
          .toList();
      return periodRates.isEmpty() ? PeriodAverage.noData(period) :
          PeriodAverage.data(period, average(periodRates), periodRates.size(),
              periodRates.get(0).date(), periodRates.get(periodRates.size() - 1).date());
    }).toList();
    return new Calculation(currency, endDate, averages);
  }

  // SequencedCollection methods require Android API 35; this library supports API 26.
  @SuppressWarnings("SequencedCollectionMethodCanBeUsed")
  public static IntervalCalculation forInterval(
      String currency, LocalDate startDate, LocalDate endDate, List<Rate> rates) {
    if (endDate.isBefore(startDate)) {
      throw new IllegalArgumentException("End date must not be before start date.");
    }
    if (rates.isEmpty()) {
      throw new IllegalStateException("The Bank of Russia returned no rates for this interval.");
    }
    return new IntervalCalculation(currency, startDate, endDate, average(rates), rates.size(),
        rates.get(0).date(), rates.get(rates.size() - 1).date());
  }

  private static BigDecimal average(List<Rate> rates) {
    return rates.stream()
        .map(Rate::rublesPerUnit)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .divide(BigDecimal.valueOf(rates.size()), 6, RoundingMode.HALF_UP);
  }
}
