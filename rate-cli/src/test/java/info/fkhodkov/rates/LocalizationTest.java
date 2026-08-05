package info.fkhodkov.rates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class LocalizationTest {
  @Test
  void selectsExplicitLanguages() {
    assertEquals(Locale.ENGLISH, LanguageSelector.from(new String[] {"--language", "en"}));
    assertEquals("ru", LanguageSelector.from(new String[] {"-l=ru"}).getLanguage());
    assertEquals("ru", LanguageSelector.from(new String[] {
        "--currency", "EUR", "--language", "ru", "--today"
    }).getLanguage());
  }

  @Test
  void rendersRussianHelp() {
    Locale previous = Locale.getDefault();
    try {
      Locale russian = Locale.forLanguageTag("ru");
      Locale.setDefault(russian);
      CliMessages messages = new CliMessages(russian);
      var output = new StringWriter();
      var command = new CommandLine(new ExchangeRateApp(messages))
          .setResourceBundle(messages.bundle())
          .setOut(new PrintWriter(output));

      assertEquals(0, command.execute("--language", "ru", "--help"));
      assertTrue(output.toString().contains("Расчёт среднего официального курса"));
      assertTrue(output.toString().contains("Язык вывода"));
    } finally {
      Locale.setDefault(previous);
    }
  }

  @Test
  void localizesRussianDatesAndDecimals() {
    CliMessages messages = new CliMessages(Locale.forLanguageTag("ru"));

    assertTrue(messages.date(LocalDate.of(2026, 8, 5)).contains("авг"));
    assertEquals("81,1291", messages.decimal(new BigDecimal("81.1291")));
    assertEquals("1 опубликованное значение", messages.observations(1));
    assertEquals("3 опубликованных значения", messages.observations(3));
    assertEquals("12 опубликованных значений", messages.observations(12));
  }
}
