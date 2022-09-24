package tinysocks;

import tinysocks.relay.Protocol;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

public final class TinyRelay {
  private static final byte[] HELLO = "tiny_relay(v0.0.1)@".getBytes(Util.ASCII);
  private final int port;
  private Protocol protocol;
  private Thread thread;
  private ServerSocket serverSocket;

  public TinyRelay(int port) {
    this.port = port;
  }

  public void start() {
    if (thread != null) {
      throw new RuntimeException("already started!");
    }
    thread = new Thread(new Runnable() {
      @Override
      public void run() {
        runServer();
      }
    }, "TinyRelay[" + port + "]");
    thread.start();
  }

  private void runServer() {
    try {
      serverSocket = new ServerSocket(port);
      System.out.println("TinyRelay running at port " + port + ".");
      while (protocol == null) {
        Socket socket = serverSocket.accept();
        InputStream in = socket.getInputStream();
        byte[] hello = Util.readNBytes(in, HELLO.length);
        if (!Arrays.equals(hello, HELLO)) {
          throw new IOException("unknown node");
        }
        String nodeName = new String(Util.readNBytes(in, in.read()), Util.ASCII);
        System.out.println("Relay node ["+ nodeName +"] connected.");
        protocol = new Protocol(socket, socket.getInputStream(), socket.getOutputStream());
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void waitRelayNodeOnline() {
  }

  public Connection.Factory connectionFactory() {
    return new Connection.Factory() {
      @Override
      public Connection newConnection(Socket socksClient, Endpoint endpoint) {
        return protocol != null ? protocol.newConnection(endpoint) : null;
      }
    };
  }
}