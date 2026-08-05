package info.fkhodkov.rates.core;

import java.time.LocalDate;
import java.util.Locale;

public record Period(int amount, PeriodUnit unit) {
  public static Period parse(String value) {
    String period = value.trim().toLowerCase(Locale.ROOT);
    if (period.length() < 2) {
      throw invalid(value);
    }
    PeriodUnit unit;
    try {
      unit = PeriodUnit.fromSuffix(period.charAt(period.length() - 1));
    } catch (IllegalArgumentException ignored) {
      throw invalid(value);
    }
    try {
      int amount = Integer.parseInt(period.substring(0, period.length() - 1));
      if (amount < 1) {
        throw invalid(value);
      }
      return new Period(amount, unit);
    } catch (NumberFormatException ignored) {
      throw invalid(value);
    }
  }

  private static IllegalArgumentException invalid(String value) {
    return new IllegalArgumentException(
        "Period must be a positive number followed by d, w, m, or y: " + value);
  }

  public LocalDate startDate(LocalDate endDate) {
    return unit.startDate(endDate, amount);
  }

  public LocalDate endDate(LocalDate startDate) {
    return unit.endDate(startDate, amount);
  }

  @Override
  public String toString() {
    return amount + String.valueOf(unit.suffix());
  }
}
