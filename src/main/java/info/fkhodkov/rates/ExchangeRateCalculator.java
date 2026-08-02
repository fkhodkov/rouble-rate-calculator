package info.fkhodkov.rates;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Fetches, caches, and calculates official Bank of Russia exchange-rate averages. */
final class ExchangeRateCalculator {
  private static final DateTimeFormatter CBR_RECORD_DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu");

  private final Clock clock;
  private final Path cachePath;
  private final CbrClient cbrClient;

  ExchangeRateCalculator(Clock clock, Path cachePath, CbrClient cbrClient) {
    this.clock = clock;
    this.cachePath = cachePath;
    this.cbrClient = cbrClient;
  }

  CurrentRate currentRate(String currency)
      throws IOException, InterruptedException,
      ParserConfigurationException, SAXException {
    Document document;
    try (InputStream input = cbrClient.downloadDailyRates()) {
      document = parseXml(input);
    }
    LocalDate effectiveDate = LocalDate.parse(
        document.getDocumentElement().getAttribute("Date"), CBR_RECORD_DATE);
    Element item = nodesAsElements(document.getElementsByTagName("Valute"))
        .filter(candidate -> currency.equalsIgnoreCase(text(candidate, "CharCode")))
        .findAny()
        .orElseThrow(() -> new IllegalArgumentException(
            "Currency '%s' is not in the current CBR daily currency list.".formatted(currency)));
    BigDecimal nominal = decimal(text(item, "Nominal"));
    BigDecimal value = decimal(text(item, "Value"));
    return new CurrentRate(currency, effectiveDate,
        value.divide(nominal, 10, RoundingMode.HALF_UP));
  }

  Calculation calculate(String currency, LocalDate endDate, List<Period> periods)
      throws SQLException, IOException, ParserConfigurationException, InterruptedException, SAXException {
    LocalDate earliest = periods.stream()
        .map(period -> period.startDate(endDate))
        .min(LocalDate::compareTo)
        .orElseThrow(() -> new IllegalArgumentException("At least one period is required."));
    List<Rate> rates = loadRates(currency, earliest, endDate);
    if (rates.isEmpty()) {
      throw new IllegalStateException("The Bank of Russia returned no rates for this period.");
    }

    List<PeriodAverage> averages = new ArrayList<>();
    for (Period period : periods) {
      LocalDate from = period.startDate(endDate);
      List<Rate> periodRates = rates.stream()
          .filter(rate -> !rate.date().isBefore(from))
          .filter(rate -> !rate.date().isAfter(endDate))
          .toList();
      if (periodRates.isEmpty()) {
        averages.add(PeriodAverage.noData(period));
        continue;
      }
      BigDecimal average = periodRates.stream()
          .map(Rate::rublesPerUnit)
          .reduce(BigDecimal.ZERO, BigDecimal::add)
          .divide(BigDecimal.valueOf(periodRates.size()), 6, RoundingMode.HALF_UP);
      averages.add(new PeriodAverage(period, average, periodRates.size(),
          periodRates.getFirst().date(), periodRates.getLast().date()));
    }
    return new Calculation(currency, endDate, List.copyOf(averages));
  }

  private List<Rate> loadRates(String currency, LocalDate from, LocalDate to)
      throws SQLException, IOException, ParserConfigurationException, InterruptedException, SAXException {
    try (RateCache cache = new RateCache(cachePath)) {
      LocalDate today = LocalDate.now(clock);
      List<RateCache.DateRange> missing = cache.missingRanges(currency, from, to, today);
      if (!missing.isEmpty()) {
        RateCache.DateRange download = new RateCache.DateRange(
            missing.getFirst().from(), missing.getLast().to());
        String currencyId = findCurrencyId(cbrClient, currency);
        List<Rate> downloaded = downloadRates(
            cbrClient, currencyId, download.from(), download.to());
        cache.storeDownload(currency, download, today.minusDays(1), downloaded);
      }
      return cache.loadRates(currency, from, to);
    }
  }

  private static String findCurrencyId(CbrClient client, String currency)
      throws IOException, InterruptedException, ParserConfigurationException, SAXException {
    Document document;
    try (InputStream input = client.downloadDailyRates()) {
      document = parseXml(input);
    }
    return nodesAsElements(document.getElementsByTagName("Valute"))
        .filter(item -> currency.equalsIgnoreCase(text(item, "CharCode")))
        .map(item -> item.getAttribute("ID"))
        .findAny()
        .orElseThrow(() -> new IllegalArgumentException(
            "Currency '%s' is not in the current CBR daily currency list.".formatted(currency)));
  }

  private static List<Rate> downloadRates(
      CbrClient client, String currencyId, LocalDate from, LocalDate to)
      throws IOException, InterruptedException, ParserConfigurationException, SAXException {
    try (InputStream input = client.downloadHistoricalRates(currencyId, from, to)) {
      return parseRates(input);
    }
  }

  static List<Rate> parseRates(InputStream xml)
      throws ParserConfigurationException, IOException, SAXException {
    Document document = parseXml(xml);
    return nodesAsElements(document.getElementsByTagName("Record"))
        .map(node -> {
          LocalDate date = LocalDate.parse(node.getAttribute("Date"), CBR_RECORD_DATE);
          BigDecimal nominal = decimal(text(node, "Nominal"));
          BigDecimal value = decimal(text(node, "Value"));
          return new Rate(date, value.divide(nominal, 10, RoundingMode.HALF_UP));
        })
        .toList();
  }

  private static Document parseXml(InputStream xml)
      throws ParserConfigurationException, IOException, SAXException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    return factory.newDocumentBuilder().parse(xml);
  }

  private static String text(Element parent, String tag) {
    NodeList nodes = parent.getElementsByTagName(tag);
    if (nodes.getLength() == 0) {
      throw new IllegalArgumentException("Missing <" + tag + "> in CBR response");
    }
    return nodes.item(0).getTextContent().trim();
  }

  private static BigDecimal decimal(String value) {
    return new BigDecimal(value.replace(',', '.'));
  }

  private static Stream<Element> nodesAsElements(NodeList nodes) {
    return IntStream.range(0, nodes.getLength()).mapToObj(nodes::item).map(Element.class::cast);
  }

}
