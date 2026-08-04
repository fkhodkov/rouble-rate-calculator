package info.fkhodkov.rates.core;

import java.time.LocalDate;
import java.util.List;

/** Stores normalized rates and records date ranges completely fetched from a source. */
public interface ExchangeRateStore extends AutoCloseable {
  List<DateRange> missingRanges(
      String currency, LocalDate requestedFrom, LocalDate requestedTo, LocalDate today)
      throws Exception;

  List<Rate> loadRates(String currency, LocalDate from, LocalDate to) throws Exception;

  void storeDownload(
      String currency, DateRange downloaded, LocalDate historicalThrough, List<Rate> rates)
      throws Exception;

  @Override
  void close() throws Exception;
}
