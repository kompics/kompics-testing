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

import java.util.Comparator;

import static junit.framework.Assert.assertEquals;

public class MatchingFunctionTest extends TestHelper{

  private TestContext<Pinger> tc;
  private Component pinger;
  private Component ponger;
  private Negative<PingPongPort> pingerPort;
  private Positive<PingPongPort> pongerPort;

  @Before
  public void init() {
    tc = TestContext.newInstance(Pinger.class, new PingerInit(new Counter()));
    pinger = tc.getComponentUnderTest();
    ponger = tc.create(Ponger.class, new PongerInit(new Counter()));
    pingerPort = pinger.getNegative(PingPongPort.class);
    pongerPort = ponger.getPositive(PingPongPort.class);
    tc.connect(pingerPort, pongerPort);
  }

  private Comparator<Ping> pingComparatorPass = new Comparator<Ping>() {
    @Override
    public int compare(Ping p1, Ping p2) {
      return p1.id - p2.id;
    }
  };

  private Comparator<Ping> pingComparatorFail = new Comparator<Ping>() {
    @Override
    public int compare(Ping p1, Ping p2) {
      return -1;
    }
  };

  private Comparator<Pong> pongComparatorPass = new Comparator<Pong>() {
    @Override
    public int compare(Pong p1, Pong p2) {
      return p1.id - p2.id;
    }
  };

  private Comparator<Pong> pongComparatorFail = new Comparator<Pong>() {
    @Override
    public int compare(Pong p1, Pong p2) {
      return -1;
    }
  };

  @Test
  public void basicPass() {
    tc.setComparator(Ping.class, pingComparatorPass)
        .setComparator(Pong.class, pongComparatorPass)
        .body()
            .trigger(ping(0), pingerPort.getPair())
            .trigger(pong(0), pongerPort.getPair())

            .expect(ping(0), pingerPort, OUT)
            .expect(pong(0), pingerPort, IN)
    ;

    assert tc.check();
    assertEquals(1, pingsReceived(ponger));
    assertEquals(1, pongsReceived(pinger));
  }

  @Test
  public void basicFail() {
    tc.setComparator(Ping.class, pingComparatorFail)
        .setComparator(Pong.class, pongComparatorFail)
        .body()
            .trigger(ping(0), pingerPort.getPair())
            .trigger(pong(0), pongerPort.getPair())

            .expect(ping(0), pingerPort, OUT)
            .expect(pong(0), pingerPort, IN)
    ;

    assert !tc.check();
    assertEquals(0, pingsReceived(ponger));
    assertEquals(0, pongsReceived(pinger));
  }


}
