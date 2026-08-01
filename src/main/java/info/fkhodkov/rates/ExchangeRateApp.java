package info.fkhodkov.rates;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Command-line entry point and presentation layer. */
@Command(name = "rouble-rate-calculator",
    version = "rouble-rate-calculator 1.0.0",
    description = "Calculate average official Bank of Russia exchange rates.",
    mixinStandardHelpOptions = true,
    sortOptions = false)
public final class ExchangeRateApp implements Callable<Integer> {
  private static final Clock CLOCK = Clock.system(ZoneId.of("Europe/Moscow"));

  @Option(names = {"-c", "--currency"}, defaultValue = "USD", converter = CurrencyConverter.class,
      paramLabel = "CODE", description = "ISO currency code (default: ${DEFAULT-VALUE}).")
  private String currency;

  @Option(names = {"-e", "--end-date"}, paramLabel = "YYYY-MM-DD",
      description = "Inclusive end date (default: yesterday in Moscow).")
  private LocalDate endDate = LocalDate.now(CLOCK).minusDays(1);

  @Option(names = {"-p", "--periods"}, split = ",", defaultValue = "3m",
      converter = PeriodConverter.class, paramLabel = "LIST",
      description = "Comma-separated periods using d, w, m, or y (default: ${DEFAULT-VALUE}).")
  private List<Period> periods;

  static void main(String[] args) {
    int exitCode = new CommandLine(new ExchangeRateApp()).execute(args);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  @Override
  public Integer call() {
    try {
      ExchangeRateCalculator calculator = new ExchangeRateCalculator(CLOCK, defaultCachePath());
      print(calculator.calculate(currency, endDate, periods));
      return 0;
    } catch (IllegalArgumentException e) {
      System.err.println("Error: " + e.getMessage());
      return 2;
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
      System.err.println("Interrupted by user");
      return 1;
    } catch (Exception e) {
      System.err.println("Could not calculate rates: " + e.getMessage());
      return 1;
    }
  }

  static Path defaultCachePath() {
    String override = System.getenv("ROUBLE_RATE_DB");
    if (override != null && !override.isBlank()) {
      return Path.of(override);
    }
    return Path.of(System.getProperty("user.home"), ".cache",
        "rouble-rate-calculator", "rates.db");
  }

  private static void print(Calculation calculation) {
    System.out.printf("Official CBR %s/RUB averages through %s%n",
        calculation.currency(), calculation.endDate());
    System.out.println("(arithmetic mean of published rates, RUB per 1 currency unit)");
    for (PeriodAverage average : calculation.averages()) {
      if (!average.hasData()) {
        System.out.printf(Locale.ROOT, "%6s: no data%n", average.period());
        continue;
      }
      System.out.printf(Locale.ROOT, "%6s: %12s  (%d published rates, %s to %s)%n",
          average.period(), average.value().toPlainString(), average.observations(),
          average.firstDate(), average.lastDate());
    }
  }
}
