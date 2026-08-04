package info.fkhodkov.rates;

import info.fkhodkov.rates.core.Calculation;
import info.fkhodkov.rates.core.CurrentRate;
import info.fkhodkov.rates.core.IntervalCalculation;
import info.fkhodkov.rates.core.Period;
import info.fkhodkov.rates.core.PeriodAverage;
import info.fkhodkov.rates.core.PeriodAverage.Data;
import info.fkhodkov.rates.core.PeriodAverage.NoData;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

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

  @Option(names = {"-s", "--start-date"}, paramLabel = "YYYY-MM-DD",
      description = "Inclusive start date for an explicit interval.")
  private LocalDate startDate;

  @Option(names = {"-e", "--end-date"}, paramLabel = "YYYY-MM-DD",
      description = "Inclusive end date (default: yesterday in Moscow).")
  private LocalDate endDate = LocalDate.now(CLOCK).minusDays(1);

  @Option(names = {"-p", "--periods"}, split = ",", defaultValue = "3m",
      converter = PeriodConverter.class, paramLabel = "LIST",
      description = "Comma-separated periods using d, w, m, or y (default: ${DEFAULT-VALUE}).")
  private List<Period> periods;

  @Option(names = {"-t", "--today"},
      description = "Show only the currently effective CBR rate.")
  private boolean todayOnly;

  @Spec
  private CommandSpec commandSpec;

  static void main(String[] args) {
    int exitCode = new CommandLine(new ExchangeRateApp()).execute(args);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  @Override
  public Integer call() {
    try {
      ExchangeRateCalculator calculator = new ExchangeRateCalculator(
          CLOCK, defaultCachePath(), new CbrClientImpl());
      var parseResult = commandSpec.commandLine().getParseResult();
      boolean hasStart = parseResult.hasMatchedOption("--start-date");
      boolean hasEnd = parseResult.hasMatchedOption("--end-date");
      boolean hasPeriods = parseResult.hasMatchedOption("--periods");
      if (todayOnly) {
        if (hasStart || hasEnd || hasPeriods) {
          throw new IllegalArgumentException(
              "--today cannot be combined with date or period options.");
        }
        print(calculator.currentRate(currency));
      } else if (hasStart) {
        if (hasEnd && hasPeriods) {
          throw new IllegalArgumentException(
              "--start-date, --end-date, and --periods cannot be used together.");
        }
        LocalDate intervalEnd;
        if (hasPeriods) {
          if (periods.size() != 1) {
            throw new IllegalArgumentException(
                "Exactly one period is required when --start-date is used.");
          }
          intervalEnd = periods.getFirst().endDate(startDate);
        } else {
          intervalEnd = hasEnd ? endDate : LocalDate.now(CLOCK).minusDays(1);
        }
        print(calculator.calculateInterval(currency, startDate, intervalEnd));
      } else {
        print(calculator.calculate(currency, endDate, periods));
      }
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
      switch (average) {
        case NoData(Period period) -> System.out.printf(Locale.ROOT, "%6s: no data%n", period);
        case Data(var period, var value, var observations, var firstDate, var lastDate) -> System.out.printf(
            Locale.ROOT, "%6s: %12s  (%d published rates, %s to %s)%n",
            period, value.toPlainString(), observations, firstDate, lastDate);
      }
    }
  }

  private static void print(CurrentRate rate) {
    System.out.printf(Locale.ROOT,
        "Official CBR %s/RUB rate effective %s: %s RUB per 1 %s%n",
        rate.currency(), rate.effectiveDate(), rate.rublesPerUnit().stripTrailingZeros().toPlainString(),
        rate.currency());
  }

  private static void print(IntervalCalculation calculation) {
    System.out.printf(Locale.ROOT,
        "Official CBR %s/RUB average from %s to %s: %s%n",
        calculation.currency(), calculation.requestedStart(), calculation.requestedEnd(),
        calculation.value().toPlainString());
    System.out.printf(Locale.ROOT,
        "(%d published rates, %s to %s; RUB per 1 %s)%n",
        calculation.observations(), calculation.firstDate(), calculation.lastDate(),
        calculation.currency());
  }
}
