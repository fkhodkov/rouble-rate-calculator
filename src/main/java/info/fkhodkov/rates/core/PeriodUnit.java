package info.fkhodkov.rates.core;

import java.time.LocalDate;
import java.util.Arrays;

public enum PeriodUnit {
  DAY('d'),
  WEEK('w'),
  MONTH('m'),
  YEAR('y');

  private final char suffix;

  PeriodUnit(char suffix) {
    this.suffix = suffix;
  }

  public char suffix() {
    return suffix;
  }

  public LocalDate startDate(LocalDate endDate, int amount) {
    return switch (this) {
      case DAY -> endDate.minusDays(amount);
      case WEEK -> endDate.minusWeeks(amount);
      case MONTH -> endDate.minusMonths(amount);
      case YEAR -> endDate.minusYears(amount);
    };
  }

  public LocalDate endDate(LocalDate startDate, int amount) {
    return switch (this) {
      case DAY -> startDate.plusDays(amount);
      case WEEK -> startDate.plusWeeks(amount);
      case MONTH -> startDate.plusMonths(amount);
      case YEAR -> startDate.plusYears(amount);
    };
  }

  public static PeriodUnit fromSuffix(char suffix) {
    return Arrays.stream(values())
        .filter(unit -> unit.suffix == suffix)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unsupported period unit: " + suffix));
  }
}
