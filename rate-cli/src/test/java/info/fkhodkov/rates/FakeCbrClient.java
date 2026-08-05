package info.fkhodkov.rates;

import info.fkhodkov.rates.cbr.CbrClient;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

final class FakeCbrClient implements CbrClient {
  private String dailyXml;
  private String historicalXml;
  private IOException failure;
  private int dailyDownloads;
  private int historicalDownloads;
  private LocalDate lastFrom;
  private LocalDate lastTo;
  private TrackingInputStream lastDailyStream;
  private TrackingInputStream lastHistoricalStream;

  static FakeCbrClient throwing(IOException failure) {
    FakeCbrClient client = new FakeCbrClient();
    client.failure = failure;
    return client;
  }

  void setDailyXml(String dailyXml) {
    this.dailyXml = dailyXml;
  }

  void setHistoricalXml(String historicalXml) {
    this.historicalXml = historicalXml;
  }

  int getDailyDownloads() {
    return dailyDownloads;
  }

  int getHistoricalDownloads() {
    return historicalDownloads;
  }

  LocalDate getLastFrom() {
    return lastFrom;
  }

  LocalDate getLastTo() {
    return lastTo;
  }

  TrackingInputStream getLastDailyStream() {
    return lastDailyStream;
  }

  TrackingInputStream getLastHistoricalStream() {
    return lastHistoricalStream;
  }

  @Override
  public InputStream downloadDailyRates() throws IOException {
    if (failure != null) {
      throw failure;
    }
    dailyDownloads++;
    lastDailyStream = stream(dailyXml);
    return lastDailyStream;
  }

  @Override
  public InputStream downloadHistoricalRates(String currencyId, LocalDate from, LocalDate to)
      throws IOException {
    if (failure != null) {
      throw failure;
    }
    historicalDownloads++;
    lastFrom = from;
    lastTo = to;
    lastHistoricalStream = stream(historicalXml);
    return lastHistoricalStream;
  }

  private static TrackingInputStream stream(String xml) {
    return new TrackingInputStream(xml.getBytes(StandardCharsets.UTF_8));
  }
}
