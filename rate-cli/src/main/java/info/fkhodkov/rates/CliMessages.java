package info.fkhodkov.rates;

import java.math.BigDecimal;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.ResourceBundle;

final class CliMessages {
  static final String BUNDLE_NAME = "info.fkhodkov.rates.Messages";

  private final Locale locale;
  private final ResourceBundle bundle;
  private final DateTimeFormatter dateFormatter;

  CliMessages(Locale locale) {
    this.locale = locale;
    bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale);
    dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
  }

  ResourceBundle bundle() {
    return bundle;
  }

  String text(String key, Object... arguments) {
    return new MessageFormat(bundle.getString(key), locale).format(arguments);
  }

  String date(LocalDate date) {
    return dateFormatter.format(date);
  }

  String decimal(BigDecimal value) {
    NumberFormat format = NumberFormat.getNumberInstance(locale);
    format.setGroupingUsed(false);
    format.setMaximumFractionDigits(10);
    return format.format(value.stripTrailingZeros());
  }

  String observations(int count) {
    String form;
    if ("ru".equals(locale.getLanguage())) {
      int lastTwoDigits = count % 100;
      int lastDigit = count % 10;
      if (lastDigit == 1 && lastTwoDigits != 11) {
        form = "one";
      } else if (lastDigit >= 2 && lastDigit <= 4
          && (lastTwoDigits < 12 || lastTwoDigits > 14)) {
        form = "few";
      } else {
        form = "many";
      }
    } else {
      form = count == 1 ? "one" : "other";
    }
    return text("observations." + form, count);
  }

  static String defaultText(String key) {
    return ResourceBundle.getBundle(BUNDLE_NAME, Locale.getDefault()).getString(key);
  }
}
