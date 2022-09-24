package tinysocks;

import tinysocks.annotation.Nullable;
import tinysocks.relay.ByteArrayPool;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public final class RelayNode {
  private static final boolean DEBUG_READ = false;
  private static final boolean DEBUG_WRITE = false;
  private static final byte[] HELLO = "tiny_relay(v0.0.1)@".getBytes(Util.ASCII);

  private static final int TYPE_DATA = 1;
  private static final int TYPE_CONNECT = 2;
  private static final int TYPE_CLOSE = 3;

  private static final IOException closedBySocks = new IOException("closed by socks");

  private final String host;
  private final int port;
  private final Executor executor;

  private final String nodeName;

  private Socket socket;

  private boolean stopped;

  private boolean closed;

  private final Map<Integer, Worker> workers = new HashMap<>();

  private RelayNode(Builder builder) {
    this.host = builder.host;
    this.port = builder.port;
    this.executor = builder.executor;
    this.nodeName = builder.nodeName;
  }

  public void start() {
    synchronized (this) {
      if (socket != null) throw new RuntimeException("already started.");
      executor.execute(new Runnable() {
        @Override
        public void run() {
          doStart();
        }
      });
    }
  }

  private void doStart() {
    Thread.currentThread().setName("RelayNode#" + nodeName);
    socket = Util.connectSocket(Endpoint.ofHost(host, port));
    if (socket == null) {
      System.out.println("Cannot connect to relay server");
      return;
    }
    try {
      sendHello(socket.getOutputStream());
      loopReader(socket.getInputStream());
    } catch (IOException e) {
      close(e);
    }
  }

  // relay_node(v0.0.1)@local_node
  private void sendHello(OutputStream out) throws IOException {
    out.write(HELLO);
    byte[] nodeNameBytes = nodeName.getBytes(Util.ASCII);
    out.write(nodeNameBytes.length);
    out.write(nodeNameBytes);
    out.flush();
  }

  public boolean isConnected() {
    return socket != null && socket.isConnected();
  }

  public void stop() {
    if (stopped) return;
    stopped = true;
    Util.closeQuietly(socket);
  }

  private void loopReader(InputStream in) throws IOException {
    while (!socket.isClosed()) {
      int connectionId = Util.readInt(in);
      int type = in.read();
      int size = Util.readUnsignedShort(in);
      if (DEBUG_READ) {
        Util.debugLogPacket("receive", connectionId, type, size);
      }
      byte[] buffer = null;
      if (size > 0) {
        buffer = ByteArrayPool.take(size);
        Util.readNBytes(in, buffer, 0, size);
      }
      processPacket(connectionId, type, buffer, size);
      if (buffer != null) {
        ByteArrayPool.recycle(buffer);
      }
    }
  }

  private void processPacket(int connectionId, int type, @Nullable byte[] buffer, int size) throws IOException {
    switch (type) {
      case TYPE_CONNECT: {
        Endpoint endpoint = Endpoint.parse(buffer);
        Worker worker = new Worker(this, connectionId, endpoint);
        runWorker(worker);
        break;
      }
      case TYPE_CLOSE: {
        Worker worker = workers.get(connectionId);
        if (worker != null) {
          worker.close(closedBySocks);
        }
        break;
      }
      case TYPE_DATA: {
        Worker worker = workers.get(connectionId);
        if (worker != null) {
          worker.sendToServer(buffer, size);
        }
        break;
      }
    }
  }

  public void runWorker(Worker worker) {
    synchronized (workers) {
      workers.put(worker.connectionId, worker);
    }
    executor.execute(worker);
  }

  @SuppressWarnings("SynchronizeOnNonFinalField")
  public void sendPacket(int connectionId, int type, byte[] buffer, int offset, int size) {
    synchronized (socket) {
      if (DEBUG_WRITE) {
        Util.debugLogPacket("send", connectionId, type, size);
      }
      try {
        OutputStream out = socket.getOutputStream();
        Util.writeInt(out, connectionId);
        out.write(type);
        Util.writeShort(out, (short) size);// max count is 4096
        if (size > 0) {
          out.write(buffer, offset, size);
        }
        out.flush();
      } catch (IOException e) {
        close(e);
      }
    }
  }

  private void close(IOException e) {
    if(closed) return;
    closed = true;
    Util.closeQuietly(socket);
    if (executor instanceof ExecutorService) {
      ((ExecutorService) executor).shutdown();
    }
  }

  static class Worker implements Runnable {
    private final RelayNode node;
    private final int connectionId;
    private final Endpoint endpoint;

    private Socket socket;

    private boolean closed;

    public Worker(RelayNode node, int connectionId, Endpoint host) {
      this.node = node;
      this.connectionId = connectionId;
      this.endpoint = host;
    }

    @Override
    public void run() {
      try {
        // step1 connect to the host
        socket = Util.connectSocket(endpoint);
        // sending connect result back
        sendConnectResult(socket != null);
        if (socket == null) {
          throw new IOException("failed connect endpoint: " + endpoint);
        }
        while (!closed) {
          byte[] buffer = new byte[4096];
          InputStream in = socket.getInputStream();
          int read = in.read(buffer, 0, buffer.length);
          if (read == -1) {
            throw new IOException("closed.");
          }
          node.sendPacket(connectionId, TYPE_DATA, buffer, 0, read);
        }
      } catch (IOException e) {
        close(e);
      }
    }

    private void close(IOException ex) {
      if (closed) return;
      closed = true;
      Util.closeQuietly(socket);
      node.workerClosed(this, ex);
    }

    private void sendConnectResult(boolean succeed) {
      byte[] buffer = new byte[1];
      buffer[0] = (byte) (succeed ? 1 : 2);
      node.sendPacket(connectionId, TYPE_CONNECT, buffer, 0, buffer.length);
    }

    // todo sending data on separated thread.
    public void sendToServer(byte[] buffer, int size) {
      try {
        OutputStream out = socket.getOutputStream();
        out.write(buffer, 0, size);
        out.flush();
      } catch (IOException e) {
        close(e);
      }
    }
  }

  private void workerClosed(Worker worker, IOException ex) {
    synchronized (this) {
      workers.remove(worker.connectionId);
    }
    if (ex != closedBySocks) {
      sendPacket(worker.connectionId, TYPE_CLOSE, null, 0, 0);
    }
  }

  public static class Builder {
    private Executor executor;
    private String host = "localhost";
    private int port = 10140;
    private String nodeName = "NO_NAME";

    public Builder host(String host) {
      this.host = host;
      return this;
    }

    public Builder port(int port) {
      this.port = port;
      return this;
    }

    public Builder executor(Executor executor) {
      this.executor = executor;
      return this;
    }

    public Builder nodeName(String nodeName) {
      this.nodeName = nodeName;
      return this;
    }

    public RelayNode build() {
      if (executor == null) {
        executor = Executors.newCachedThreadPool();
      }
      return new RelayNode(this);
    }
  }
}