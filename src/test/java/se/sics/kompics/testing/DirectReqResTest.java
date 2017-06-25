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
import se.sics.kompics.Unsafe;

import static org.junit.Assert.assertEquals;

public class DirectReqResTest extends TestHelper{

  private Component pinger, ponger;
  private TestContext tc;
  private Negative<PingPongPort> pingerPort;
  private Positive<PingPongPort> pongerPort;
  private Counter pongsReceived, pingsReceived;
  private PingerInit pingerInit;
  private PongerInit pongerInit;

  @Before
  public void init() {
    pingsReceived = new Counter();
    pongsReceived = new Counter();
    pingerInit = new PingerInit(pongsReceived);
    pongerInit = new PongerInit(pingsReceived);
  }

  @Test
  public void outReqinResTest() {
    outReqInResSetup();
    int N = 3;
    DirectPing reqA = dPing(1), reqB = dPing(2);
    DirectPong resA = dPong(1), resB = dPong(2);

    tc.body()
        .repeat(N).body()
            .trigger(reqA, pingerPort.getPair())
        .end()
        .trigger(reqB, pingerPort.getPair())

        .repeat(N).body()
            .expect(reqA, pingerPort, OUT)
        .end()
        .expect(reqB, pingerPort, OUT)

        .repeat(N).body()
            .expect(resA, pingerPort, IN)
        .end()
        .expect(resB, pingerPort, IN)
     ;
    assert tc.check();
    assertEquals(N + 1, pingsReceived.count);
    assertEquals(N + 1, pongsReceived.count);
  }

  private void outReqInResSetup() {
    tc = TestContext.newInstance(Pinger.class, pingerInit);
    pinger = tc.getComponentUnderTest();
    ponger = tc.create(Ponger.class, pongerInit);
    pingerPort = pinger.getNegative(PingPongPort.class);
    pongerPort = ponger.getPositive(PingPongPort.class);
    tc.connect(pingerPort, pongerPort);
  }

  @Test
  public void outResInReqTest() {
    outResInReqSetup();
    final DirectPing reqA = dPing(1), reqB = dPing(2);
    DirectPong resA = dPong(1), resB = dPong(2);
    Unsafe.setOrigin(reqB, pingerPort);

    // reset origin for ping used in loop otherwise, multiple ghost ports are created
    EntryFunction resetOrigin = new EntryFunction() {
      @Override
      public void run() {
        Unsafe.setOrigin(reqA, pingerPort);
      }
    };

    tc.body()
        .repeat(3, resetOrigin).body()
            .trigger(reqA, pingerPort.getPair())
        .end()
        .trigger(reqB, pingerPort.getPair())

        .repeat(3).body()
            .expect(reqA, pongerPort, IN)
        .end()
        .expect(reqB, pongerPort, IN)

        .repeat(3).body()
            .expect(resA, pongerPort, OUT)
        .end()
        .expect(resB, pongerPort, OUT)
    ;
    assert tc.check();
  }

  private void outResInReqSetup() {
    tc = TestContext.newInstance(Ponger.class, pongerInit);
    ponger = tc.getComponentUnderTest();
    pinger = tc.create(Pinger.class, pingerInit);
    pingerPort = pinger.getNegative(PingPongPort.class);
    pongerPort = ponger.getPositive(PingPongPort.class);
    tc.connect(pingerPort, pongerPort);
  }
}
