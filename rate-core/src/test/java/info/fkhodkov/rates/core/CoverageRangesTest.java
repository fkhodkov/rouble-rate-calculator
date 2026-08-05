package info.fkhodkov.rates.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoverageRangesTest {
  @Test
  void findsGapsAndKeepsTodayRefreshable() {
    LocalDate today = LocalDate.of(2026, 8, 5);
    List<DateRange> missing = CoverageRanges.missing(
        LocalDate.of(2026, 8, 1), today, today,
        List.of(new DateRange(LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 3))));

    assertEquals(List.of(
        new DateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1)),
        new DateRange(LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 4)),
        new DateRange(today, today)), missing);
  }

  @Test
  void mergesOverlappingAndAdjacentRanges() {
    assertEquals(List.of(new DateRange(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10))),
        CoverageRanges.merge(List.of(
            new DateRange(LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 10)),
            new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5)))));
  }
}
