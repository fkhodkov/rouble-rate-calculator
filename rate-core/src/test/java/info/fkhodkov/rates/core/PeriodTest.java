package info.fkhodkov.rates.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PeriodTest {
  @Test
  void parsesAllSupportedUnitsCaseInsensitively() {
    assertEquals(new Period(7, PeriodUnit.DAY), Period.parse("7d"));
    assertEquals(new Period(1, PeriodUnit.WEEK), Period.parse(" 1W "));
    assertEquals(new Period(3, PeriodUnit.MONTH), Period.parse("3m"));
    assertEquals(new Period(2, PeriodUnit.YEAR), Period.parse("2y"));
  }

  @Test
  void rejectsInvalidPeriods() {
    assertThrows(IllegalArgumentException.class, () -> Period.parse(""));
    assertThrows(IllegalArgumentException.class, () -> Period.parse("0m"));
    assertThrows(IllegalArgumentException.class, () -> Period.parse("3q"));
    assertThrows(IllegalArgumentException.class, () -> Period.parse("999999999999m"));
  }
}
