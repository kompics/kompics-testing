/**
 * This file is part of the Kompics Testing runtime.
 *
 * Copyright (C) 2017 Swedish Institute of Computer Science (SICS)
 * Copyright (C) 2017 Royal Institute of Technology (KTH)
 *
 * Kompics is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package se.sics.kompics.testing;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import org.junit.Test;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Negative;
import se.sics.kompics.PortType;
import se.sics.kompics.Positive;

import se.sics.kompics.testing.pingpong.Ping;
import se.sics.kompics.testing.pingpong.PingComparator;
import se.sics.kompics.testing.pingpong.Pong;
import se.sics.kompics.testing.pingpong.PongComparator;

import static se.sics.kompics.testing.Direction.*;

public class ConditionalTest {
  private TestContext<Pinger> tc = TestContext.newInstance(Pinger.class, Init.NONE);
  private Component ponger = tc.create(Ponger.class, Init.NONE);
  private Component pinger = tc.getComponentUnderTest();
  private Negative<PingPongPort> pingerPort = pinger.getNegative(PingPongPort.class);
  private Positive<PingPongPort> pongerPort = ponger.getPositive(PingPongPort.class);
  {
    tc.connect(pingerPort, pongerPort);
    tc.setComparator(Ping.class, new PingComparator());
    tc.setComparator(Pong.class, new PongComparator());
  }

  @Test
  public void basicEitherTest() {
    tc.body().repeat(10).body()
        .trigger(pong(0), pongerPort.getPair())
        .trigger(pong(1), pongerPort.getPair())
        .trigger(pong(2), pongerPort.getPair())
        .trigger(pong(4), pongerPort.getPair())
        .trigger(pong(5), pongerPort.getPair())
        .trigger(ping(6), pingerPort.getPair());

    basicExpect();
  }

  @Test
  public void basicOrTest() {
    tc.body().repeat(10).body()
        .trigger(pong(0), pongerPort.getPair())
        .trigger(pong(1), pongerPort.getPair())
        .trigger(ping(2), pingerPort.getPair())
        .trigger(ping(3), pingerPort.getPair())
        .trigger(ping(4), pingerPort.getPair())
        .trigger(ping(5), pingerPort.getPair())
        .trigger(pong(1), pongerPort.getPair())
        .trigger(ping(6), pingerPort.getPair());

    basicExpect();
  }

  private void basicExpect() {
    tc.expect(pong(0), pingerPort, IN);

    tc.either()
        .expect(pong(1), pingerPort, IN)
        .expect(pong(2), pingerPort, IN)
        .expect(pong(4), pingerPort, IN)
        .expect(pong(5), pingerPort, IN)
    .or()
        .expect(pong(1), pingerPort, IN)
        .expect(ping(2), pingerPort, OUT)
        .expect(ping(3), pingerPort, OUT)
        .expect(ping(4), pingerPort, OUT)
        .expect(ping(5), pingerPort, OUT)
        .expect(pong(1), pingerPort, IN)
    .end();

    tc.expect(ping(6), pingerPort, OUT).end(); // end repeat

    assert tc.check();
  }

  @Test
  public void nestedEither1() {
    tc.body().repeat(10).body()
        .trigger(pong(0), pongerPort.getPair())
        .trigger(pong(1), pongerPort.getPair())
        .trigger(pong(2), pongerPort.getPair())
        .trigger(pong(3), pongerPort.getPair())
        .trigger(pong(9), pongerPort.getPair())
        .trigger(ping(10), pingerPort.getPair())
        .trigger(ping(6), pingerPort.getPair());

    nestedExpect();
  }

  @Test
  public void nestedEither2() {
    tc.body().repeat(10).body()
        .trigger(pong(0), pongerPort.getPair())
        .trigger(pong(1), pongerPort.getPair())
        .trigger(pong(2), pongerPort.getPair())
        .trigger(pong(3), pongerPort.getPair())
        .trigger(ping(11), pingerPort.getPair())
        .trigger(ping(6), pingerPort.getPair());

    nestedExpect();
  }

  @Test
  public void nestedEither3() {
    tc.body().repeat(10).body()
        .trigger(pong(0), pongerPort.getPair())
        .trigger(pong(1), pongerPort.getPair())
        .trigger(pong(2), pongerPort.getPair())
        .trigger(pong(3), pongerPort.getPair())
        .trigger(ping(6), pingerPort.getPair());

    nestedExpect();
  }

  @Test
  public void nestedEither4() {
    tc.body().repeat(10).body()
        .trigger(pong(0), pongerPort.getPair())
        .trigger(pong(1), pongerPort.getPair())
        .trigger(pong(2), pongerPort.getPair())
        .trigger(pong(3), pongerPort.getPair())
        .trigger(ping(11), pingerPort.getPair())
        .trigger(ping(6), pingerPort.getPair());

    nestedExpect();
  }

  @Test
  public void nestedOrTest1() {
    tc.body().repeat(10).body()
        .trigger(pong(0), pongerPort.getPair())
        .trigger(pong(1), pongerPort.getPair())
        .trigger(pong(2), pongerPort.getPair())
        .trigger(ping(3), pingerPort.getPair())
        .trigger(pong(5), pongerPort.getPair())
        .trigger(pong(6), pongerPort.getPair())
        .trigger(ping(6), pingerPort.getPair());

    nestedExpect();
  }

  @Test
  public void nestedOrTest2() {
    tc.body().repeat(10).body()
        .trigger(pong(0), pongerPort.getPair())
        .trigger(pong(1), pongerPort.getPair())
        .trigger(pong(2), pongerPort.getPair())
        .trigger(ping(3), pingerPort.getPair())
        .trigger(ping(4), pingerPort.getPair())
        .trigger(pong(6), pongerPort.getPair())
        .trigger(ping(6), pingerPort.getPair());

    nestedExpect();
  }

  @Test
  public void nestedOrTest3() {
    tc.body().repeat(10).body()
        .trigger(pong(0), pongerPort.getPair())
        .trigger(pong(1), pongerPort.getPair())
        .trigger(pong(2), pongerPort.getPair())
        .trigger(ping(5), pingerPort.getPair())
        .trigger(pong(6), pongerPort.getPair())
        .trigger(ping(6), pingerPort.getPair());

    nestedExpect();
  }

  @Test
  public void nestedOrTest4() {
    tc.body().repeat(10).body()
        .trigger(pong(0), pongerPort.getPair())
        .trigger(pong(1), pongerPort.getPair())
        .trigger(pong(2), pongerPort.getPair())
        .trigger(ping(6), pingerPort.getPair())
        .trigger(pong(6), pongerPort.getPair())
        .trigger(ping(6), pingerPort.getPair());

    nestedExpect();
  }

  private void nestedExpect() {
    tc.expect(pong(0), pingerPort, IN);

    tc.either()
        .expect(pong(1), pingerPort, IN)
        .expect(pong(2), pingerPort, IN)
        .either()
            .expect(pong(3), pingerPort, IN)
            .either()
              .expect(pong(9), pingerPort, IN)
              .expect(ping(10), pingerPort, OUT)
            .or()
              .expect(ping(11), pingerPort, OUT)
            .end()
        .or()
            .expect(pong(3), pingerPort, IN)
        .end()
    .or()
        .expect(pong(1), pingerPort, IN)
        .expect(pong(2), pingerPort, IN)
        .either()
            .either()
                .expect(ping(3), pingerPort, OUT)
                .expect(pong(5), pingerPort, IN)
            .or()
                .expect(ping(3), pingerPort, OUT)
                .expect(ping(4), pingerPort, OUT)
            .end()
        .or()
            .either()
                .expect(ping(5), pingerPort, OUT)
            .or()
                .expect(ping(6), pingerPort, OUT)
            .end()
        .end()
        .expect(pong(6), pingerPort, IN)
    .end();

    tc.expect(ping(6), pingerPort, OUT).end(); // end repeat

    assert tc.check();
  }

  @Test
  public void basicNestedLoopTest() {

    tc.body();
    tc.trigger(pong(3), pongerPort.getPair());

    tc.either()
        .expect(pong(3), pingerPort, IN)
        .either()
            .expect(pong(9), pingerPort, IN)
            .expect(ping(10), pingerPort, OUT)
        .or()
            .expect(ping(11), pingerPort, OUT)
        .end()
    .or()
        .expect(pong(3), pingerPort, IN)
    .end();
    assert tc.check();
  }

  @Test
  public void conditionalUnorderedEitherTest() {
    tc.body().repeat(10).body()
        .trigger(pong(2), pongerPort.getPair())
        .trigger(pong(1), pongerPort.getPair())
        .trigger(ping(6), pingerPort.getPair())
        .trigger(pong(4), pongerPort.getPair())
        .trigger(ping(5), pingerPort.getPair())
        .trigger(pong(1), pongerPort.getPair())
        .trigger(pong(2), pongerPort.getPair());

    conditionalUnorderedTest();
  }

  @Test
  public void conditionalUnorderedOrTest() {
    tc.body().repeat(10).body()
        .trigger(pong(2), pongerPort.getPair())
        .trigger(pong(1), pongerPort.getPair())
        .trigger(ping(1), pingerPort.getPair())
        .trigger(pong(2), pongerPort.getPair())
        .trigger(ping(3), pingerPort.getPair())
        .trigger(pong(2), pongerPort.getPair());

    conditionalUnorderedTest();
  }

  private void conditionalUnorderedTest() {
    tc.expect(pong(2), pingerPort, IN);
    tc.either()
        .expect(pong(1), pingerPort, IN)
        .unordered()
            .expect(pong(4), pingerPort, IN)
            .expect(ping(5), pingerPort, OUT)
            .expect(ping(6), pingerPort, OUT)
        .end()
        .expect(pong(1), pingerPort, IN)
    .or()
        .expect(pong(1), pingerPort, IN)
        .unordered()
            .expect(ping(3), pingerPort, OUT)
            .expect(ping(1), pingerPort, OUT)
            .expect(pong(2), pingerPort, IN)
        .end()
    .end();
    tc.expect(pong(2), pingerPort, IN).end(); // end repeat

    assert tc.check();
  }

  @Test
  public void conditionalExpectMapperTest() {
    tc.body().repeat(10).body()
        .trigger(ping(1), pingerPort.getPair())
        .trigger(ping(4), pingerPort.getPair())
        .trigger(ping(2), pingerPort.getPair())
        .trigger(ping(3), pingerPort.getPair())
        .trigger(ping(3), pingerPort.getPair())
        .trigger(ping(5), pingerPort.getPair())
        .trigger(pong(9), pongerPort.getPair());

    conditionalExpectWithResponse();
  }

  @Test
  public void conditionalExpectFutureTest() {
    tc.body().repeat(10).body()
        .trigger(ping(1), pingerPort.getPair())
        .trigger(ping(6), pingerPort.getPair())
        .trigger(ping(2), pingerPort.getPair())
        .trigger(ping(3), pingerPort.getPair())
        .trigger(ping(3), pingerPort.getPair())
        .trigger(ping(7), pingerPort.getPair())
        .trigger(pong(9), pongerPort.getPair());

    conditionalExpectWithResponse();
  }

  private void conditionalExpectWithResponse() {
    tc.expect(ping(1), pingerPort, OUT);
    tc.either()
        .expect(ping(4), pingerPort, OUT)

        .answerRequest(Ping.class, pingerPort, pingPongMapper, pingerPort)
        .answerRequest(Ping.class, pingerPort, pingPongMapper, pingerPort)
        .answerRequest(Ping.class, pingerPort, pingPongMapper, pingerPort)

        .expect(ping(5), pingerPort, OUT)
    .or()
        .expect(ping(6), pingerPort, OUT)

        .answerRequest(Ping.class, pingerPort, future1)
        .answerRequest(Ping.class, pingerPort, future2)
        .answerRequest(Ping.class, pingerPort, future3)
        .trigger(pingerPort, future2)
        .trigger(pingerPort, future1)
        .trigger(pingerPort, future3)

        .expect(ping(7), pingerPort, OUT)
    .end();
    tc.expect(pong(9), pingerPort, IN).end(); // end repeat

    assert tc.check();
  }

  @Test
  public void conditionalTriggerInternalTransitionTest() {
    tc.body();
    tc.trigger(pong(4), pongerPort.getPair());
    conditionalTrigger();
  }

  private void conditionalTrigger() {
    tc.either()
        .expect(pong(4), pingerPort, IN)
        .expect(pong(3), pingerPort, IN)
        .either()
            .trigger(pong(5), pongerPort.getPair())
            .expect(pong(5), pingerPort, IN)
        .or()
            .trigger(pong(6), pongerPort.getPair())
            .expect(pong(6), pingerPort, IN)
        .end()
        .trigger(pong(9), pongerPort.getPair())
    .or()
        .expect(pong(4), pingerPort, IN)
        .trigger(pong(7), pongerPort.getPair())
        .expect(pong(7), pingerPort, IN)
        .trigger(pong(9), pongerPort.getPair())
    .end();
    tc.expect(pong(9), pingerPort, IN);
    assert tc.check();
  }

  @Test
  public void conditionalInspectEitherTest() {
    tc.body();
    tc.trigger(pong(0), pongerPort.getPair());
    conditionalInspect();
  }

  @Test
  public void conditionalInspectOrTest() {
    tc.body();
    tc.trigger(pong(1), pongerPort.getPair());
    conditionalInspect();
  }

  private void conditionalInspect() {
    Pong pong = pong(2);
    tc.either()
        .expect(pong(0), pingerPort, IN)
        .trigger(pong, pingerPort)
        .trigger(pong, pingerPort)
        .inspect(new Predicate<Pinger>() {
          @Override
          public boolean apply(Pinger pinger) {
            System.out.println(pinger.pongsReceived);
            return pinger.pongsReceived == 3;
          }
        })
        .trigger(pong, pingerPort)
        .inspect(new Predicate<Pinger>() {
          @Override
          public boolean apply(Pinger pinger) {
            return pinger.pongsReceived == 4;
          }
        })
    .or()
        .expect(pong(1), pingerPort, IN)
        .trigger(pong, pingerPort)
        .inspect(new Predicate<Pinger>() {
          @Override
          public boolean apply(Pinger pinger) {
            return pinger.pongsReceived == 2;
          }
        })
    .end();

    assert tc.check();
  }

  @Test
  public void conditionalRepeatTest() {
    tc.body()
        .repeat(3)
        .body()
            .trigger(ping(0), pingerPort.getPair())
        .end()
        .either()
            .repeat(3)
            .body()
                .expect(ping(0), pingerPort, OUT)
            .end()
        .or()
            .expect(ping(0), pingerPort, OUT)
        .end()
    ;

    assert tc.check();
  }

  @Test
  public void conditionalKleeneTest() {
    tc.body()
        .either()
            .repeat()
            .body()
                .expect(ping(0), pingerPort, OUT)
            .end()
        .or()
            .expect(ping(0), pingerPort, OUT)
        .end()
    ;

    assert tc.check();
  }

  private PFuture future1 = new PFuture();
  private PFuture future2 = new PFuture();
  private PFuture future3 = new PFuture();

  private class PFuture extends Future<Ping, Pong> {
    Pong pong;

    @Override
    public boolean set(Ping request) {
      pong = pingPongMapper.apply(request);
      return true;
    }

    @Override
    public Pong get() {
      return pong;
    }
  };

  private Function<Ping, Pong> pingPongMapper = new Function<Ping, Pong>() {
    @Override
    public Pong apply(Ping ping) {
      return new Pong(ping.count);
    }
  };


  public static Ping ping(int i) {
    return new Ping(i);
  }

  public static Pong pong(int i) {
    return new Pong(i);
  }

  public static class Pinger extends ComponentDefinition {

    int pongsReceived;
    Positive<PingPongPort> pPort = requires(PingPongPort.class);

    Handler<Pong> pongHandler = new Handler<Pong>() {
      @Override
      public void handle(Pong event) {
        pongsReceived++;
      }
    };

    {
      subscribe(pongHandler, pPort);
    }
  }

  public static class Ponger extends ComponentDefinition {
    {provides(PingPongPort.class);}
  }

  public static class PingPongPort extends PortType {
    {
      request(Ping.class);
      indication(Pong.class);
    }
  }
}
