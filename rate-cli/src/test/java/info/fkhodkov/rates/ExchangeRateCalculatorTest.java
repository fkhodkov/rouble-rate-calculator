package info.fkhodkov.rates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import info.fkhodkov.rates.core.Calculation;
import info.fkhodkov.rates.cbr.CbrRateSource;
import info.fkhodkov.rates.core.CurrentRate;
import info.fkhodkov.rates.core.DateRange;
import info.fkhodkov.rates.core.ExchangeRateCalculator;
import info.fkhodkov.rates.core.IntervalCalculation;
import info.fkhodkov.rates.core.Period;
import info.fkhodkov.rates.core.PeriodAverage;
import info.fkhodkov.rates.core.PeriodUnit;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExchangeRateCalculatorTest {
  private static final Clock CLOCK = Clock.fixed(
      Instant.parse("2026-08-02T00:00:00Z"), ZoneOffset.UTC);

  @TempDir
  Path temporaryDirectory;

  @Test
  void readsCurrentRateAndNormalizesNominal() throws Exception {
    FakeCbrClient client = new FakeCbrClient();
    client.setDailyXml(dailyXml("02.08.2026", "JPY", "R01820", "100", "53,25"));
    ExchangeRateCalculator calculator = calculator(client, "current.db");

    CurrentRate result = calculator.currentRate("JPY");

    assertEquals("JPY", result.currency());
    assertEquals(LocalDate.of(2026, Month.AUGUST, 2), result.effectiveDate());
    assertEquals(0, new BigDecimal("0.5325").compareTo(result.rublesPerUnit()));
    assertEquals(1, client.getDailyDownloads());
    assertTrue(client.getLastDailyStream().isClosed());
  }

  @Test
  void calculatesHistoricalAverageThenReusesCacheWithoutClientCalls() throws Exception {
    FakeCbrClient client = new FakeCbrClient();
    client.setDailyXml(dailyXml("31.07.2026", "USD", "R01235", "1", "82,00"));
    client.setHistoricalXml(historicalXml("R01235",
        makeRecord("24.07.2026", "1", "80,00"),
        makeRecord("31.07.2026", "1", "82,00")));
    Path database = temporaryDirectory.resolve("cache.db");
    ExchangeRateCalculator calculator = calculator(client, database);

    Calculation first = calculator.calculate("USD", LocalDate.of(2026, Month.JULY, 31),
        List.of(new Period(7, PeriodUnit.DAY)));

    PeriodAverage.Data average = assertInstanceOf(PeriodAverage.Data.class, first.averages().getFirst());
    assertEquals(new BigDecimal("81.000000"), average.value());
    assertEquals(2, average.observations());
    assertEquals(1, client.getDailyDownloads());
    assertEquals(1, client.getHistoricalDownloads());
    assertTrue(client.getLastHistoricalStream().isClosed());

    FakeCbrClient offline = FakeCbrClient.throwing(new IOException("network must not be used"));
    Calculation cached = calculator(offline, database).calculate(
        "USD", LocalDate.of(2026, Month.JULY, 31), List.of(new Period(7, PeriodUnit.DAY)));

    PeriodAverage.Data data = assertInstanceOf(PeriodAverage.Data.class, cached.averages().getFirst());
    assertEquals(new BigDecimal("81.000000"), data.value());
    assertEquals(0, offline.getDailyDownloads());
    assertEquals(0, offline.getHistoricalDownloads());
  }

  @Test
  void combinesSeveralMissingIntervalsIntoOneDownload() throws Exception {
    Path database = temporaryDirectory.resolve("gaps.db");
    try (RateCache cache = new RateCache(database)) {
      LocalDate historicalThrough = LocalDate.of(2026, Month.JANUARY, 1);
      cache.storeDownload("EUR",
          new DateRange(LocalDate.of(2025, Month.MARCH, 1), LocalDate.of(2025, Month.APRIL, 30)),
          historicalThrough, List.of());
      cache.storeDownload("EUR",
          new DateRange(LocalDate.of(2025, Month.JULY, 1), LocalDate.of(2025, Month.AUGUST, 31)),
          historicalThrough, List.of());
    }
    FakeCbrClient client = new FakeCbrClient();
    client.setDailyXml(dailyXml("30.11.2025", "EUR", "R01239", "1", "90,00"));
    client.setHistoricalXml(historicalXml("R01239",
        makeRecord("29.11.2025", "1", "90,00")));

    calculator(client, database).calculate(
        "EUR", LocalDate.of(2025, Month.NOVEMBER, 30), List.of(new Period(10, PeriodUnit.MONTH)));

    assertEquals(1, client.getHistoricalDownloads());
    assertEquals(LocalDate.of(2025, Month.JANUARY, 30), client.getLastFrom());
    assertEquals(LocalDate.of(2025, Month.NOVEMBER, 30), client.getLastTo());
  }

  @Test
  void calculatesAnExplicitInclusiveInterval() throws Exception {
    FakeCbrClient client = new FakeCbrClient();
    client.setDailyXml(dailyXml("10.07.2026", "USD", "R01235", "1", "82,00"));
    client.setHistoricalXml(historicalXml("R01235",
        makeRecord("01.07.2026", "1", "80,00"),
        makeRecord("10.07.2026", "1", "82,00")));

    IntervalCalculation result = calculator(client, "interval.db").calculateInterval(
        "USD", LocalDate.of(2026, Month.JULY, 1), LocalDate.of(2026, Month.JULY, 10));

    assertEquals(new BigDecimal("81.000000"), result.value());
    assertEquals(2, result.observations());
    assertEquals(LocalDate.of(2026, Month.JULY, 1), client.getLastFrom());
    assertEquals(LocalDate.of(2026, Month.JULY, 10), client.getLastTo());
  }

  @Test
  void rejectsAnIntervalWhoseEndPrecedesItsStart() {
    ExchangeRateCalculator calculator = calculator(
        FakeCbrClient.throwing(new IOException("must not be called")), "invalid-interval.db");

    IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
        () -> calculator.calculateInterval(
            "USD", LocalDate.of(2026, Month.JULY, 10), LocalDate.of(2026, Month.JULY, 1)));

    assertEquals("End date must not be before start date.", thrown.getMessage());
  }

  @Test
  void propagatesClientFailure() {
    IOException failure = new IOException("CBR unavailable");
    ExchangeRateCalculator calculator = calculator(FakeCbrClient.throwing(failure), "error.db");

    IOException thrown = assertThrows(IOException.class,
        () -> calculator.currentRate("USD"));

    assertEquals(failure, thrown);
  }

  private ExchangeRateCalculator calculator(FakeCbrClient client, String databaseName) {
    return calculator(client, temporaryDirectory.resolve(databaseName));
  }

  private ExchangeRateCalculator calculator(FakeCbrClient client, Path database) {
    return new ExchangeRateCalculator(
        CLOCK, () -> new RateCache(database), new CbrRateSource(client));
  }

  private static String dailyXml(
      String date, String code, String id, String nominal, String value) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <ValCurs Date="%s">
          <Valute ID="%s">
            <CharCode>%s</CharCode><Nominal>%s</Nominal><Value>%s</Value>
          </Valute>
        </ValCurs>
        """.formatted(date, id, code, nominal, value);
  }

  private static String historicalXml(String id, String... records) {
    return "<ValCurs ID=\"%s\">%s</ValCurs>".formatted(id, String.join("", records));
  }

  private static String makeRecord(String date, String nominal, String value) {
    return "<Record Date=\"%s\"><Nominal>%s</Nominal><Value>%s</Value></Record>"
        .formatted(date, nominal, value);
  }

}
