package info.fkhodkov.rates;

import java.time.LocalDate;

record Period(int amount, PeriodUnit unit) {
  LocalDate startDate(LocalDate endDate) {
    return unit.getStartToEnd().apply(endDate, (long) amount);
  }

  @Override
  public String toString() {
    return amount + String.valueOf(unit.getSuffix());
  }
}
