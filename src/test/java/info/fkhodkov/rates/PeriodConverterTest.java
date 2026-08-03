package info.fkhodkov.rates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import info.fkhodkov.rates.data.Period;
import info.fkhodkov.rates.data.PeriodUnit;
import org.junit.jupiter.api.Test;

class PeriodConverterTest {
  private final PeriodConverter converter = new PeriodConverter();

  @Test
  void convertsSupportedUnitsCaseInsensitively() {
    assertEquals(new Period(7, PeriodUnit.DAY), converter.convert("7d"));
    assertEquals(new Period(1, PeriodUnit.WEEK), converter.convert("1W"));
    assertEquals(new Period(3, PeriodUnit.MONTH), converter.convert(" 3m "));
  }

  @Test
  void rejectsZeroAndUnsupportedUnits() {
    assertThrows(IllegalArgumentException.class, () -> converter.convert("0d"));
    assertThrows(IllegalArgumentException.class, () -> converter.convert("1x"));
  }

  @Test
  void calculatesAnEndDateForwardFromAStartDate() {
    Period period = new Period(3, PeriodUnit.MONTH);

    assertEquals(java.time.LocalDate.of(2026, 4, 30),
        period.endDate(java.time.LocalDate.of(2026, 1, 31)));
  }
}
