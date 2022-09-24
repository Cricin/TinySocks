package tinysocks.test;

import tinysocks.TinyRelay;
import tinysocks.TinySocks;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

public class TinyRelayTest {
  public static void main(String[] args) {
    System.out.println("pid=" + getPid());

    TinyRelay relay = new TinyRelay(10140);
    relay.start();
    TinySocks tinySocks = new TinySocks.Builder()
      .port(10141)
      .connectionFactory(relay.connectionFactory())
      .build();
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
