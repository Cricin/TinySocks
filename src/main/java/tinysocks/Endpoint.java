package tinysocks;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public final class Endpoint {

  private Endpoint(String hostname, byte[] ip, int port) {
    this.hostname = hostname;
    this.ip = ip;
    this.port = port;
  }

  public final String hostname;
  public final byte[] ip;
  public final int port;

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder(32);
    if (hostname != null) {
      builder.append(hostname).append(":");
    }
    if (ip != null) {
      builder.append(ip[0]).append(".");
      builder.append(ip[1]).append(".");
      builder.append(ip[2]).append(".");
      builder.append(ip[3]);
      builder.append(":");
    }
    builder.append(port);
    return builder.toString();
  }

  public byte[] toBytes() {
    ByteArrayOutputStream out = new ByteArrayOutputStream(20);
    try {
      if (hostname != null) {
        out.write(1);
        byte[] bytes = hostname.getBytes(Util.ASCII);
        out.write(bytes.length);
        out.write(bytes);
      } else {
        out.write(2);
        out.write(ip);
      }
      Util.writeShort(out, port);
      return out.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException(e); //this should never happen
    }
  }

  public static Endpoint parse(byte[] buffer) throws IOException {
    ByteArrayInputStream in = new ByteArrayInputStream(buffer);
    int hostType = in.read();
    String hostname = null;
    byte[] ip = null;
    if(hostType == 1) {
      hostname = new String(Util.readNBytes(in, in.read()), Util.ASCII);
    } else {
      ip = Util.readNBytes(in, 4);
    }
    int port = Util.readUnsignedShort(in);
    return new Endpoint(hostname, ip, port);
  }

  public static Endpoint parseSocks(InputStream in) throws IOException {
    byte[] bytes = Util.readNBytes(in, 4);
    if (bytes[0] != 5 && bytes[1] != 1) {
      throw new IOException("Unsupported socks command");
    }
    String hostname = null;
    byte[] ip = null;
    switch (bytes[3]) {
      case 1:
        ip = Util.readNBytes(in, 4);
        break;
      case 3:
        hostname = new String(Util.readNBytes(in, in.read()), Util.ASCII);
        break;
      default:
        throw new IOException("Unsupported host type: " + bytes[3]);
    }
    int port = Util.readUnsignedShort(in);
    return new Endpoint(hostname, ip, port);
  }

  public InetSocketAddress toSocketAddress() throws IOException {
    InetAddress address;
    if (hostname != null) {
      address = InetAddress.getByName(hostname);
    } else if (ip != null) {
      address = InetAddress.getByAddress(ip);
    } else {
      throw new IOException("Unknown endpoint " + this);
    }
    return new InetSocketAddress(address, port);
  }

  public static Endpoint ofHost(String hostname, int port) {
    return new Endpoint(hostname, null, port);
  }

  public static String socketAddressToString(SocketAddress address) {
    return "";
  }
}
