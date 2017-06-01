package se.sics.kompics.testing;

import org.junit.Before;
import org.junit.Test;
import se.sics.kompics.Component;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;

import static org.junit.Assert.assertEquals;

public class AllowDisallowDropTest extends TestHelper{

  private Component pinger, ponger;
  private TestContext<Pinger> tc;
  private Negative<PingPongPort> pingerPort;
  private Positive<PingPongPort> pongerPort;
  private Counter pongsReceived;

  @Before
  public void init() {
    pongsReceived = new Counter();
    PingerInit pingerInit = new PingerInit(pongsReceived);
    tc = TestContext.newTestContext(Pinger.class, pingerInit);
    pinger = tc.getComponentUnderTest();
    ponger = tc.create(Ponger.class);
    pingerPort = pinger.getNegative(PingPongPort.class);
    pongerPort = ponger.getPositive(PingPongPort.class);
    tc.connect(pingerPort, pongerPort);
  }

  @Test
  public void allowTest() {
    tc.allow(pong(0), pingerPort, IN)
        .body()
            .trigger(pong(1), pongerPort.getPair())
            .trigger(pong(0), pongerPort.getPair())
            .trigger(pong(2), pongerPort.getPair())

            .expect(pong(1), pingerPort, IN)
            .expect(pong(2), pingerPort, IN)
    ;
    assert tc.check();
    assertEquals(3, pongsReceived.count);
  }

  @Test
  public void disallowTest() {
    tc.disallow(pong(0), pingerPort, IN)
        .body()
            .trigger(pong(1), pongerPort.getPair())
            .trigger(pong(0), pongerPort.getPair())
            .trigger(pong(2), pongerPort.getPair())
            .expect(pong(1), pingerPort, IN)
            .expect(pong(2), pingerPort, IN)
    ;
    assert !tc.check();
    assertEquals(1, pongsReceived.count);
  }

  @Test
  public void dropTest() {
    tc.drop(pong(0), pingerPort, IN)
        .body()
            .trigger(pong(0), pongerPort.getPair())
            .trigger(pong(1), pongerPort.getPair())
            .trigger(pong(0), pongerPort.getPair())
            .trigger(pong(2), pongerPort.getPair())
            .expect(pong(1), pingerPort, IN)
            .expect(pong(2), pingerPort, IN)
    ;
    assert tc.check();
    assertEquals(2, pongsReceived.count);
  }

  @Test
  public void shadowTest() {
    int N = 4;
    tc.drop(pong(0), pingerPort, IN)
        .drop(pong(1), pingerPort, IN)
        .body()
        .repeat(N).body()
            .trigger(pong(0), pongerPort.getPair())
            .trigger(pong(1), pongerPort.getPair())
        .end()
        .repeat(N - 1)
            .allow(pong(0), pingerPort, IN)
        .body()
            .expect(pong(0), pingerPort, IN)
        .end()
    ;
    assert tc.check();
    assertEquals(N - 1, pongsReceived.count);
  }
}
