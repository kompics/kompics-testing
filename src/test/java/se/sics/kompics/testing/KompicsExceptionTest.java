package se.sics.kompics.testing;

import org.junit.Test;
import se.sics.kompics.Component;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;

public class KompicsExceptionTest extends TestHelper{

  @Test
  public void throwsOnDirectNullOriginTest() {
    TestContext<Ponger> tc = TestContext.newInstance(Ponger.class);
    Component ponger = tc.getComponentUnderTest(), pinger = tc.create(Pinger.class);
    Negative<PingPongPort> pingerPort = pinger.getNegative(PingPongPort.class);
    Positive<PingPongPort> pongerPort = ponger.getPositive(PingPongPort.class);
    tc.connect(pingerPort, pongerPort);
    /*origin for ping0 is not set, causes null exception when in inbound handler*/
    tc.body()
        .trigger(dPing(0), pingerPort.getPair())
        .expect(dPing(0), pongerPort, IN)

    ;
    assert !tc.check();
  }
}
