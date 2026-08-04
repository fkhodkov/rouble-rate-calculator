package info.fkhodkov.rates;

import info.fkhodkov.rates.core.Calculation;
import info.fkhodkov.rates.core.CurrentRate;
import info.fkhodkov.rates.core.DateRange;
import info.fkhodkov.rates.core.ExchangeRateSource;
import info.fkhodkov.rates.core.ExchangeRateStore;
import info.fkhodkov.rates.core.ExchangeRateStoreFactory;
import info.fkhodkov.rates.core.IntervalCalculation;
import info.fkhodkov.rates.core.Period;
import info.fkhodkov.rates.core.Rate;
import info.fkhodkov.rates.core.RateAverages;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Fetches, caches, and calculates official Bank of Russia exchange-rate averages. */
final class ExchangeRateCalculator {
  private final Clock clock;
  private final ExchangeRateStoreFactory storeFactory;
  private final ExchangeRateSource source;

  ExchangeRateCalculator(Clock clock, Path cachePath, CbrClient cbrClient) {
    this(clock, () -> new RateCache(cachePath), new CbrRateSource(cbrClient));
  }

  ExchangeRateCalculator(
      Clock clock, ExchangeRateStoreFactory storeFactory, ExchangeRateSource source) {
    this.clock = clock;
    this.storeFactory = storeFactory;
    this.source = source;
  }

  CurrentRate currentRate(String currency) throws IOException, InterruptedException {
    return source.currentRate(currency);
  }

  Calculation calculate(String currency, LocalDate endDate, List<Period> periods)
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

  IntervalCalculation calculateInterval(String currency, LocalDate startDate, LocalDate endDate)
      throws Exception {
    if (endDate.isBefore(startDate)) {
      throw new IllegalArgumentException("End date must not be before start date.");
    }
    List<Rate> rates = loadRates(currency, startDate, endDate);
    return RateAverages.forInterval(currency, startDate, endDate, rates);
  }

  private List<Rate> loadRates(String currency, LocalDate from, LocalDate to)
      throws Exception {
    try (ExchangeRateStore store = storeFactory.open()) {
      LocalDate today = LocalDate.now(clock);
      List<DateRange> missing = store.missingRanges(currency, from, to, today);
      if (!missing.isEmpty()) {
        DateRange download = new DateRange(
            missing.getFirst().from(), missing.getLast().to());
        List<Rate> downloaded = source.historicalRates(currency, download.from(), download.to());
        store.storeDownload(currency, download, today.minusDays(1), downloaded);
      }
      return store.loadRates(currency, from, to);
    }
  }
}
