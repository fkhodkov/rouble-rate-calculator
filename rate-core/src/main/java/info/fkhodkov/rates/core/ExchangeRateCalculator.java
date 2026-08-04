package info.fkhodkov.rates.core;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Platform-neutral use cases for current rates and cached historical averages. */
public final class ExchangeRateCalculator {
  private final Clock clock;
  private final ExchangeRateStoreFactory storeFactory;
  private final ExchangeRateSource source;

  public ExchangeRateCalculator(
      Clock clock, ExchangeRateStoreFactory storeFactory, ExchangeRateSource source) {
    this.clock = clock;
    this.storeFactory = storeFactory;
    this.source = source;
  }

  public CurrentRate currentRate(String currency) throws IOException, InterruptedException {
    return source.currentRate(currency);
  }

  public Calculation calculate(String currency, LocalDate endDate, List<Period> periods)
      throws Exception {
    LocalDate earliest = periods.stream()
        .map(period -> period.startDate(endDate))
        .min(LocalDate::compareTo)
        .orElseThrow(() -> new IllegalArgumentException("At least one period is required."));
    List<Rate> rates = loadRates(currency, earliest, endDate);
    if (rates.isEmpty()) {
      throw new IllegalStateException("The Bank of Russia returned no rates for this period.");
    }
    return RateAverages.forPeriods(currency, endDate, periods, rates);
  }

  public IntervalCalculation calculateInterval(
      String currency, LocalDate startDate, LocalDate endDate) throws Exception {
    if (endDate.isBefore(startDate)) {
      throw new IllegalArgumentException("End date must not be before start date.");
    }
    List<Rate> rates = loadRates(currency, startDate, endDate);
    return RateAverages.forInterval(currency, startDate, endDate, rates);
  }

  private List<Rate> loadRates(String currency, LocalDate from, LocalDate to) throws Exception {
    try (ExchangeRateStore store = storeFactory.open()) {
      LocalDate today = LocalDate.now(clock);
      List<DateRange> missing = store.missingRanges(currency, from, to, today);
      if (!missing.isEmpty()) {
        DateRange download = new DateRange(
            missing.get(0).from(), missing.get(missing.size() - 1).to());
        List<Rate> downloaded = source.historicalRates(currency, download.from(), download.to());
        store.storeDownload(currency, download, today.minusDays(1), downloaded);
      }
      return store.loadRates(currency, from, to);
    }
  }
}
