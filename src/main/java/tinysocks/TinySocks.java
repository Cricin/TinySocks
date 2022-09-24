package tinysocks;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public final class TinySocks {
  // configurations
  private final int port;
  private final Executor executor;
  private final Connection.Factory connectionFactory;
  private final EventListener eventListener;

  private final List<Worker> workers = new ArrayList<>();
  private boolean closed;
  private ServerSocket serverSocket;

  private TinySocks(Builder builder) {
    this.port = builder.port;
    this.connectionFactory = builder.connectionFactory;
    this.eventListener = builder.eventListener;
    this.executor = builder.executor;
  }

  public void close() {
    if (closed) return;
    closed = true;
    Util.closeQuietly(serverSocket);
    synchronized (workers) {
      workers.clear();
    }
    eventListener.onTinySocksStopped(this);
  }

  public void start() {
    if (serverSocket != null) throw new RuntimeException("can not start twice!");
    executor.execute(new Runnable() {
      @Override
      public void run() {
        Thread.currentThread().setName("TinySocks[" + port +"]");
        doStartSocksServer();
      }
    });
  }

  private void doStartSocksServer() {
    try {
      serverSocket = new ServerSocket(port);
      eventListener.onTinySocksStarted(this);
      while (!closed) {
        Socket socket = serverSocket.accept();
//        System.out.println("accepted....");
        Worker worker = new Worker(socket, this);
        synchronized (this) {
          workers.add(worker);
        }
        executor.execute(worker);
//        System.out.println("submitted....");
      }
    } catch (IOException e) {
      close();
    }
  }

  public int port() {
    return port;
  }

  private void workerFinished(Worker worker, IOException ex) {
    synchronized (this) {
      workers.remove(worker);
    }
    if(!closed) {
      eventListener.onConnectionClosed(this, worker.endpoint, ex);
    }
//    System.out.println("worker count: " + workers.size());
  }

  private static class Worker implements Runnable {
    private final Socket client;
    private final TinySocks tinySocks;
    private Connection connection;
    private volatile boolean closed;
    private Endpoint endpoint;

    public Worker(Socket socket, TinySocks tinySocks) {
      this.client = socket;
      this.tinySocks = tinySocks;
    }

    @Override
    public void run() {
      try {
        final InputStream input = client.getInputStream();
        final OutputStream output = client.getOutputStream();
        negotiation(input, output);
        tinySocks.eventListener.onSocksNegotiated(tinySocks, client);
        endpoint = Endpoint.parseSocks(input);
        connection = tinySocks.connectionFactory.newConnection(client, endpoint);
        sendConnectResult(output);
        if (connection == null) {
          throw new IOException("connection not established.");
        }
        tinySocks.eventListener.onConnectionEstablished(tinySocks, connection);
        @SuppressWarnings("resource")
        final InputStream serverIn = connection.inputStream();
        tinySocks.executor.execute(new Runnable() {
          @Override
          public void run() {
            runPipe(serverIn, output, false);
          }
        });
        runPipe(input, connection.outputStream(), true);
      } catch (IOException e) {
        close(e);
      } catch (Throwable e) {
        System.out.println("Fatal error occurred. worker will exit.");
        close(new IOException(e));
      }
    }

    private void runPipe(InputStream input, OutputStream output, boolean localToServer) {
      byte[] buffer = new byte[4096];
      int read;
      try {
        while (!closed) {
          read = input.read(buffer, 0, buffer.length);
          if (read == -1) {
            throw new IOException("read eof, localToServer=" + localToServer);
          } else {
            output.write(buffer, 0, read);
            output.flush();
          }
        }
      } catch (IOException e) {
        close(e);
      }
    }

    private void close(IOException e) {
      if (closed) return;
      closed = true;
      Util.closeQuietly(client);
      if (connection != null) connection.close();
      tinySocks.workerFinished(this, e);
    }

    private void sendConnectResult(OutputStream clientOut) throws IOException {
      byte[] response = {5/*Socks5 version*/, connection != null ? 0/*success*/ : (byte) 4/*host unreached*/, 0/*reserved*/, 1/*atype, ipv4*/};
      clientOut.write(response);
      // write server address
      clientOut.write(connection != null ? connection.remoteAddress() : new byte[4]);
      // write server port
      int port = connection != null ? connection.remotePort() : 0;
      Util.writeShort(clientOut, (short) port);
      clientOut.flush();
    }

    private void negotiation(InputStream input, OutputStream output) throws IOException {
      int version = input.read();
      int nMethods = input.read();
      byte[] methods = Util.readNBytes(input, nMethods);

      output.write(5/*Socks version 5*/);
      output.write(0 /*No Authentication*/);
      output.flush();
    }
  }

  public static class Builder {
    private Executor executor;
    private int port = 10010;
    private Connection.Factory connectionFactory = NoRelay.connectionFactory();
    private EventListener eventListener = EventListener.LOG_LISTENER;

    public Builder executor(Executor executor) {
      this.executor = executor;
      return this;
    }

    public Builder port(int port) {
      this.port = port;
      return this;
    }

    public Builder connectionFactory(Connection.Factory connectionFactory) {
      this.connectionFactory = connectionFactory;
      return this;
    }

    public Builder eventListener(EventListener eventListener) {
      this.eventListener = eventListener;
      return this;
    }

    public TinySocks build() {
      if (executor == null) {
        executor = new ThreadPoolExecutor(
          20,
          Integer.MAX_VALUE,
          60,
          TimeUnit.SECONDS,
//          new LinkedBlockingDeque<Runnable>()
          new SynchronousQueue<Runnable>()
        );
      }
      return new TinySocks(this);
    }
  }
}