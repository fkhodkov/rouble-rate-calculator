package info.fkhodkov.rates;

import java.time.LocalDate;

record Period(int amount, PeriodUnit unit) {
  LocalDate startDate(LocalDate endDate) {
    return switch (unit) {
      case DAY -> endDate.minusDays(amount);
      case WEEK -> endDate.minusWeeks(amount);
      case MONTH -> endDate.minusMonths(amount);
    };
  }

  @Override
  public String toString() {
    return amount + String.valueOf(unit.getSuffix());
  }
}
