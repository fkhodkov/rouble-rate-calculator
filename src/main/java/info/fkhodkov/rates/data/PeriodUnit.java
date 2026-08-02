package info.fkhodkov.rates.data;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum PeriodUnit {
  DAY('d', LocalDate::minusDays),
  WEEK('w', LocalDate::minusWeeks),
  MONTH('m', LocalDate::minusMonths),
  YEAR('y', LocalDate::minusYears)
  ;
  private static final Map<Character, PeriodUnit> BY_SUFFIX = Arrays.stream(PeriodUnit.values())
      .collect(Collectors.toUnmodifiableMap(PeriodUnit::getSuffix, Function.identity()));

  private final char suffix;
  private final BiFunction<LocalDate, Long, LocalDate> startToEnd;

  PeriodUnit(char suffix, BiFunction<LocalDate, Long, LocalDate> startToEnd) {
    this.suffix = suffix;
    this.startToEnd = startToEnd;
  }

  public char getSuffix() {
    return suffix;
  }

  public BiFunction<LocalDate, Long, LocalDate> getStartToEnd() {
    return startToEnd;
  }

  public static PeriodUnit fromSuffix(char suffix) {
    if (!BY_SUFFIX.containsKey(suffix)) {
      throw new IllegalArgumentException("Unsupported period unit: " + suffix);
    }
    return BY_SUFFIX.get(suffix);
  }
}
