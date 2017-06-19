package se.sics.kompics.testing;

import com.google.common.base.Function;
import org.junit.Before;
import org.junit.Test;
import se.sics.kompics.Component;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;


public class RequestResponseTest extends TestHelper{

  private TestContext<Pinger> tc;
  private Component pinger;
  private Component ponger;
  private Negative<PingPongPort> pingerPort;
  private Positive<PingPongPort> pongerPort;

  private class PingMapper implements Function<Ping, Pong> {
    private List<Integer> pingOrder = new ArrayList<Integer>();

    @Override
    public Pong apply(Ping ping) {
      pingOrder.add(ping.id);
      return new Pong(ping.id);
    }
  }
  private PingMapper pingMapper = new PingMapper();

  @Before
  public void init() {
    tc = TestContext.newTestContext(Pinger.class, new PingerInit(new Counter()));
    pinger = tc.getComponentUnderTest();
    ponger = tc.create(Ponger.class, new PongerInit(new Counter()));
    pingerPort = pinger.getNegative(PingPongPort.class);
    pongerPort = ponger.getPositive(PingPongPort.class);
    tc.connect(pingerPort, pongerPort);
  }

  @Test
  public void orderedReceiveAll() {
    int N = 3;
    tc.body().
        repeat(N).body()
            .trigger(ping(0), pingerPort.getPair())
            .trigger(ping(1), pingerPort.getPair())
            .trigger(ping(2), pingerPort.getPair())
            .trigger(ping(3), pingerPort.getPair())

            .requestResponse()
                .setMapperForNext(1, Ping.class, pingMapper)
                .expect(pingerPort, pingerPort)
                .setMapperForNext(2, Ping.class, pingMapper)
                .expect(pingerPort, pingerPort)

                // equivalent to setMapperForNext(1, ...)
                .expect(Ping.class, pingerPort, pingerPort, pingMapper)
                .expect(pingerPort, pingerPort)
            .end()
        .end()
    ;

    assert tc.check();
    assertEquals(4*N, pongsReceived(pinger));
    assertEquals(0, pingsReceived(ponger));
    for (Integer i = 0; i < 4*N; i++) {
      assertEquals(Integer.valueOf(i % 4), pingMapper.pingOrder.get(i));
    }
  }

  @Test
  public void unorderedReceiveAll() {
    int N = 3;
    tc.body()
        .repeat(N).body()
            .trigger(ping(2), pingerPort.getPair())
            .trigger(ping(3), pingerPort.getPair())
            .trigger(ping(1), pingerPort.getPair())

            .requestResponse(REQUEST_ORDERING.UNORDERED, RESPONSE_POLICY.RECEIVE_ALL)
                .expect(Ping.class, pingerPort, pingerPort, mapper(1))
                .expect(Ping.class, pingerPort, pingerPort, mapper(2))
                .expect(Ping.class, pingerPort, pingerPort, mapper(3))
            .end()
        .end()
    ;

    assert tc.check();
    assertEquals(3*N, pongsReceived(pinger));
    assertEquals(0, pingsReceived(ponger));
  }

  @Test
  public void orderedReceiveAllFail() {
    tc.body()
          .trigger(ping(2), pingerPort.getPair())
          .trigger(ping(1), pingerPort.getPair())

          .requestResponse(REQUEST_ORDERING.ORDERED, RESPONSE_POLICY.IMMEDIATE)
              .expect(Ping.class, pingerPort, pingerPort, mapper(1))
              .expect(Ping.class, pingerPort, pingerPort, mapper(2))
          .end()
    ;

    assert !tc.check();
    assertEquals(0, pongsReceived(pinger));
    assertEquals(0, pingsReceived(ponger));
  }

  private Function<Ping, Pong> mapper(final int id) {
    return new Function<Ping, Pong>() {
      @Override
      public Pong apply(Ping ping) {
        return ping.id == id? pong(id) : null;
      }
    };
  }

}
