package info.fkhodkov.rates;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;

/** Source of raw Bank of Russia XML data. */
interface CbrClient {
  InputStream downloadDailyRates() throws IOException, InterruptedException;

  InputStream downloadHistoricalRates(String currencyId, LocalDate from, LocalDate to)
      throws IOException, InterruptedException;
}
