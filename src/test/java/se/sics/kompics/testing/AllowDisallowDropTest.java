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
