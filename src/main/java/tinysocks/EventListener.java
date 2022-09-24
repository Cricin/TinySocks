package tinysocks;

import java.io.IOException;
import java.net.Socket;

public abstract class EventListener {
  public void onTinySocksStarted(TinySocks tinySocks) {
  }

  public void onTinySocksStopped(TinySocks tinySocks) {

  }

  public void onSocksNegotiated(TinySocks tinySocks, Socket client) {

  }

  public void onConnectionEstablished(TinySocks tinySocks, Connection connection) {

  }

  public void onConnectionClosed(TinySocks tinySocks, Endpoint endpoint, IOException ex) {
  }

  public static final EventListener LOG_LISTENER = new EventListener() {
    @Override
    public void onTinySocksStarted(TinySocks tinySocks) {
      System.out.println("TinySocks running at port " + tinySocks.port() + ".");
    }

    @Override
    public void onTinySocksStopped(TinySocks tinySocks) {
      System.out.println("TinySocks stopped.");
    }

    @Override
    public void onSocksNegotiated(TinySocks tinySocks, Socket client) {
      super.onSocksNegotiated(tinySocks, client);
    }

    @Override
    public void onConnectionEstablished(TinySocks tinySocks, Connection connection) {
      System.out.println("Connection(" + connection.endpoint() + ") established.");
    }

    @Override
    public void onConnectionClosed(TinySocks tinySocks, Endpoint endpoint, IOException ex) {
      System.out.println("Connection(" + endpoint + ") closed.");
    }
  };
}