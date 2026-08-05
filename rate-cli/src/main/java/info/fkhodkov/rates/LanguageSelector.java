package info.fkhodkov.rates;

import java.util.Locale;
import picocli.CommandLine;
import picocli.CommandLine.Option;

final class LanguageSelector {
  private LanguageSelector() {
  }

  static Locale from(String[] args) {
    var options = new Options();
    new CommandLine(options).setUnmatchedArgumentsAllowed(true).parseArgs(args);
    if (options.language == null) {
      return "ru".equals(Locale.getDefault().getLanguage())
          ? Locale.forLanguageTag("ru") : Locale.ENGLISH;
    }
    return switch (options.language.toLowerCase(Locale.ROOT)) {
      case "en" -> Locale.ENGLISH;
      case "ru" -> Locale.forLanguageTag("ru");
      default -> throw new CommandLine.ParameterException(
          new CommandLine(options), "--language must be en or ru");
    };
  }

  private static final class Options {
    @Option(names = {"-l", "--language"})
    private String language;
  }
}
