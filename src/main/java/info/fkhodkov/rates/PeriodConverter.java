package info.fkhodkov.rates;

import java.util.Locale;
import picocli.CommandLine;

final class PeriodConverter implements CommandLine.ITypeConverter<Period> {
  @Override
  public Period convert(String value) {
    String period = value.trim().toLowerCase(Locale.ROOT);
    if (!period.matches("[1-9]\\d*" + PeriodUnit.PATTERN)) {
      throw new IllegalArgumentException(
          "period must be a positive number followed by d, w, or m: " + value);
    }
    try {
      int amount = Integer.parseInt(period.substring(0, period.length() - 1));
      return new Period(amount, PeriodUnit.fromSuffix(period.charAt(period.length() - 1)));
    } catch (NumberFormatException _) {
      throw new IllegalArgumentException("period number is too large: " + value);
    }
  }
}
