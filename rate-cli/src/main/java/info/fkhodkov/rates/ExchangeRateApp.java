package info.fkhodkov.rates;

import info.fkhodkov.rates.cbr.CbrRateSource;
import info.fkhodkov.rates.core.Calculation;
import info.fkhodkov.rates.core.CurrentRate;
import info.fkhodkov.rates.core.ExchangeRateCalculator;
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
    resourceBundle = CliMessages.BUNDLE_NAME,
    mixinStandardHelpOptions = true,
    sortOptions = false)
public final class ExchangeRateApp implements Callable<Integer> {
  private static final Clock CLOCK = Clock.system(ZoneId.of("Europe/Moscow"));

  @Option(names = {"-c", "--currency"}, defaultValue = "USD", converter = CurrencyConverter.class,
      paramLabel = "CODE", descriptionKey = "currency")
  private String currency;

  @Option(names = {"-s", "--start-date"}, paramLabel = "YYYY-MM-DD",
      descriptionKey = "startDate")
  private LocalDate startDate;

  @Option(names = {"-e", "--end-date"}, paramLabel = "YYYY-MM-DD",
      descriptionKey = "endDate")
  private LocalDate endDate = LocalDate.now(CLOCK).minusDays(1);

  @Option(names = {"-p", "--periods"}, split = ",", defaultValue = "3m",
      converter = PeriodConverter.class, paramLabel = "LIST",
      descriptionKey = "periods")
  private List<Period> periods;

  @Option(names = {"-t", "--today"},
      descriptionKey = "today")
  private boolean todayOnly;

  @Option(names = {"-l", "--language"}, paramLabel = "en|ru", descriptionKey = "language")
  @SuppressWarnings("unused") // Picocli populates this field after locale preselection.
  private String language;

  @Spec
  private CommandSpec commandSpec;

  private final CliMessages messages;

  public ExchangeRateApp() {
    this(new CliMessages(Locale.getDefault()));
  }

  ExchangeRateApp(CliMessages messages) {
    this.messages = messages;
  }

  static void main(String[] args) {
    Locale locale;
    try {
      locale = LanguageSelector.from(args);
    } catch (CommandLine.ParameterException e) {
      System.err.println(e.getMessage());
      System.exit(2);
      return;
    }
    Locale.setDefault(locale);
    CliMessages messages = new CliMessages(locale);
    int exitCode = new CommandLine(new ExchangeRateApp(messages))
        .setResourceBundle(messages.bundle())
        .execute(args);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  @Override
  public Integer call() {
    try {
      ExchangeRateCalculator calculator = new ExchangeRateCalculator(
          CLOCK, () -> new RateCache(defaultCachePath()),
          new CbrRateSource(new CbrClientImpl()));
      var parseResult = commandSpec.commandLine().getParseResult();
      boolean hasStart = parseResult.hasMatchedOption("--start-date");
      boolean hasEnd = parseResult.hasMatchedOption("--end-date");
      boolean hasPeriods = parseResult.hasMatchedOption("--periods");
      if (todayOnly) {
        if (hasStart || hasEnd || hasPeriods) {
          throw new IllegalArgumentException(
              messages.text("error.todayCombination"));
        }
        print(calculator.currentRate(currency));
      } else if (hasStart) {
        if (hasEnd && hasPeriods) {
          throw new IllegalArgumentException(
              messages.text("error.intervalCombination"));
        }
        LocalDate intervalEnd;
        if (hasPeriods) {
          if (periods.size() != 1) {
            throw new IllegalArgumentException(
                messages.text("error.singlePeriod"));
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
      System.err.println(messages.text("error.prefix", e.getMessage()));
      return 2;
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
      System.err.println(messages.text("error.interrupted"));
      return 1;
    } catch (Exception e) {
      System.err.println(messages.text("error.calculate", e.getMessage()));
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

  private void print(Calculation calculation) {
    System.out.println(messages.text("average.heading",
        calculation.currency(), messages.date(calculation.endDate())));
    System.out.println(messages.text("average.explanation"));
    for (PeriodAverage average : calculation.averages()) {
      switch (average) {
        case NoData(Period period) -> System.out.println(
            messages.text("average.noData", period));
        case Data(var period, var value, var observations, var firstDate, var lastDate) ->
            System.out.println(messages.text("average.data", period, messages.decimal(value),
                messages.observations(observations), messages.date(firstDate),
                messages.date(lastDate)));
      }
    }
  }

  private void print(CurrentRate rate) {
    System.out.println(messages.text("current", rate.currency(), messages.date(rate.effectiveDate()),
        messages.decimal(rate.rublesPerUnit())));
  }

  private void print(IntervalCalculation calculation) {
    System.out.println(messages.text("interval", calculation.currency(),
        messages.date(calculation.requestedStart()), messages.date(calculation.requestedEnd()),
        messages.decimal(calculation.value())));
    System.out.println(messages.text("interval.details",
        messages.observations(calculation.observations()),
        messages.date(calculation.firstDate()), messages.date(calculation.lastDate()),
        calculation.currency()));
  }
}
