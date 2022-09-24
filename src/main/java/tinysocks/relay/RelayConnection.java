package tinysocks.relay;

import tinysocks.Connection;
import tinysocks.Endpoint;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.Queue;

final class RelayConnection implements Connection {
  private static final int CONNECT_STATE_UNKNOWN = 0;
  private static final int CONNECT_STATE_CONNECTED = 1;
  private static final int CONNECT_STATE_FAILED = 2;

  public static final int MAX_QUEUE_BYTES = 2 * 1024 * 1024;// 2MB

  private final Protocol protocol;
  private final int connectionId;
  private final Endpoint endpoint;
  private boolean closed = false;
  private volatile Thread readThread;
  private volatile int connectState = 0;// 0: response not received yet, 1: connected, 2: failed.
  private final Queue<ReceivedData> receiveQueue = new LinkedList<>();

  private int queueBytes = 0;

  private volatile Thread receiveThread;

  RelayConnection(Protocol protocol, int connectionId, Endpoint endpoint) {
    this.protocol = protocol;
    this.connectionId = connectionId;
    this.endpoint = endpoint;
  }

  public int connectionId() {
    return connectionId;
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    protocol.close(this);
  }

  @Override
  public byte[] remoteAddress() {
    byte[] address = {1, 1, 1, 9};
    return address;
  }

  @Override
  public int remotePort() {
    return 443;
  }

  @Override
  public Endpoint endpoint() {
    return endpoint;
  }

  @Override
  public InputStream inputStream() {
    return new InputStream() {
      @Override
      public int read() {
        throw new RuntimeException("unused.");
      }

      @Override
      public int read(byte[] b, int off, int len) {
        synchronized (receiveQueue) {
          readThread = Thread.currentThread();
          while (receiveQueue.isEmpty()) {
            try {
              receiveQueue.wait();
            } catch (InterruptedException ignored) {
            }
          }
          readThread = null;
          ReceivedData data = receiveQueue.peek();
          int copied = Math.min(data.size - data.consumed, len);
          System.arraycopy(data.buffer, data.consumed, b, off, copied);
          // update state
          queueBytes -= copied;
          data.consumed += copied;
          if (data.consumed >= data.size) {
            receiveQueue.poll();
            ByteArrayPool.recycle(data.buffer);
          }
          // if receiveThread is waiting，notify it
          if (receiveThread != null) {
            receiveQueue.notify();
          }
          return copied;
        }
      }
    };
  }

  @Override
  public OutputStream outputStream() {
    return new OutputStream() {
      @Override
      public void write(int b) {
        throw new RuntimeException("unused.");
      }

      @Override
      public void write(byte[] b, int off, int len) throws IOException {
        protocol.write(RelayConnection.this, b, off, len);
      }

      @Override
      public void flush() {
        //no-op
      }
    };
  }

  public void onDataReceived(byte[] buffer, int size) {
    synchronized (receiveQueue) {
      while (queueBytes + size > MAX_QUEUE_BYTES) {// receive queue is full, consumer thread are slow
        try {
          receiveThread = Thread.currentThread();
          receiveQueue.wait();
        } catch (InterruptedException ignored) {
        }
      }
      receiveThread = null;
      ReceivedData data = new ReceivedData(buffer, size);
      queueBytes += size;
      receiveQueue.offer(data);
      if (readThread != null) {
        receiveQueue.notify();
      }
    }
  }

  public boolean waitConnectResult() {
    readThread = Thread.currentThread();
    while (connectState == CONNECT_STATE_UNKNOWN) {
      try {
        //noinspection BusyWait
        Thread.sleep(Long.MAX_VALUE);
      } catch (InterruptedException ignored) {
      }
    }
    readThread = null;
    return connectState == CONNECT_STATE_CONNECTED;
  }

  public void onConnectStateChanged(int newState) {
    connectState = newState;
    if (readThread != null) {
      readThread.interrupt();
    }
  }

  private static class ReceivedData {
    byte[] buffer;
    int size;
    int consumed;

    public ReceivedData(byte[] buffer, int size) {
      this.buffer = buffer;
      this.size = size;
      this.consumed = 0;
    }
  }
}