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
import org.junit.Before;
import org.junit.Test;
import se.sics.kompics.Component;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class AnswerRequestsTest extends TestHelper{

  private TestContext tc;
  private Component pinger;
  private Component ponger;
  private Negative<PingPongPort> pingerPort;

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
    Positive<PingPongPort> pongerPort = ponger.getPositive(PingPongPort.class);
    tc.connect(pingerPort, pongerPort);
  }

  @Test
  public void orderedReceiveAll() {
    int N = 3;
    tc.body()
        .repeat(N).body()
            .trigger(ping(0), pingerPort.getPair())
            .trigger(ping(1), pingerPort.getPair())
            .trigger(ping(2), pingerPort.getPair())
            .trigger(ping(3), pingerPort.getPair())

            .answerRequests()
                .answerRequest(Ping.class, pingerPort, pingMapper, pingerPort)
                .answerRequest(Ping.class, pingerPort, pingMapper, pingerPort)
                .answerRequest(Ping.class, pingerPort, pingMapper, pingerPort)
                .answerRequest(Ping.class, pingerPort, pingMapper, pingerPort)
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
  public void failIfPendingRequests() {
    tc.body()
        .trigger(ping(0), pingerPort.getPair())
        .trigger(ping(1), pingerPort.getPair())
        .trigger(ping(2), pingerPort.getPair())

        // N triggered but N + 1 expected -> no responses will be triggered
        .answerRequests()
            .answerRequest(Ping.class, pingerPort, pingMapper, pingerPort)
            .answerRequest(Ping.class, pingerPort, pingMapper, pingerPort)
            .answerRequest(Ping.class, pingerPort, pingMapper, pingerPort)
            .answerRequest(Ping.class, pingerPort, pingMapper, pingerPort)
        .end()
    ;

    assert !tc.check();
    assertEquals(0, pongsReceived(pinger));
    assertEquals(0, pingsReceived(ponger));
    for (Integer i = 0; i < 3; i++) {
      assertEquals(Integer.valueOf(i), pingMapper.pingOrder.get(i));
    }
  }

  @Test
  public void orderedImmediate() {
    int N = 3;
    tc.body()
        .repeat(N).body()
            .trigger(ping(0), pingerPort.getPair())
            .answerRequest(Ping.class, pingerPort, pingMapper, pingerPort)
            .trigger(ping(1), pingerPort.getPair())
            .answerRequest(Ping.class, pingerPort, pingMapper, pingerPort)

            .trigger(ping(2), pingerPort.getPair())
            .trigger(ping(3), pingerPort.getPair())
            .answerRequest(Ping.class, pingerPort, pingMapper, pingerPort)
            .answerRequest(Ping.class, pingerPort, pingMapper, pingerPort)
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

            .unordered(false)
                .answerRequest(Ping.class, pingerPort, mapper(1), pingerPort)
                .answerRequest(Ping.class, pingerPort, mapper(2), pingerPort)
                .answerRequest(Ping.class, pingerPort, mapper(3), pingerPort)
            .end()
        .end()
    ;

    assert tc.check();
    assertEquals(3*N, pongsReceived(pinger));
    assertEquals(0, pingsReceived(ponger));
  }

  @Test
  public void unorderedImmediate() {
    int N = 3;
    tc.body()
        .repeat(N).body()
            .trigger(ping(2), pingerPort.getPair())
            .trigger(ping(3), pingerPort.getPair())
            .trigger(ping(1), pingerPort.getPair())

            .unordered(true)
                .answerRequest(Ping.class, pingerPort, mapper(1), pingerPort)
                .answerRequest(Ping.class, pingerPort, mapper(2), pingerPort)
                .answerRequest(Ping.class, pingerPort, mapper(3), pingerPort)
            .end()
        .end()
    ;

    assert tc.check();
    assertEquals(3*N, pongsReceived(pinger));
    assertEquals(0, pingsReceived(ponger));
  }

  @Test
  public void unorderedFailIfNotReceiveAllRequests() {
    tc.body()
        .trigger(ping(2), pingerPort.getPair())
        .trigger(ping(3), pingerPort.getPair())

        .unordered(false)
            .answerRequest(Ping.class, pingerPort, mapper(1), pingerPort)
            .answerRequest(Ping.class, pingerPort, mapper(2), pingerPort)
            .answerRequest(Ping.class, pingerPort, mapper(3), pingerPort)
        .end()
    ;

    assert !tc.check();
    assertEquals(0, pongsReceived(pinger));
    assertEquals(0, pingsReceived(ponger));
  }

  @Test
  public void unorderedImmediateFailIfNotReceiveAllRequests() {
    tc.body()
        .trigger(ping(2), pingerPort.getPair())
        .trigger(ping(3), pingerPort.getPair())

        .unordered(true)
        .answerRequest(Ping.class, pingerPort, mapper(1), pingerPort)
        .answerRequest(Ping.class, pingerPort, mapper(2), pingerPort)
        .answerRequest(Ping.class, pingerPort, mapper(3), pingerPort)
        .end()
    ;

    assert !tc.check();
    assertEquals(2, pongsReceived(pinger));
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

  // future

  private Fut future1 = new Fut(1);
  private Fut future2 = new Fut(2);
  private Fut future3 = new Fut(3);

  private class Fut extends Future<Ping, Pong> {
    private Pong pong;
    private int id;

    Fut(int id) {
      this.id = id;
    }

    @Override
    public boolean set(Ping ping) {
      if (ping.id != id) {
        return false;
      }
      pong = pong(ping.id);
      return true;
    }

    @Override
    public Pong get() {
      return pong;
    }
  }

  @Test
  public void orderedFuture() {
    int N = 3;
    tc.body()
        .repeat(N).body()
            .trigger(ping(1), pingerPort.getPair())
            .trigger(ping(2), pingerPort.getPair())
            .trigger(ping(3), pingerPort.getPair())

            .answerRequest(Ping.class, pingerPort, future1)
            .answerRequest(Ping.class, pingerPort, future2)

            .trigger(pingerPort, future2)
            .answerRequest(Ping.class, pingerPort, future3)

            .trigger(pingerPort, future1)
            .trigger(pingerPort, future3)

        .end()
    ;
    assert tc.check();
    assertEquals(N * 3, pongsReceived(pinger));
    assertEquals(0, pingsReceived(ponger));
  }

  @Test
  public void unorderedFuture() {
    int N = 3;
    tc.body()
        .repeat(N).body()
            .trigger(ping(1), pingerPort.getPair())
            .trigger(ping(2), pingerPort.getPair())
            .trigger(ping(3), pingerPort.getPair())

            .unordered()
                .answerRequest(Ping.class, pingerPort, future3)
                .answerRequest(Ping.class, pingerPort, future1)
                .answerRequest(Ping.class, pingerPort, future2)
            .end()

            .trigger(pingerPort, future2)
            .trigger(pingerPort, future1)
            .trigger(pingerPort, future3)
        .end()
    ;
    assert tc.check();
    assertEquals(N * 3, pongsReceived(pinger));
    assertEquals(0, pingsReceived(ponger));
  }

  @Test
  public void failIfNotOrdered() {
    tc.body()
        .trigger(ping(1), pingerPort.getPair())
        .trigger(ping(2), pingerPort.getPair())

        .answerRequest(Ping.class, pingerPort, future2)
        .answerRequest(Ping.class, pingerPort, future1)
    ;

    assert !tc.check();
    assertEquals(0, pongsReceived(pinger));
    assertEquals(0, pingsReceived(ponger));
  }

  @Test
  public void failIfPendingUnorderedRequests() {
    tc.body()
        .trigger(ping(1), pingerPort.getPair())
        .trigger(ping(2), pingerPort.getPair())

        .unordered()
            .answerRequest(Ping.class, pingerPort, future2)
            .answerRequest(Ping.class, pingerPort, future1)
            .answerRequest(Ping.class, pingerPort, future3)
        .end()

        .trigger(pingerPort, future2)
        .trigger(pingerPort, future1)
    ;

    assert !tc.check();
    assertEquals(0, pongsReceived(pinger));
    assertEquals(0, pingsReceived(ponger));
  }
}
