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
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Negative;
import se.sics.kompics.PortType;
import se.sics.kompics.Positive;
import se.sics.kompics.testing.pingpong.Ping;
import se.sics.kompics.testing.pingpong.PingPongPort;
import se.sics.kompics.testing.pingpong.Pong;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class ExpectWithResponseTest {

  private TestContext<Pinger> tc = TestContext.newTestContext(Pinger.class, Init.NONE);
  private Component pinger = tc.getComponentUnderTest();
  private Negative<PingPongPort> pingerPort = pinger.getNegative(PingPongPort.class);
  private static int N = 3;

  @Test
  public void expectWithMapperTest() {
    Token start = new Token();
    tc.body().
            repeat(30).body().

                    // send N pings with random ids
                    trigger(start, pinger.getPositive(TokenPort.class)).
                    expectWithMapper().
                      setMapperForNext(1, Ping.class, pingPongMapper).
                      expect(pingerPort, pingerPort).
                      setMapperForNext(1, Ping.class, pingPongMapper).
                      expect(pingerPort, pingerPort).

                      // equivalent to setMapperForNext(1, ...)
                      expect(Ping.class, pingerPort, pingerPort, pingPongMapper).

                      // use different mapper for negative ping
                      setMapperForNext(1, Ping.class, negativePingMapper).
                      expect(pingerPort, pingerPort).
                    end().

        inspect(emptyIds).

                    // same sequence, different groups
                    trigger(start, pinger.getPositive(TokenPort.class)).
                    expectWithMapper().
                      setMapperForNext(2, Ping.class, pingPongMapper).
                      expect(pingerPort, pingerPort).
                      expect(pingerPort, pingerPort).
                      setMapperForNext(1, Ping.class, pingPongMapper).
                      expect(pingerPort, pingerPort).
                      setMapperForNext(1, Ping.class, negativePingMapper).
                      expect(pingerPort, pingerPort).
                    end().

        inspect(emptyIds).
            end();

    //assertEquals(tc.check(), tc.getFinalState());
    assert tc.check();
  }

  @Test
  public void expectWithFutureTest() {
    Token start = new Token();
    tc.body().
            repeat(30).body().

                    // send N pings with random ids
                    trigger(start, pinger.getPositive(TokenPort.class)).
                    expectWithFuture().
                      expect(Ping.class, pingerPort, future1).
                      expect(Ping.class, pingerPort, future2).

                      // interleave expect trigger
                      trigger(pingerPort, future2).
                      expect(Ping.class, pingerPort, future3).

                      // trigger not mandatory expected events
                      expect(Ping.class, pingerPort, negativeFuture).

                      trigger(pingerPort, future1).
                      trigger(pingerPort, future3).
                    end().

        inspect(emptyIds).

            end();

    //assertEquals(tc.check(), tc.getFinalState());
    assert tc.check();
  }

  private PFuture future1 = new PFuture(true);
  private PFuture future2 = new PFuture(true);
  private PFuture future3 = new PFuture(true);
  private PFuture negativeFuture = new PFuture(false);

  private class PFuture extends Future<Ping, Pong> {
    boolean positivePing;
    Pong pong;

    PFuture(boolean positivePing) {
      this.positivePing = positivePing;
    }

    @Override
    public void set(Ping request) {
      pong = positivePing
              ? pingPongMapper.apply(request)
              : negativePingMapper.apply(request);
    }

    @Override
    public Pong get() {
      return pong;
    }
  };

  private Predicate<Pinger> emptyIds = new Predicate<Pinger>() {
      @Override
      public boolean apply(Pinger pinger) {
        return pinger.ids.isEmpty();
      }
  };

  private Function<Ping, Pong> pingPongMapper = new Function<Ping, Pong>() {
    @Override
    public Pong apply(Ping ping) {
      return new Pong(ping.count);
    }
  };

  private Function<Ping, Pong> negativePingMapper = new Function<Ping, Pong>() {
    @Override
    public Pong apply(Ping event) {
      Ping ping = (Ping) event;
      return ping.count < 0? new Pong(((Ping)event).count) : null;
    }
  };

  public static class Pinger extends ComponentDefinition {
    private Positive<PingPongPort> ppPort = requires(PingPongPort.class);
    private Negative<TokenPort> tokenPort = provides(TokenPort.class);
    private Random random;
    private Set<Integer> ids = new HashSet<Integer>();

    private Handler<Token> tokenHandler = new Handler<Token>() {
      @Override
      public void handle(Token event) {
         random = new Random(System.nanoTime());
        ids.clear();
        for (int i = 0; i < N; i++) {
          int id = random.nextInt(100);
          ids.add(id);
          trigger(new Ping(id), ppPort);
        }
        // trigger a negative ping count
        trigger(new Ping(-random.nextInt(100)), ppPort);
      }
    };

    private Handler<Pong> pongHandler = new Handler<Pong>() {
      @Override
      public void handle(Pong pong) {
        TestContext.logger.error("pong {}", pong);
        ids.remove(pong.count);
      }
    };

    {
      subscribe(tokenHandler, tokenPort);
      subscribe(pongHandler, ppPort);
    }
  }

  public static class TokenPort extends PortType {
    {
      request(Token.class);
    }
  }

  public static class Token implements KompicsEvent {}
}
