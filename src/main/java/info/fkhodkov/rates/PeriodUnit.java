package info.fkhodkov.rates;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

enum PeriodUnit {
  DAY('d'),
  WEEK('w'),
  MONTH('m'),
  ;
  private static final Map<Character, PeriodUnit> BY_SUFFIX = Arrays.stream(PeriodUnit.values())
      .collect(Collectors.toUnmodifiableMap(PeriodUnit::getSuffix, Function.identity()));

  static final String PATTERN = Arrays.stream(PeriodUnit.values())
      .map(PeriodUnit::getSuffix)
      .map(String::valueOf)
      .collect(Collectors.joining("", "[", "]"));

  private final char suffix;

  PeriodUnit(char suffix) {
    this.suffix = suffix;
  }

  public char getSuffix() {
    return suffix;
  }

  static PeriodUnit fromSuffix(char suffix) {
    if (!BY_SUFFIX.containsKey(suffix)) {
      throw new IllegalArgumentException("Unsupported period unit: " + suffix);
    }
    return BY_SUFFIX.get(suffix);
  }
}
