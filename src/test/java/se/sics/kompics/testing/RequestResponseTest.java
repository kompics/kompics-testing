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

import org.junit.Test;
import se.sics.kompics.Component;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;

import static org.junit.Assert.assertEquals;

public class RequestResponseTest extends TestHelper{

  private PingRequest ping = rping(1);
  private PongResponse pong = rpong(1, ping);
  private PingRequest ping2 = rping(2);
  private PongResponse pong2 = rpong(2, ping2);

  Component pinger, pinger2, ponger;

  private Negative<PingPongPort> pingerPort;
  private Negative<PingPongPort> pinger2Port;
  private Positive<PingPongPort> pongerPort;

  private void init(TestContext tc){
    pongerPort = ponger.getPositive(PingPongPort.class);
    pingerPort = pinger.getNegative(PingPongPort.class);
    pinger2Port = pinger2.getNegative(PingPongPort.class);

    tc.connect(pingerPort, pongerPort);
    tc.connect(pinger2Port, pongerPort);
  }

  @Test
  public void outgoingRequestTest() {
    TestContext<Pinger> tc = TestContext.newTestContext(Pinger.class, new PingerInit(new Counter()));
    pinger = tc.getComponentUnderTest();
    pinger2 = tc.create(Pinger.class, new PingerInit(new Counter()));
    ponger = tc.create(Ponger.class);

    init(tc);

    int N = 5;

    tc.body().repeat(N).body()
        .trigger(ping, pingerPort.getPair()) // response is delivered only to pinger
        .trigger(ping2, pinger2Port.getPair()) // response is delivered only to pinger2
        .expect(ping, pingerPort, OUT)
        .expect(pong, pingerPort, IN)
    .end()
    ;
    assert tc.check();
    assertEquals(N, ((Pinger) pinger.getComponent()).pongsReceived.count);
    assertEquals(N, ((Pinger) pinger2.getComponent()).pongsReceived.count);
  }

  @Test
  public void outgoingResponseTest() {
    TestContext<Ponger> tc = TestContext.newTestContext(Ponger.class);
    ponger = tc.getComponentUnderTest();
    pinger = tc.create(Pinger.class, new PingerInit(new Counter()));
    pinger2 = tc.create(Pinger.class, new PingerInit(new Counter()));

    init(tc);

    int N = 5;

    tc.body().repeat(N).body()
        .trigger(ping, pingerPort.getPair())
        .trigger(ping2, pinger2Port.getPair())
        .expect(ping, pongerPort, IN)
        .expect(ping2, pongerPort, IN)
        .expect(pong, pongerPort, OUT)
        .expect(pong2, pongerPort, OUT)
    .end()
    ;

    assert tc.check();
    assertEquals(N, ((Pinger) pinger2.getComponent()).pongsReceived.count);
    assertEquals(N, ((Pinger) pinger.getComponent()).pongsReceived.count);
  }
}
