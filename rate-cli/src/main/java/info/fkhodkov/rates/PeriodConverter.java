package info.fkhodkov.rates;

import info.fkhodkov.rates.core.Period;
import picocli.CommandLine;

final class PeriodConverter implements CommandLine.ITypeConverter<Period> {
  @Override
  public Period convert(String value) {
    try {
      return Period.parse(value);
    } catch (IllegalArgumentException ignored) {
      throw new IllegalArgumentException(
          new CliMessages(java.util.Locale.getDefault()).text("error.period", value));
    }
  }
}
