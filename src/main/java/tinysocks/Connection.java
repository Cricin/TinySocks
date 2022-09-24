package tinysocks;

import tinysocks.annotation.NonNull;
import tinysocks.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public interface Connection {
  void close();

  @NonNull
  byte[] remoteAddress();

  int remotePort();

  Endpoint endpoint();

  @NonNull
  InputStream inputStream() throws IOException;

  @NonNull
  OutputStream outputStream() throws IOException;

  interface Factory {
    @Nullable
    Connection newConnection(@NonNull Socket socksClient, @NonNull Endpoint endpoint) throws IOException;
  }
}
