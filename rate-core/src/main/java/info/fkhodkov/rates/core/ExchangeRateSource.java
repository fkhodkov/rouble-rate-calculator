package info.fkhodkov.rates.core;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/** Supplies normalized rates independently of a particular HTTP or XML implementation. */
public interface ExchangeRateSource {
  CurrentRate currentRate(String currency) throws IOException, InterruptedException;

  List<Rate> historicalRates(String currency, LocalDate from, LocalDate to)
      throws IOException, InterruptedException;
}
