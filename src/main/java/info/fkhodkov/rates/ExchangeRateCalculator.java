package info.fkhodkov.rates;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Fetches, caches, and calculates official Bank of Russia exchange-rate averages. */
final class ExchangeRateCalculator {
  private static final String BASE_URL = "https://www.cbr.ru/scripts/";
  private static final DateTimeFormatter CBR_QUERY_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
  private static final DateTimeFormatter CBR_RECORD_DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu");

  private final Clock clock;
  private final Path cachePath;

  ExchangeRateCalculator(Clock clock, Path cachePath) {
    this.clock = clock;
    this.cachePath = cachePath;
  }

  CurrentRate currentRate(String currency)
      throws GeneralSecurityException, IOException, InterruptedException,
      ParserConfigurationException, SAXException {
    Document document = parseXml(get(newHttpClient(), BASE_URL + "XML_daily.asp"));
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
      throws SQLException, GeneralSecurityException, IOException, ParserConfigurationException, InterruptedException, SAXException {
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
      throws SQLException, IOException, GeneralSecurityException, ParserConfigurationException, InterruptedException, SAXException {
    try (RateCache cache = new RateCache(cachePath)) {
      LocalDate today = LocalDate.now(clock);
      List<RateCache.DateRange> missing = cache.missingRanges(currency, from, to, today);
      if (!missing.isEmpty()) {
        RateCache.DateRange download = new RateCache.DateRange(
            missing.getFirst().from(), missing.getLast().to());
        HttpClient client = newHttpClient();
        String currencyId = findCurrencyId(client, currency);
        List<Rate> downloaded = downloadRates(client, currencyId, download.from(), download.to());
        cache.storeDownload(currency, download, today.minusDays(1), downloaded);
      }
      return cache.loadRates(currency, from, to);
    }
  }

  private static HttpClient newHttpClient() throws GeneralSecurityException, IOException {
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .sslContext(cbrSslContext())
        .build();
  }

  /** Adds CBR's current public CA root to the JDK trust anchors without replacing them. */
  private static SSLContext cbrSslContext() throws GeneralSecurityException, IOException {
    TrustManagerFactory defaults = TrustManagerFactory.getInstance(
        TrustManagerFactory.getDefaultAlgorithm());
    defaults.init((KeyStore) null);
    X509TrustManager defaultTrustManager = Stream.of(defaults.getTrustManagers())
        .filter(X509TrustManager.class::isInstance)
        .map(X509TrustManager.class::cast)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No default X.509 trust manager"));

    KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
    trustStore.load(null, null);
    int index = 0;
    for (X509Certificate certificate : defaultTrustManager.getAcceptedIssuers()) {
      trustStore.setCertificateEntry("jdk-" + index++, certificate);
    }
    try (var input = ExchangeRateCalculator.class.getResourceAsStream(
        "/certs/harica-tls-rsa-root-2021.pem")) {
      if (input == null) {
        throw new IllegalStateException("Bundled CBR CA certificate is missing");
      }
      X509Certificate haricaRoot = (X509Certificate) CertificateFactory.getInstance("X.509")
          .generateCertificate(input);
      trustStore.setCertificateEntry("harica-tls-rsa-root-2021", haricaRoot);
    }

    TrustManagerFactory combined = TrustManagerFactory.getInstance(
        TrustManagerFactory.getDefaultAlgorithm());
    combined.init(trustStore);
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, combined.getTrustManagers(), null);
    return context;
  }

  private static String findCurrencyId(HttpClient client, String currency)
      throws IOException, InterruptedException, ParserConfigurationException, SAXException {
    Document document = parseXml(get(client, BASE_URL + "XML_daily.asp"));
    return nodesAsElements(document.getElementsByTagName("Valute"))
        .filter(item -> currency.equalsIgnoreCase(text(item, "CharCode")))
        .map(item -> item.getAttribute("ID"))
        .findAny()
        .orElseThrow(() -> new IllegalArgumentException(
            "Currency '%s' is not in the current CBR daily currency list.".formatted(currency)));
  }

  private static List<Rate> downloadRates(
      HttpClient client, String currencyId, LocalDate from, LocalDate to)
      throws IOException, InterruptedException, ParserConfigurationException, SAXException {
    String url = BASE_URL + "XML_dynamic.asp?date_req1=" + encode(CBR_QUERY_DATE.format(from))
        + "&date_req2=" + encode(CBR_QUERY_DATE.format(to))
        + "&VAL_NM_RQ=" + encode(currencyId);
    return parseRates(get(client, url));
  }

  static List<Rate> parseRates(byte[] xml)
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

  private static Document parseXml(byte[] xml)
      throws ParserConfigurationException, IOException, SAXException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
  }

  private static byte[] get(HttpClient client, String url) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(30))
        .header("Accept", "application/xml,text/xml")
        .header("User-Agent", "rouble-rate-calculator/1.0")
        .GET().build();
    HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    if (response.statusCode() != 200) {
      throw new IllegalStateException("CBR HTTP request failed with status " + response.statusCode());
    }
    return response.body();
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

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static Stream<Element> nodesAsElements(NodeList nodes) {
    return IntStream.range(0, nodes.getLength()).mapToObj(nodes::item).map(Element.class::cast);
  }

}
