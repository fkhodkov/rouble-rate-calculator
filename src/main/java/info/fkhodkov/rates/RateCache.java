package info.fkhodkov.rates;

import info.fkhodkov.rates.data.Rate;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Persistent SQLite storage for rates and date ranges already fetched from CBR. */
final class RateCache implements AutoCloseable {
  private final Connection connection;

  RateCache(Path database) throws IOException, SQLException {
    Path absolute = database.toAbsolutePath();
    Path parent = absolute.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    connection = DriverManager.getConnection("jdbc:sqlite:" + absolute);
    try (Statement statement = connection.createStatement()) {
      statement.execute("PRAGMA journal_mode=WAL");
      statement.execute("PRAGMA foreign_keys=ON");
      statement.execute("""
          CREATE TABLE IF NOT EXISTS rates (
            currency TEXT NOT NULL,
            rate_date TEXT NOT NULL,
            rubles_per_unit TEXT NOT NULL,
            PRIMARY KEY (currency, rate_date)
          )""");
      statement.execute("""
          CREATE TABLE IF NOT EXISTS coverage (
            currency TEXT NOT NULL,
            start_date TEXT NOT NULL,
            end_date TEXT NOT NULL,
            PRIMARY KEY (currency, start_date, end_date),
            CHECK (start_date <= end_date)
          )""");
    }
  }

  List<DateRange> missingRanges(
      String currency, LocalDate requestedFrom, LocalDate requestedTo, LocalDate today)
      throws SQLException {
    List<DateRange> missing = new ArrayList<>();
    LocalDate historicalTo = requestedTo.isBefore(today) ? requestedTo : today.minusDays(1);
    if (!historicalTo.isBefore(requestedFrom)) {
      subtractCoverage(currency, requestedFrom, historicalTo, missing);
    }
    if (!requestedTo.isBefore(today)) {
      missing.add(new DateRange(requestedFrom.isAfter(today) ? requestedFrom : today, requestedTo));
    }
    return List.copyOf(missing);
  }

  private void subtractCoverage(
      String currency, LocalDate from, LocalDate to, List<DateRange> missing) throws SQLException {
    LocalDate cursor = from;
    for (DateRange covered : coverage(currency)) {
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

  List<Rate> loadRates(String currency, LocalDate from, LocalDate to)
      throws SQLException {
    String sql = """
        SELECT rate_date, rubles_per_unit
        FROM rates
        WHERE currency = ? AND rate_date BETWEEN ? AND ?
        ORDER BY rate_date""";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, currency);
      statement.setString(2, from.toString());
      statement.setString(3, to.toString());
      try (ResultSet result = statement.executeQuery()) {
        List<Rate> rates = new ArrayList<>();
        while (result.next()) {
          rates.add(new Rate(
              LocalDate.parse(result.getString(1)), new BigDecimal(result.getString(2))));
        }
        return List.copyOf(rates);
      }
    }
  }

  void storeDownload(
      String currency, DateRange downloaded, LocalDate historicalThrough,
      List<Rate> rates) throws SQLException {
    connection.setAutoCommit(false);
    try {
      try (PreparedStatement statement = connection.prepareStatement("""
          INSERT INTO rates(currency, rate_date, rubles_per_unit) VALUES (?, ?, ?)
          ON CONFLICT(currency, rate_date) DO UPDATE SET rubles_per_unit=excluded.rubles_per_unit""")) {
        for (Rate rate : rates) {
          statement.setString(1, currency);
          statement.setString(2, rate.date().toString());
          statement.setString(3, rate.rublesPerUnit().toPlainString());
          statement.addBatch();
        }
        statement.executeBatch();
      }
      if (!downloaded.from().isAfter(historicalThrough)) {
        DateRange permanent = new DateRange(downloaded.from(),
            downloaded.to().isBefore(historicalThrough) ? downloaded.to() : historicalThrough);
        replaceCoverage(currency, permanent);
      }
      connection.commit();
    } catch (SQLException e) {
      connection.rollback();
      throw e;
    } finally {
      connection.setAutoCommit(true);
    }
  }

  private List<DateRange> coverage(String currency) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "SELECT start_date, end_date FROM coverage WHERE currency=? ORDER BY start_date")) {
      statement.setString(1, currency);
      try (ResultSet result = statement.executeQuery()) {
        List<DateRange> ranges = new ArrayList<>();
        while (result.next()) {
          ranges.add(new DateRange(
              LocalDate.parse(result.getString(1)), LocalDate.parse(result.getString(2))));
        }
        return ranges;
      }
    }
  }

  private void replaceCoverage(String currency, DateRange addition) throws SQLException {
    List<DateRange> all = new ArrayList<>(coverage(currency));
    all.add(addition);
    all.sort(Comparator.comparing(DateRange::from));
    List<DateRange> merged = new ArrayList<>();
    for (DateRange range : all) {
      if (merged.isEmpty()) {
        merged.add(range);
        continue;
      }
      DateRange previous = merged.getLast();
      if (!range.from().isAfter(previous.to().plusDays(1))) {
        LocalDate end = range.to().isAfter(previous.to()) ? range.to() : previous.to();
        merged.set(merged.size() - 1, new DateRange(previous.from(), end));
      } else {
        merged.add(range);
      }
    }
    try (PreparedStatement delete = connection.prepareStatement(
        "DELETE FROM coverage WHERE currency=?")) {
      delete.setString(1, currency);
      delete.executeUpdate();
    }
    try (PreparedStatement insert = connection.prepareStatement(
        "INSERT INTO coverage(currency, start_date, end_date) VALUES (?, ?, ?)")) {
      for (DateRange range : merged) {
        insert.setString(1, currency);
        insert.setString(2, range.from().toString());
        insert.setString(3, range.to().toString());
        insert.addBatch();
      }
      insert.executeBatch();
    }
  }

  @Override
  public void close() throws SQLException {
    connection.close();
  }

  record DateRange(LocalDate from, LocalDate to) {
  }
}
