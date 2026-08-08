package info.fkhodkov.rates.cbr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CbrRateSourceTest {
  @Test
  void parsesCurrentAndHistoricalRatesAndClosesResponses() throws Exception {
    FakeClient client = new FakeClient();
    CbrRateSource source = new CbrRateSource(client);

    var current = source.currentRate("JPY");
    List<info.fkhodkov.rates.core.Rate> historical = source.historicalRates(
        "JPY", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3));

    assertEquals(new BigDecimal("0.5325000000"), current.rublesPerUnit());
    assertEquals(new BigDecimal("0.5400000000"), historical.getFirst().rublesPerUnit());
    assertTrue(client.daily.closed);
    assertTrue(client.historical.closed);
  }

  @Test
  void rejectsExternalEntities() {
    CbrRateSource source = new CbrRateSource(new FakeClient() {
      @Override
      public InputStream downloadDailyRates() {
        return stream("""
            <!DOCTYPE ValCurs [<!ENTITY external SYSTEM "file:///etc/passwd">]>
            <ValCurs Date="03.08.2026">
              <Valute ID="R01820"><CharCode>JPY</CharCode><Nominal>100</Nominal><Value>&external;</Value></Valute>
            </ValCurs>
            """);
      }
    });

    IOException error = assertThrows(IOException.class, () -> source.currentRate("JPY"));

    assertEquals("Invalid CBR XML response", error.getMessage());
  }

  private static class FakeClient implements CbrClient {
    private CloseTrackingStream daily;
    private CloseTrackingStream historical;

    @Override
    public InputStream downloadDailyRates() {
      daily = stream("""
          <ValCurs Date="03.08.2026">
            <Valute ID="R01820"><CharCode>JPY</CharCode><Nominal>100</Nominal><Value>53,25</Value></Valute>
          </ValCurs>
          """);
      return daily;
    }

    @Override
    public InputStream downloadHistoricalRates(String id, LocalDate from, LocalDate to) {
      historical = stream("""
          <ValCurs ID="R01820">
            <Record Date="03.08.2026"><Nominal>100</Nominal><Value>54,00</Value></Record>
          </ValCurs>
          """);
      return historical;
    }

    protected static CloseTrackingStream stream(String xml) {
      return new CloseTrackingStream(xml.getBytes(StandardCharsets.UTF_8));
    }
  }

  private static final class CloseTrackingStream extends ByteArrayInputStream {
    private boolean closed;

    private CloseTrackingStream(byte[] bytes) {
      super(bytes);
    }

    @Override
    public void close() throws IOException {
      closed = true;
      super.close();
    }
  }
}
