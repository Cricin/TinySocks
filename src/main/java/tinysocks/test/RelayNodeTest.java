package tinysocks.test;

import tinysocks.RelayNode;

public class RelayNodeTest {
  public static void main(String[] args) {
    RelayNode node = new RelayNode.Builder()
      .port(10140)
      .nodeName("localhost")
      .build();
    node.start();
  }
}
