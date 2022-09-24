package tinysocks.relay;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public final class ByteArrayPool {
  private static int maxPoolSize = 30;
  private static List<byte[]> pool = new LinkedList<>();

  public static byte[] take(int arraySize) {
    synchronized (ByteArrayPool.class) {
      Iterator<byte[]> i = pool.iterator();
      while (i.hasNext()) {
        byte[] item = i.next();
        if (item.length >= arraySize) {
          i.remove();
          return item;
        }
      }
    }
    return new byte[Math.max(arraySize, 4096)];
  }

  public static void recycle(byte[] bytes) {
    synchronized (ByteArrayPool.class) {
      if (pool.size() < maxPoolSize) {
        pool.add(bytes);
      }
    }
  }
}
