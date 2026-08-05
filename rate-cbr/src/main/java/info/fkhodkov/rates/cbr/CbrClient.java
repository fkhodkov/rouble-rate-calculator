package info.fkhodkov.rates.cbr;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;

/** Source of raw Bank of Russia XML data. */
public interface CbrClient {
  InputStream downloadDailyRates() throws IOException, InterruptedException;

  InputStream downloadHistoricalRates(String currencyId, LocalDate from, LocalDate to)
      throws IOException, InterruptedException;
}
