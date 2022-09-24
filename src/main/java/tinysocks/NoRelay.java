package tinysocks;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public final class NoRelay {
  private NoRelay() {
    throw new RuntimeException("no instance.");
  }

  public static Connection.Factory connectionFactory() {
    return new Connection.Factory() {
      @Override
      public Connection newConnection(Socket socksClient, Endpoint endpoint) throws IOException {
        Socket socket = Util.connectSocket(endpoint);
        return socket != null ? new DirectConnection(socket, endpoint) : null;
      }
    };
  }

  private static final class DirectConnection implements Connection {
    private final Socket socket;
    private final Endpoint endpoint;

    private DirectConnection(Socket socket, Endpoint endpoint) {
      this.socket = socket;
      this.endpoint = endpoint;
    }

    @Override
    public void close() {
      Util.closeQuietly(socket);
    }

    @Override
    public byte[] remoteAddress() {
      return ((InetSocketAddress) socket.getRemoteSocketAddress()).getAddress().getAddress();
    }

    @Override
    public int remotePort() {
      return ((InetSocketAddress) socket.getRemoteSocketAddress()).getPort();
    }

    @Override
    public Endpoint endpoint() {
      return endpoint;
    }

    @Override
    public InputStream inputStream() throws IOException {
      return socket.getInputStream();
    }

    @Override
    public OutputStream outputStream() throws IOException {
      return socket.getOutputStream();
    }
  }
}