package info.fkhodkov.rates;

import info.fkhodkov.rates.core.Period;
import picocli.CommandLine;

final class PeriodConverter implements CommandLine.ITypeConverter<Period> {
  @Override
  public Period convert(String value) {
    return Period.parse(value);
  }
}
