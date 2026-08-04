package info.fkhodkov.rates.core;

import java.time.LocalDate;

public record DateRange(LocalDate from, LocalDate to) {
  public DateRange {
    if (to.isBefore(from)) {
      throw new IllegalArgumentException("Range end must not be before range start.");
    }
  }
}
