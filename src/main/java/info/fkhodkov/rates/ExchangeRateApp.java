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
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;
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
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;

/**
 * Downloads official Bank of Russia rates and prints arithmetic averages.
 */
@Command(name = "rouble-rate-calculator",
    version = "rouble-rate-calculator 1.0.0",
    description = "Calculate average official Bank of Russia exchange rates.",
    mixinStandardHelpOptions = true,
    sortOptions = false)
public final class ExchangeRateApp implements Callable<Integer> {
  private static final String BASE_URL = "https://www.cbr.ru/scripts/";
  private static final DateTimeFormatter CBR_QUERY_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
  private static final DateTimeFormatter CBR_RECORD_DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu");
  private static final Clock clock = Clock.system(ZoneId.of("Europe/Moscow"));

  @Option(names = {"-c", "--currency"}, defaultValue = "USD", converter = CurrencyConverter.class,
      paramLabel = "CODE", description = "ISO currency code (default: ${DEFAULT-VALUE}).")
  private String currency;

  @Option(names = {"-e", "--end-date"}, paramLabel = "YYYY-MM-DD",
      description = "Inclusive end date (default: yesterday in Moscow).")
  private LocalDate endDate = LocalDate.now(clock).minusDays(1);

  @Option(names = {"-p", "--periods"}, split = ",", defaultValue = "3m",
      converter = PeriodConverter.class, paramLabel = "LIST",
      description = "Comma-separated periods using d, w, or m (default: ${DEFAULT-VALUE}).")
  private List<Period> periods;

  static void main(String[] args) {
    int exitCode = new CommandLine(new ExchangeRateApp()).execute(args);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  @Override
  public Integer call() {
    try {
      LocalDate earliest = periods.stream()
          .map(period -> period.startDate(endDate))
          .min(LocalDate::compareTo)
          .orElseThrow();
      List<Rate> rates;
      try (RateCache cache = new RateCache(cachePath())) {
        LocalDate today = LocalDate.now(clock);
        List<RateCache.DateRange> missing = cache.missingRanges(currency, earliest, endDate, today);
        if (!missing.isEmpty()) {
          RateCache.DateRange download = new RateCache.DateRange(
              missing.getFirst().from(), missing.getLast().to());
          HttpClient client = HttpClient.newBuilder()
              .connectTimeout(Duration.ofSeconds(15))
              .followRedirects(HttpClient.Redirect.NORMAL)
              .sslContext(cbrSslContext())
              .build();
          String currencyId = findCurrencyId(client, currency);
          List<Rate> downloaded = downloadRates(
              client, currencyId, download.from(), download.to());
          cache.storeDownload(currency, download, today.minusDays(1), downloaded);
        }
        rates = cache.loadRates(currency, earliest, endDate);
      }
      if (rates.isEmpty()) {
        throw new IllegalStateException("The Bank of Russia returned no rates for this period.");
      }

      System.out.printf("Official CBR %s/RUB averages through %s%n",
          currency, endDate);
      System.out.println("(arithmetic mean of published rates, RUB per 1 currency unit)");
      for (Period period : periods) {
        LocalDate from = period.startDate(endDate);
        List<Rate> periodRates = rates.stream()
            .filter(rate -> !rate.date().isBefore(from))
            .filter(rate -> !rate.date().isAfter(endDate))
            .toList();
        if (periodRates.isEmpty()) {
          System.out.printf(Locale.ROOT, "%6s: no data%n", period);
          continue;
        }
        BigDecimal average = periodRates.stream()
            .map(Rate::rublesPerUnit)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(periodRates.size()), 6, RoundingMode.HALF_UP);
        System.out.printf(Locale.ROOT, "%6s: %12s  (%d published rates, %s to %s)%n",
            period, average.toPlainString(),
            periodRates.size(), periodRates.getFirst().date(),
            periodRates.getLast().date());
      }
      return 0;
    } catch (IllegalArgumentException e) {
      System.err.println("Error: " + e.getMessage());
      return 2;
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
      System.err.println("Interrupted by user");
      return 1;
    } catch (Exception e) {
      System.err.println("Could not calculate rates: " + e.getMessage());
      return 1;
    }
  }

  private static Path cachePath() {
    String override = System.getenv("ROUBLE_RATE_DB");
    if (override != null && !override.isBlank()) {
      return Path.of(override);
    }
    return Path.of(System.getProperty("user.home"), ".cache",
        "rouble-rate-calculator", "rates.db");
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

  record Rate(LocalDate date, BigDecimal rublesPerUnit) {
  }

  public record Period(int amount, PeriodUnit unit) {
    LocalDate startDate(LocalDate endDate) {
      return switch (unit) {
        case DAY -> endDate.minusDays(amount);
        case WEEK -> endDate.minusWeeks(amount);
        case MONTH -> endDate.minusMonths(amount);
      };
    }

    @Override
    public String toString() {
      return amount + String.valueOf(unit.suffix);
    }
  }

  enum PeriodUnit {
    DAY('d'),
    WEEK('w'),
    MONTH('m'),
    ;
    private static final Map<Character, PeriodUnit> BY_SUFFIX = Arrays.stream(PeriodUnit.values())
        .collect(Collectors.toUnmodifiableMap(unit -> unit.suffix, Function.identity()));

    static final String PATTERN = Arrays.stream(PeriodUnit.values())
        .map(unit -> unit.suffix)
        .map(String::valueOf)
        .collect(Collectors.joining("", "[", "]"));

    private final char suffix;

    PeriodUnit(char suffix) {
      this.suffix = suffix;
    }

    static PeriodUnit fromSuffix(char suffix) {
      if (!BY_SUFFIX.containsKey(suffix)) {
        throw new IllegalArgumentException("Unsupported period unit: " + suffix);
      }
      return BY_SUFFIX.get(suffix);
    }
  }

  public static final class CurrencyConverter implements ITypeConverter<String> {
    @Override
    public String convert(String value) {
      String normalized = value.trim().toUpperCase(Locale.ROOT);
      if (!normalized.matches("[A-Z]{3}")) {
        throw new IllegalArgumentException(
            "currency must be a three-letter ISO code, such as USD");
      }
      return normalized;
    }
  }

  public static final class PeriodConverter implements ITypeConverter<Period> {
    @Override
    public Period convert(String value) {
      String period = value.trim().toLowerCase(Locale.ROOT);
      if (!period.matches("[1-9]\\d*" + PeriodUnit.PATTERN)) {
        throw new IllegalArgumentException(
            "period must be a positive number followed by d, w, or m: " + value);
      }
      try {
        int amount = Integer.parseInt(period.substring(0, period.length() - 1));
        return new Period(amount, PeriodUnit.fromSuffix(period.charAt(period.length() - 1)));
      } catch (NumberFormatException _) {
        throw new IllegalArgumentException("period number is too large: " + value);
      }
    }
  }
}
