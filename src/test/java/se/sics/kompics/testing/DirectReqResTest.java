package se.sics.kompics.testing;

import org.junit.Before;
import org.junit.Test;
import se.sics.kompics.Component;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;

import static org.junit.Assert.assertEquals;

public class DirectReqResTest extends TestHelper{

  private Component pinger, ponger;
  private TestContext tc;
  private Negative<PingPongPort> pingerPort;
  private Positive<PingPongPort> pongerPort;
  private Counter pongsReceived, pingsReceived;

  @Test
  public void outReqinResTest() {
    outReqInResSetup();
    int N = 3;
    DirectPing reqA = dPing(1), reqB = dPing(2);
    DirectPong resA = dPong(1), resB = dPong(2);

    tc.body()
        .repeat(N).body()
            .trigger(reqA, pingerPort.getPair())
        .end()
        .trigger(reqB, pingerPort.getPair())

        .repeat(N).body()
            .expect(reqA, pingerPort, OUT)
        .end()
        .expect(reqB, pingerPort, OUT)

        .repeat(N).body()
            .expect(resA, pingerPort, IN)
        .end()
        .expect(resB, pingerPort, OUT)
     ;
    assert tc.check();
    assertEquals(N + 1, pingsReceived.count);
    assertEquals(N + 1, pongsReceived.count);
  }

  private void outReqInResSetup() {
    pingsReceived = new Counter();
    pongsReceived = new Counter();
    PingerInit pingerInit = new PingerInit(pongsReceived);
    PongerInit pongerInit = new PongerInit(pingsReceived);

    tc = TestContext.newTestContext(Pinger.class, pingerInit);
    pinger = tc.getComponentUnderTest();
    ponger = tc.create(Ponger.class, pongerInit);
    pingerPort = pinger.getNegative(PingPongPort.class);
    pongerPort = ponger.getPositive(PingPongPort.class);
    tc.connect(pingerPort, pongerPort);
  }

  @Test
  public void outResInReqTest() {
    outResInReqSetup();
  }

  private void outResInReqSetup() {
    pingsReceived = new Counter();
    pongsReceived = new Counter();
    PingerInit pingerInit = new PingerInit(pongsReceived);
    PongerInit pongerInit = new PongerInit(pingsReceived);

    tc = TestContext.newTestContext(Pinger.class, pingerInit);
    pinger = tc.getComponentUnderTest();
    ponger = tc.create(Ponger.class, pongerInit);
    pingerPort = pinger.getNegative(PingPongPort.class);
    pongerPort = ponger.getPositive(PingPongPort.class);
    tc.connect(pingerPort, pongerPort);
  }
}
