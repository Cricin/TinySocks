package tinysocks.relay;

import tinysocks.Connection;
import tinysocks.Endpoint;
import tinysocks.Util;
import tinysocks.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class Protocol {
  private static final int TYPE_DATA = 1;
  private static final int TYPE_CONNECT = 2;
  private static final int TYPE_CLOSE = 3;

  private final AtomicInteger idGenerator = new AtomicInteger(1);

  private final Socket socket;
  private final InputStream in;
  private final OutputStream out;
  private boolean closed;
  private final Object writeLock = new Object();

  private final Map<Integer, RelayConnection> connections = new HashMap<>();

  private final Map<Integer, RelayConnection> pendingConnections = new HashMap<>();

  private final Thread readerThread;

  @SuppressWarnings("FieldCanBeLocal")
  private final Runnable readerRunnable = new Runnable() {
    @Override
    public void run() {
      try {
        loopReader();
      } catch (IOException e) {
        close();
      }
    }
  };

  public Protocol(Socket socket, InputStream in, OutputStream out) {
    this.socket = socket;
    this.in = in;
    this.out = out;
    this.readerThread = new Thread(readerRunnable, "Protocol-Reader");
    this.readerThread.start();
  }

  public Connection newConnection(Endpoint endpoint) {
    if (closed) return null;
    int newId = idGenerator.getAndIncrement();
    RelayConnection connection = new RelayConnection(this, newId, endpoint);
    synchronized (pendingConnections) {
      pendingConnections.put(newId, connection);
    }

    byte[] hostBytes = endpoint.toBytes();
    sendPacket(newId, TYPE_CONNECT, hostBytes, 0, hostBytes.length);

    boolean connected = connection.waitConnectResult();
    if (connected) {
      synchronized (connections) {
        connections.put(newId, connection);
      }
    }

    return connected ? connection : null;
//    while (connection.connectState == 0) {
//      try {
//        //noinspection BusyWait
//        Thread.sleep(Long.MAX_VALUE);
//      } catch (InterruptedException ignored) {
//      }
//    }
//    if(connection.connectState == 2) {
//      synchronized (connections) {
//        connections.remove(newId);
//      }
//    }
//    return connection.connectState == 1 ? connection : null;
  }

  private void loopReader() throws IOException {
    while (!closed) {
      int connectionId = Util.readInt(in);
      int type = in.read();
      int size = Util.readUnsignedShort(in);
//      System.out.println("received: id=" + connectionId + ",type=" + type + ", size=" + size);
//      Util.logData("receive", connectionId, type, size);

      @Nullable RelayConnection connection = connections.get(connectionId);
      switch (type) {
        case TYPE_CLOSE: {
          connections.remove(connectionId);
          if (connection != null) {
            connection.close();
          }
          break;
        }
        case TYPE_CONNECT: {
          synchronized (pendingConnections) {
            RelayConnection conn = pendingConnections.remove(connectionId);
            if(conn != null) conn.onConnectStateChanged(in.read());
          }
          break;
        }
        case TYPE_DATA: {
          byte[] buffer = ByteArrayPool.take(size);
          Util.readNBytes(in, buffer, 0, size);
          if(connection != null) {
            connection.onDataReceived(buffer, size);
          }
          break;
        }
        default:
          break;
      }
    }
  }

  public void write(RelayConnection connection, byte[] buffer, int offset, int count) throws IOException {
    if (closed) throw new IOException("connection closed.");
    sendPacket(connection.connectionId(), TYPE_DATA, buffer, offset, count);
  }

  public void close(RelayConnection connection) {
    if (closed) return;
    if (socket.isClosed()) {
      close();
      return;
    }
    RelayConnection removed = connections.remove(connection.connectionId());
    if (removed != null) {
      sendPacket(connection.connectionId(), TYPE_CLOSE, null, 0, 0);
    }
  }

  public void close() {
    closed = true;
    Util.closeQuietly(socket);
    System.out.println("Relay node disconnected.");
  }

  /**
   * core send method, called frequently and concurrently
   */
  private void sendPacket(int connectionId, int type, byte[] buffer, int offset, int count) {
    synchronized (writeLock) {
//      Util.logData("send", connectionId, type, count);
      try {
        Util.writeInt(out, connectionId);
        out.write(type);
        Util.writeShort(out, (short) count);// max count is 4096
        if (count > 0) {
          out.write(buffer, offset, count);
        }
        out.flush();
      } catch (IOException e) {
        close();
      }
    }
  }
}