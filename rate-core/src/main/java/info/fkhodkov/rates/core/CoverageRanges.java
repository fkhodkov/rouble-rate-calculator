package info.fkhodkov.rates.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Shared coverage arithmetic for persistent store implementations. */
public final class CoverageRanges {
  private CoverageRanges() {
  }

  public static List<DateRange> missing(
      LocalDate requestedFrom, LocalDate requestedTo, LocalDate today,
      List<DateRange> coveredRanges) {
    List<DateRange> missing = new ArrayList<>();
    LocalDate historicalTo = requestedTo.isBefore(today) ? requestedTo : today.minusDays(1);
    if (!historicalTo.isBefore(requestedFrom)) {
      subtractCoverage(requestedFrom, historicalTo, merge(coveredRanges), missing);
    }
    if (!requestedTo.isBefore(today)) {
      missing.add(new DateRange(requestedFrom.isAfter(today) ? requestedFrom : today, requestedTo));
    }
    return List.copyOf(missing);
  }

  // SequencedCollection methods require Android API 35; this library supports API 26.
  @SuppressWarnings("SequencedCollectionMethodCanBeUsed")
  public static List<DateRange> merge(List<DateRange> ranges) {
    List<DateRange> sorted = new ArrayList<>(ranges);
    sorted.sort(Comparator.comparing(DateRange::from));
    List<DateRange> merged = new ArrayList<>();
    for (DateRange range : sorted) {
      if (merged.isEmpty()) {
        merged.add(range);
        continue;
      }
      DateRange previous = merged.get(merged.size() - 1);
      if (!range.from().isAfter(previous.to().plusDays(1))) {
        LocalDate end = range.to().isAfter(previous.to()) ? range.to() : previous.to();
        merged.set(merged.size() - 1, new DateRange(previous.from(), end));
      } else {
        merged.add(range);
      }
    }
    return List.copyOf(merged);
  }

  private static void subtractCoverage(
      LocalDate from, LocalDate to, List<DateRange> coveredRanges, List<DateRange> missing) {
    LocalDate cursor = from;
    for (DateRange covered : coveredRanges) {
      if (covered.to().isBefore(cursor) || covered.from().isAfter(to)) {
        continue;
      }
      if (covered.from().isAfter(cursor)) {
        missing.add(new DateRange(cursor, covered.from().minusDays(1)));
      }
      if (!covered.to().isBefore(cursor)) {
        cursor = covered.to().plusDays(1);
      }
      if (cursor.isAfter(to)) {
        return;
      }
    }
    missing.add(new DateRange(cursor, to));
  }
}
