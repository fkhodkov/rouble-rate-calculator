package info.fkhodkov.rates;

import java.util.Locale;
import picocli.CommandLine;

final class CurrencyConverter implements CommandLine.ITypeConverter<String> {
  @Override
  public String convert(String value) {
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (!normalized.matches("[A-Z]{3}")) {
      throw new IllegalArgumentException(
          CliMessages.defaultText("error.currency"));
    }
    return normalized;
  }
}
