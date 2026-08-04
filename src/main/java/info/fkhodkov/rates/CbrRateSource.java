package info.fkhodkov.rates;

import info.fkhodkov.rates.core.CurrentRate;
import info.fkhodkov.rates.core.ExchangeRateSource;
import info.fkhodkov.rates.core.Rate;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

/** Converts raw CBR XML responses into the platform-neutral rate model. */
final class CbrRateSource implements ExchangeRateSource {
  private static final DateTimeFormatter CBR_RECORD_DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu");

  private final CbrClient client;

  CbrRateSource(CbrClient client) {
    this.client = client;
  }

  @Override
  public CurrentRate currentRate(String currency) throws IOException, InterruptedException {
    Document document;
    try (InputStream input = client.downloadDailyRates()) {
      document = parseXml(input);
    }
    LocalDate effectiveDate = LocalDate.parse(
        document.getDocumentElement().getAttribute("Date"), CBR_RECORD_DATE);
    Element item = nodesAsElements(document.getElementsByTagName("Valute"))
        .filter(candidate -> currency.equalsIgnoreCase(text(candidate, "CharCode")))
        .findAny()
        .orElseThrow(() -> unknownCurrency(currency));
    return new CurrentRate(currency, effectiveDate,
        decimal(text(item, "Value")).divide(decimal(text(item, "Nominal")), 10, RoundingMode.HALF_UP));
  }

  @Override
  public List<Rate> historicalRates(String currency, LocalDate from, LocalDate to)
      throws IOException, InterruptedException {
    String currencyId = findCurrencyId(currency);
    try (InputStream input = client.downloadHistoricalRates(currencyId, from, to)) {
      Document document = parseXml(input);
      return nodesAsElements(document.getElementsByTagName("Record"))
          .map(node -> new Rate(
              LocalDate.parse(node.getAttribute("Date"), CBR_RECORD_DATE),
              decimal(text(node, "Value")).divide(
                  decimal(text(node, "Nominal")), 10, RoundingMode.HALF_UP)))
          .toList();
    }
  }

  private String findCurrencyId(String currency) throws IOException, InterruptedException {
    Document document;
    try (InputStream input = client.downloadDailyRates()) {
      document = parseXml(input);
    }
    return nodesAsElements(document.getElementsByTagName("Valute"))
        .filter(item -> currency.equalsIgnoreCase(text(item, "CharCode")))
        .map(item -> item.getAttribute("ID"))
        .findAny()
        .orElseThrow(() -> unknownCurrency(currency));
  }

  private static Document parseXml(InputStream xml) throws IOException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      return factory.newDocumentBuilder().parse(xml);
    } catch (ParserConfigurationException | SAXException e) {
      throw new IOException("Invalid CBR XML response", e);
    }
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

  private static IllegalArgumentException unknownCurrency(String currency) {
    return new IllegalArgumentException(
        "Currency '%s' is not in the current CBR daily currency list.".formatted(currency));
  }
}
