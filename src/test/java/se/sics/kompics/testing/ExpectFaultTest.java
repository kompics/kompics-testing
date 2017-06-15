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

import com.google.common.base.Predicate;
import org.junit.Before;
import org.junit.Test;
import static junit.framework.Assert.assertEquals;

import se.sics.kompics.Component;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;

public class ExpectFaultTest extends TestHelper{
  private TestContext<TestHelper.Pinger> tc;
  private Component pinger, ponger;
  private Negative<TestHelper.PingPongPort> pingerPort;
  private Positive<TestHelper.PingPongPort> pongerPort;

  @Before
  public void init() {
    tc = TestContext.newTestContext(TestHelper.Pinger.class, new PingerInit(new Counter()));
    pinger = tc.getComponentUnderTest();
    ponger = tc.create(TestHelper.Ponger.class, new PongerInit(new Counter()));
    pingerPort = pinger.getNegative(TestHelper.PingPongPort.class);
    pongerPort = ponger.getPositive(TestHelper.PingPongPort.class);
    tc.connect(pingerPort, pongerPort);
  }

  @Test
  public void matchByClass() {
    int N = 3;
    tc.body()
        .repeat(N).body()
            .trigger(pong(0), pongerPort.getPair())
            .expect(pong(0), pingerPort, IN)
            .trigger(pong(-1), pongerPort.getPair())
            .expect(pong(-1), pingerPort, IN) // throws error
            .expectFault(IllegalStateException.class)
        .end()
    ;
    assert tc.check();
    assertEquals(N*2, pongsReceived(pinger));
  }

  @Test
  public void matchByEvent() {
    int N = 3;
    tc.body()
        .repeat(N).body()
            .trigger(pong(0), pongerPort.getPair())
            .expect(pong(0), pingerPort, IN)
            .trigger(pong(-1), pongerPort.getPair())
            .expect(pong(-1), pingerPort, IN) // throws error
            .expectFault(new Predicate<Throwable>() {
              @Override
              public boolean apply(Throwable throwable) {
                return throwable instanceof IllegalStateException;
              }
            })
        .end()
    ;
    assert tc.check();
    assertEquals(N*2, pongsReceived(pinger));
  }

  @Test
  public void failIfNoFaultThrown() {
    tc.body()
        .trigger(ping(0), pingerPort.getPair())
        .expect(ping(0), pingerPort, OUT)
        .expectFault(IllegalStateException.class) // no exception is actually thrown
    ;
    assert !tc.check();
    assertEquals(1, pingsReceived(ponger));
  }

  @Test
  public void failIfUnexpectedFault() {
    tc.body()
        .trigger(pong(-1), pongerPort.getPair())
        .trigger(pong(1), pongerPort.getPair())

        .expect(pong(-1), pingerPort, IN) // this causes exception
        .expect(pong(1), pingerPort, IN) // this should not be delivered
    ;
    assert !tc.check();
    assertEquals(1, pongsReceived(pinger));
  }

  @Test
  public void catchFaultThrownBetweenEvents() {
    int N = 3;
    tc
        .allow(pong(0), pingerPort, IN)
        .allow(pong(-1), pingerPort, IN)
        .body()
        .repeat(N).body()
            .trigger(pong(0), pongerPort.getPair())
        .end()

        .trigger(pong(-1), pongerPort.getPair()) // check that error thrown here is caught before next pong in queue
        .trigger(pong(1), pongerPort.getPair())

        .expectFault(IllegalStateException.class)
        .expect(pong(1), pingerPort, IN)
    ;
    assert tc.check();
    assertEquals(N + 2, pongsReceived(pinger));
  }
}
