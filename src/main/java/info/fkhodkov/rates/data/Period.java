package info.fkhodkov.rates.data;

import java.time.LocalDate;

public record Period(int amount, PeriodUnit unit) {
  public LocalDate startDate(LocalDate endDate) {
    return unit.getStartToEnd().apply(endDate, (long) amount);
  }

  public LocalDate endDate(LocalDate startDate) {
    return switch (unit) {
      case DAY -> startDate.plusDays(amount);
      case WEEK -> startDate.plusWeeks(amount);
      case MONTH -> startDate.plusMonths(amount);
      case YEAR -> startDate.plusYears(amount);
    };
  }

  @Override
  public String toString() {
    return amount + String.valueOf(unit.getSuffix());
  }
}
