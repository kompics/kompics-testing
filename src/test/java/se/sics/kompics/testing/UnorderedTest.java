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
import org.junit.Before;
import org.junit.Test;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.testing.pingpong.Ping;
import se.sics.kompics.testing.pingpong.PingComparator;
import se.sics.kompics.testing.pingpong.PingPongPort;
import se.sics.kompics.testing.pingpong.Pinger;
import se.sics.kompics.testing.pingpong.Pong;
import se.sics.kompics.testing.pingpong.PongComparator;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;

import static se.sics.kompics.testing.Direction.*;

import java.util.Random;

public class UnorderedEventsTest {

  private TestContext<Pinger> tc;
  private Negative<PingPongPort> pingerPort;

  private Component pinger, ponger1, ponger2, ponger3;

  private Ping ping = new Ping(0);
  private Pong pong1 = new Pong(1);
  private Pong pong2 = new Pong(2);
  private Pong pong3 = new Pong(3);

  @Before
  public void init() {
    tc = TestContext.newTestContext(Pinger.class, Init.NONE);
    pinger = tc.getComponentUnderTest();
    ponger1 = tc.create(Ponger.class, new PongerInit(1));
    ponger2 = tc.create(Ponger.class, new PongerInit(2));
    ponger3 = tc.create(Ponger.class, new PongerInit(3));

    pingerPort = pinger.getNegative(PingPongPort.class);
    tc.connect(pingerPort, ponger1.getPositive(PingPongPort.class));
    tc.connect(pingerPort, ponger2.getPositive(PingPongPort.class));
    tc.connect(pingerPort, ponger3.getPositive(PingPongPort.class));

    tc.setComparator(Ping.class, new PingComparator()).
        setComparator(Pong.class, new PongComparator());
  }

  private void connectTimers() {
    //Component timer1 = tc.create(JavaTimer.class, Init.NONE);
    //Component timer3 = tc.create(JavaTimer.class, Init.NONE);
    //Component timer2 = tc.create(JavaTimer.class, Init.NONE);
    //tc.connect(ponger1.getNegative(Timer.class), timer1.getPositive(Timer.class));
    //tc.connect(ponger2.getNegative(Timer.class), timer2.getPositive(Timer.class));
    //tc.connect(ponger3.getNegative(Timer.class), timer3.getPositive(Timer.class));
  }

  @Test
  public void unorderedTest() {
    connectTimers();

    tc.setDefaultAction(Ping.class, new Function<Ping, Action>() {
         @Override
         public Action apply(Ping ping) {
           return ping.count == 0? Action.HANDLE : Action.DROP;
         }
    }).body();

    tc.repeat(10).body().
       trigger(ping, pingerPort.getPair()).
       expect(ping, pingerPort, OUT).
       unordered().
            expect(pong1, pingerPort, IN).
            expect(pong2, pingerPort, IN).
            expect(pong3, pingerPort, IN).
       end().
    end();

    assert tc.check();
  }

  private ScheduleTimeout st = new ScheduleTimeout(500);
  private RandomTimeout timeout = new RandomTimeout(st);

  @Test
  public void expectWithinSingleBlockTest() {
    initExpectWithinBlock();

    tc.
        blockExpect(Pong.class, predicateForPong(1), pingerPort, IN).
        blockExpect(pong2, pingerPort, IN).
       body().
         trigger(ping, pingerPort.getPair()).
         expect(ping, pingerPort, OUT).
         trigger(timeout, ponger1.getNegative(Timer.class)).
         trigger(timeout, ponger3.getNegative(Timer.class)).
         trigger(timeout, ponger2.getNegative(Timer.class)).
         expect(pong3, pingerPort, IN);

    assert tc.check();
  }

  private Predicate<Pong> predicateForPong(final int count) {
    return new Predicate<Pong>() {
      @Override
      public boolean apply(Pong pong) {
        return pong.count == count;
      }

      @Override
      public String toString() {
        return "Predicate(Pong(" + count + "))";
      }
    };
  }

  private void initExpectWithinBlock() {
    tc.setDefaultAction(Ping.class, new Function<Ping, Action>() {
      @Override
      public Action apply(Ping ping) {
        return Action.DROP;
      }
    }); // drop pings that weren't triggered
  }

  @Test
  public void expectWithinEmptyBlockTest() {
    connectTimers();
    initExpectWithinBlock();
    tc.body().
        repeat(10).
        body().
          trigger(ping, pingerPort.getPair()).
          expect(ping, pingerPort, OUT).
          repeat(1).
            blockExpect(Pong.class, predicateForPong(1), pingerPort, IN).
            blockExpect(Pong.class, predicateForPong(2), pingerPort, IN).
            blockExpect(pong3, pingerPort, IN).
          body().
          end().
        end();
    //assertEquals(tc.check(), tc.getFinalState());
    assert tc.check();
  }

  @Test
  public void ExpectWithinMultipleBlockTest() {
    tc.setTimeout(Ponger.timeout * 2);
    connectTimers();
    initExpectWithinBlock();
    tc.body().
       repeat(10).
            blockExpect(pong1, pingerPort, IN).
       body().
          trigger(ping, pingerPort.getPair()).
          repeat(1).
            blockExpect(pong2, pingerPort, IN).
          body().
             expect(ping, pingerPort, OUT).
             repeat(1).
                blockExpect(pong3, pingerPort, IN).
             body().
             end().
          end().
       end();
    assert tc.check();
  }

  public static class Ponger extends ComponentDefinition {

    private static long timeout = 10;
    private Random random = new Random(System.nanoTime());
    private Positive<Timer> timer = requires(Timer.class);
    private Negative<PingPongPort> ppPort = provides(PingPongPort.class);
    private int id;

    public Ponger(PongerInit init) {
      id = init.id;
    }

    private Handler<Ping> pingHandler = new Handler<Ping>() {
      @Override
      public void handle(Ping ping) {
        setTimer();
      }
    };

    private Handler<RandomTimeout> timeoutHandler = new Handler<RandomTimeout>() {
      @Override
      public void handle(RandomTimeout timeout) {
        trigger(new Pong(id), ppPort);
      }
    };

    private void setTimer() {
      long delay = random.nextInt((int) timeout);
      ScheduleTimeout st = new ScheduleTimeout(delay);
      RandomTimeout timeout = new RandomTimeout(st);
      st.setTimeoutEvent(timeout);
      trigger(st, timer);
    }

    {
      subscribe(timeoutHandler, timer);
      subscribe(pingHandler, ppPort);
    }
  }

  public static class PongerInit extends Init<Ponger> {
    int id;
    public PongerInit(int id) {
      this.id = id;
    }
  }

  private static class RandomTimeout extends Timeout {
    RandomTimeout(ScheduleTimeout st) {
      super(st);
    }
  }
}
