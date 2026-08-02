package info.fkhodkov.rates.data;

import java.time.LocalDate;

public record Period(int amount, PeriodUnit unit) {
  public LocalDate startDate(LocalDate endDate) {
    return unit.getStartToEnd().apply(endDate, (long) amount);
  }

  @Override
  public String toString() {
    return amount + String.valueOf(unit.getSuffix());
  }
}
