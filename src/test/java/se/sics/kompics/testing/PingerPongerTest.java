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

import com.google.common.base.Supplier;
import org.junit.Before;
import org.junit.Test;
import static junit.framework.Assert.assertEquals;

import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Negative;
import se.sics.kompics.PortType;
import se.sics.kompics.Positive;
import se.sics.kompics.Request;
import se.sics.kompics.Start;

import static org.junit.Assert.assertTrue;
import static se.sics.kompics.testing.Direction.OUT;

public class PingerPongerTest {

  private TestContext<Pinger> tc;
  private Component pinger;
  private Component ponger;
  private Negative<PingPongPort> pingerPort;
  private Positive<PingPongPort> pongerPort;

  private class Counter {
    int i = 0;
  }

  private Counter counter = new Counter();

  private EntryFunction increment = new EntryFunction() {
    @Override
    public void run() {
      counter.i++;
    }
  };

  @Test
  public void triggerWithSupplierTest() {
    int M = 5;
    Supplier<Ping> supplier = new Supplier<Ping>() {
      int i;
      @Override
      public Ping get() {
        counter.i++;
        return new Ping(++i);
      }
    };
    tc.connect(pingerPort, pongerPort).body()
        .repeat(M)
        .body()
            .trigger(supplier, pingerPort.getPair())
        .end();

    for (int i = 1; i <= M; i++) {
      tc.expect(new Ping(i), pingerPort, OUT);
    }

    assertTrue(tc.check());
    assertEquals(counter.i, M);
  }

  @Test
  public void entryFunctionTest() {
    int M = 5, N = 2;
    tc.connect(pingerPort, pongerPort).body()
        .repeat(M)
        .body()
            .repeat(N, increment)
            .body()
                .trigger(new Ping(0), pingerPort.getPair())
                .expect(new Ping(0), pingerPort, OUT)
            .end()
        .end()

    ;
    assert tc.check();
    assertEquals(counter.i, M * N);
  }

  @Test
  public void entryFunctionNestedTest() {

    int M = 2, N = 1;
    tc.connect(pingerPort, pongerPort).body()
        .repeat(M, increment)
        .body()
            .repeat(N, increment)
            .body()
                .trigger(new Ping(0), pingerPort.getPair())
                .expect(new Ping(0), pingerPort, OUT)
            .end()

            .repeat(N, increment).body().end()
            .repeat(N, increment).body().end()
        .end();

    assert tc.check();
    assertEquals(counter.i, (M * 3 * N) + M);
  }

  @Test
  public void nestedEntryFunctionsTest() {
    int A = 3, B = 4, C = 5, D = 6, E = 7;
    tc.body()
        .repeat(A, increment) // A
        .body()
            .repeat(B, increment) // A * B
            .body()
                .repeat(C, increment) // A * B * C
                .body()
                    .repeat(D, increment).body().end() // A * B * C * D
                .end()
                .repeat(7, increment).body().end() // A * B * E
            .end()
        .end()
    ;
    assert tc.check();
    assertEquals(counter.i, A + (A*B) + (A*B*C) + (A * B * E) + (A*B*C*D));
  }

  @Test
  public void defaultActionTest() {
    tc.setDefaultAction(Ping.class, new Function<Ping, Action>() {
        @Override
        public Action apply(Ping ping) {
          if (ping.count < 3) {
            return Action.HANDLE;
          }
          return Action.FAIL;
        }
    });

    tc.setDefaultAction(KompicsEvent.class, new Function<KompicsEvent, Action>() {
        @Override
        public Action apply(KompicsEvent event) {
          return Action.FAIL;
        }
    });

    int M = 3, N = 30;
    tc.connect(pingerPort, pongerPort).body()
        .repeat(M)
        .body()
            .repeat(N, increment)
            .body()
                .trigger(new Ping(0), pingerPort.getPair())
                .trigger(new Ping(0), pingerPort.getPair())
                .trigger(new Ping(0), pingerPort.getPair())
                .trigger(new Ping(0), pingerPort.getPair())
                .trigger(new Ping(1), pingerPort.getPair())
                .expect(new Ping(1), pingerPort, OUT)
            .end()
        .end()
    ;
    assert tc.check();
    assertEquals(counter.i, M * N);
  }

  @Before
  public void init() {
    tc = TestContext.newInstance(Pinger.class, Init.NONE);
    pinger = tc.getComponentUnderTest();
    ponger = tc.create(Ponger.class, Init.NONE);
    pingerPort = pinger.getNegative(PingPongPort.class);
    pongerPort = ponger.getPositive(PingPongPort.class);
  }

  public static class Pinger extends ComponentDefinition {
    static int counter = 0;

    Positive<PingPongPort> ppPort = requires(PingPongPort.class);

    Handler<Pong> pongHandler = new Handler<Pong>() {
      @Override
      public void handle(Pong event) {
      }
    };

    Handler<Start> startHandler = new Handler<Start>() {
      @Override
      public void handle(Start event) {
      }
    };

    {
      subscribe(pongHandler, ppPort);
      subscribe(startHandler, control);
    }
  }

  public static class Ponger extends ComponentDefinition {
    Negative<PingPongPort> pingPongPort = provides(PingPongPort.class);

    Handler<Ping> pingHandler = new Handler<Ping>() {
      @Override
      public void handle(Ping ping) { }
    };

    {
      subscribe(pingHandler, pingPongPort);
    }
  }

  public static class PingPongPort extends PortType {
    {
      request(Ping.class);
      indication(Pong.class);
    }
  }

  static class Ping extends Request {
    int count;
    Ping(int count) { this.count = count; }

    public boolean equals(Object o) {
      return o instanceof Ping && ((Ping) o).count == count;
    }

    public int hashCode() {
      return count;
    }

    public String toString() {
      return "" + count;
    }
  }

  static class Pong implements KompicsEvent {

    int count;
    Pong(int count) { this.count = count; }

    public boolean equals(Object o) {
      return o instanceof Pong && ((Pong)o).count == count;
    }

    public int hashCode() {
      return count;
    }

    public String toString() {
      return "" + count;
    }
  }
}
