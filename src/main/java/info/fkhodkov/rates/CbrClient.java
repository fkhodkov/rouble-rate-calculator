package info.fkhodkov.rates;

import java.io.IOException;
import java.io.InputStream;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/** Downloads raw XML responses from the Bank of Russia. */
final class CbrClient {
  private static final String BASE_URL = "https://www.cbr.ru/scripts/";
  private static final DateTimeFormatter QUERY_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

  private final HttpClient httpClient;

  CbrClient() throws GeneralSecurityException, IOException {
    httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .sslContext(cbrSslContext())
        .build();
  }

  InputStream downloadDailyRates() throws IOException, InterruptedException {
    return get(BASE_URL + "XML_daily.asp");
  }

  InputStream downloadHistoricalRates(String currencyId, LocalDate from, LocalDate to)
      throws IOException, InterruptedException {
    String url = BASE_URL + "XML_dynamic.asp?date_req1=" + encode(QUERY_DATE.format(from))
        + "&date_req2=" + encode(QUERY_DATE.format(to))
        + "&VAL_NM_RQ=" + encode(currencyId);
    return get(url);
  }

  private InputStream get(String url) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(30))
        .header("Accept", "application/xml,text/xml")
        .header("User-Agent", "rouble-rate-calculator/1.0")
        .GET().build();
    HttpResponse<InputStream> response = httpClient.send(
        request, HttpResponse.BodyHandlers.ofInputStream());
    if (response.statusCode() != 200) {
      response.body().close();
      throw new IOException("CBR HTTP request failed with status " + response.statusCode());
    }
    return response.body();
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
    try (var input = CbrClient.class.getResourceAsStream(
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
}
