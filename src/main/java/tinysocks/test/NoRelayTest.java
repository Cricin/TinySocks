package tinysocks.test;

import tinysocks.TinySocks;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

public class NoRelayTest {
  public static void main(String[] args) {
    System.out.println("pid=" + getPid());
    TinySocks tinySocks = new TinySocks.Builder().port(10141).build();
    tinySocks.start();
  }

  private static int getPid() {
    RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
    String name = runtime.getName();
    try {
      return Integer.parseInt(name.substring(0, name.indexOf('@')));
    } catch (Exception e) {
      return -1;
    }
  }
}
