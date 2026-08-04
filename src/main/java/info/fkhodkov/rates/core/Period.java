package info.fkhodkov.rates.core;

import java.time.LocalDate;

public record Period(int amount, PeriodUnit unit) {
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
