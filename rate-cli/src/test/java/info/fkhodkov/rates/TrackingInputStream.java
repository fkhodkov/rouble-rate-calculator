package info.fkhodkov.rates;

import java.io.ByteArrayInputStream;
import java.io.IOException;

final class TrackingInputStream extends ByteArrayInputStream {
  private boolean closed;

  TrackingInputStream(byte[] bytes) {
    super(bytes);
  }

  public boolean isClosed() {
    return closed;
  }

  @Override
  public void close() throws IOException {
    closed = true;
    super.close();
  }
}
