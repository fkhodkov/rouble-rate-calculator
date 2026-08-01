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
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
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

/**
 * Downloads official Bank of Russia rates and prints arithmetic averages.
 */
public final class ExchangeRateApp {
  private static final String BASE_URL = "https://www.cbr.ru/scripts/";
  private static final DateTimeFormatter CBR_QUERY_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
  private static final DateTimeFormatter CBR_RECORD_DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu");
  private static final int[] PERIODS = {1, 3, 6, 12};
  private static final Clock clock = Clock.system(ZoneId.of("Europe/Moscow"));

  private ExchangeRateApp() {
  }

  static void main(String[] args) {
    try {
      Arguments options = parseArguments(args);
      if (options.help()) {
        printHelp();
        return;
      }

      HttpClient client = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(15))
          .followRedirects(HttpClient.Redirect.NORMAL)
          .sslContext(cbrSslContext())
          .build();
      String currencyId = findCurrencyId(client, options.currency());
      LocalDate earliest = options.endDate().minusMonths(12);
      List<Rate> rates = downloadRates(client, currencyId, earliest, options.endDate());
      if (rates.isEmpty()) {
        throw new IllegalStateException("The Bank of Russia returned no rates for this period.");
      }

      System.out.printf("Official CBR %s/RUB averages through %s%n",
          options.currency(), options.endDate());
      System.out.println("(arithmetic mean of published rates, RUB per 1 currency unit)");
      for (int months : PERIODS) {
        LocalDate from = options.endDate().minusMonths(months);
        List<Rate> periodRates = rates.stream()
            .filter(rate -> !rate.date().isBefore(from))
            .filter(rate -> !rate.date().isAfter(options.endDate()))
            .toList();
        if (periodRates.isEmpty()) {
          System.out.printf(Locale.ROOT, "%2d month%s: no data%n",
              months, months == 1 ? " " : "s");
          continue;
        }
        BigDecimal average = periodRates.stream()
            .map(Rate::rublesPerUnit)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(periodRates.size()), 6, RoundingMode.HALF_UP);
        System.out.printf(Locale.ROOT, "%2d month%s: %12s  (%d published rates, %s to %s)%n",
            months, months == 1 ? " " : "s", average.toPlainString(),
            periodRates.size(), periodRates.getFirst().date(),
            periodRates.getLast().date());
      }
    } catch (IllegalArgumentException e) {
      System.err.println("Error: " + e.getMessage());
      System.err.println("Use --help for usage.");
      System.exit(2);
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
      System.err.println("Interrupted by user");
      System.exit(1);
    } catch (Exception e) {
      System.err.println("Could not calculate rates: " + e.getMessage());
      System.exit(1);
    }
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
    try (var input = ExchangeRateApp.class.getResourceAsStream("/certs/harica-tls-rsa-root-2021.pem")) {
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
    byte[] xml = get(client, BASE_URL + "XML_daily.asp");
    Document document = parseXml(xml);
    NodeList currencies = document.getElementsByTagName("Valute");
    return nodesAsElements(currencies)
        .filter(item -> currency.equalsIgnoreCase(text(item, "CharCode")))
        .map(item -> item.getAttribute("ID"))
        .findAny()
        .orElseThrow(() -> new IllegalArgumentException("Currency '%s' is not in the current CBR daily currency list.".formatted(currency)));
  }

  private static List<Rate> downloadRates(HttpClient client, String currencyId, LocalDate from, LocalDate to)
      throws IOException, InterruptedException, ParserConfigurationException, SAXException {
    String url = BASE_URL + "XML_dynamic.asp?date_req1=" + encode(CBR_QUERY_DATE.format(from))
                 + "&date_req2=" + encode(CBR_QUERY_DATE.format(to))
                 + "&VAL_NM_RQ=" + encode(currencyId);
    return parseRates(get(client, url));
  }

  static List<Rate> parseRates(byte[] xml) throws ParserConfigurationException, IOException, SAXException {
    Document document = parseXml(xml);
    NodeList nodes = document.getElementsByTagName("Record");
    return nodesAsElements(nodes)
        .map(node -> {
          LocalDate date = LocalDate.parse(node.getAttribute("Date"), CBR_RECORD_DATE);
          BigDecimal nominal = decimal(text(node, "Nominal"));
          BigDecimal value = decimal(text(node, "Value"));
          return new Rate(date, value.divide(nominal, 10, RoundingMode.HALF_UP));
        })
        .toList();
  }

  private static Document parseXml(byte[] xml) throws ParserConfigurationException, IOException, SAXException {
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
    return IntStream.range(0, nodes.getLength())
        .mapToObj(nodes::item)
        .map(Element.class::cast);
  }

  private static void printHelp() {
    System.out.println("""
        Usage: java -jar target/rouble-rate-calculator-1.0.0.jar [CURRENCY] [END_DATE]
          CURRENCY  ISO code from the CBR daily list (default: USD)
          END_DATE  YYYY-MM-DD, inclusive (default: today)
        Examples:
          java -jar target/rouble-rate-calculator-1.0.0.jar
          java -jar target/rouble-rate-calculator-1.0.0.jar EUR 2026-07-31""");
  }

  static Arguments parseArguments(String[] args) {
    if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
      return new Arguments("USD", LocalDate.now(clock), true);
    }
    if (args.length > 2) {
      throw new IllegalArgumentException("Expected at most two arguments.");
    }
    String currency = args.length >= 1 ? args[0].trim().toUpperCase(Locale.ROOT) : "USD";
    if (!currency.matches("[A-Z]{3}")) {
      throw new IllegalArgumentException("Currency must be a three-letter ISO code, such as USD.");
    }
    try {
      LocalDate end = args.length == 2 ? LocalDate.parse(args[1]) : LocalDate.now(clock);
      return new Arguments(currency, end, false);
    } catch (DateTimeParseException _) {
      throw new IllegalArgumentException("End date must use YYYY-MM-DD format.");
    }
  }

  record Rate(LocalDate date, BigDecimal rublesPerUnit) {
  }

  private record Arguments(String currency, LocalDate endDate, boolean help) {
  }
}
