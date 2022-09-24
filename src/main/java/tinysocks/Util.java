package tinysocks;

import tinysocks.annotation.NonNull;
import tinysocks.annotation.Nullable;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@SuppressWarnings("PointlessBitwiseExpression")
public class Util {
  public static final Charset ASCII = StandardCharsets.US_ASCII;

  public static void closeQuietly(@Nullable Closeable c) {
    if (c != null) {
      try {
        c.close();
      } catch (IOException ignored) {
      }
    }
  }

  public static int readUnsignedShort(@NonNull InputStream in) throws IOException {
    int ch1 = in.read();
    int ch2 = in.read();
    if ((ch1 | ch2) < 0)
      throw new EOFException();
    return (ch1 << 8) + (ch2 << 0);
  }

  public static void writeShort(@NonNull OutputStream out, int value) throws IOException {
    out.write((value >>> 8) & 0xFF);
    out.write((value >>> 0) & 0xFF);
  }

  public static int readInt(@NonNull InputStream in) throws IOException {
    int ch1 = in.read();
    int ch2 = in.read();
    int ch3 = in.read();
    int ch4 = in.read();
    if ((ch1 | ch2 | ch3 | ch4) < 0)
      throw new EOFException();
    return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
  }

  public static void writeInt(@NonNull OutputStream out, int v) throws IOException {
    out.write((v >>> 24) & 0xFF);
    out.write((v >>> 16) & 0xFF);
    out.write((v >>> 8) & 0xFF);
    out.write((v >>> 0) & 0xFF);
  }

  @Nullable
  public static Socket connectSocket(@NonNull Endpoint endpoint) {
    Socket socket = new Socket();
    try {
      InetAddress address;
      if (endpoint.hostname != null) {
        address = InetAddress.getByName(endpoint.hostname);
      } else if (endpoint.ip != null) {
        address = InetAddress.getByAddress(endpoint.ip);
      } else {
        throw new IOException("Unknown endpoint "+ endpoint);
      }
      socket.connect(new InetSocketAddress(address, endpoint.port), 5000);
      return socket;
    } catch (IOException e) {
      Util.closeQuietly(socket);
    } catch (IllegalArgumentException e) {
      System.out.println(endpoint);
      e.printStackTrace();
    }
    return null;
  }

  public static int readNBytes(@NonNull InputStream in,
                               @NonNull byte[] buffer,
                               int position, int N) throws IOException {
    int n = 0;
    while (n < N) {
      int count = in.read(buffer, position + n, N - n);
      if (count < 0)
        break;
      n += count;
    }
    return n;
  }

  public static byte[] readNBytes(@NonNull InputStream in, int N) throws IOException {
    byte[] bytes = new byte[N];
    readNBytes(in, bytes, 0, N);
    return bytes;
  }

  public static void debugLogPacket(String prefix, int id, int type, int size) {
    StringBuilder result = new StringBuilder(prefix)
      .append(": id=")
      .append(id)
      .append(", type=");
    switch (type) {
      case 1: {
        result.append("data    ");
        break;
      }
      case 2: {
        result.append("connect ");
        break;
      }
      case 3 : {
        result.append("close   ");
        break;
      }
    }
    result.append(", size=").append(size);
    System.out.println(result);
  }
}
