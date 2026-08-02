package info.fkhodkov.rates;

import info.fkhodkov.rates.data.Period;
import info.fkhodkov.rates.data.PeriodUnit;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import picocli.CommandLine;

final class PeriodConverter implements CommandLine.ITypeConverter<Period> {
  static final String UNITS_PATTERN = Arrays.stream(PeriodUnit.values())
      .map(PeriodUnit::getSuffix)
      .map(String::valueOf)
      .collect(Collectors.joining("", "[", "]"));

  @Override
  public Period convert(String value) {
    String period = value.trim().toLowerCase(Locale.ROOT);
    if (!period.matches("[1-9]\\d*" + UNITS_PATTERN)) {
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
