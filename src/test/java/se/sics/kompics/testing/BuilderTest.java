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

import java.util.Comparator;

public class BuilderTest extends TestHelper{

  private TestContext<Pinger> tc;
  private Component pinger, ponger;
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

  @Test(expected = IllegalArgumentException.class)
  public void expectEventOnWrongPort() {
    tc.body()
        .expect(ping(0), pingerPort, IN)
    ;
    assert tc.check();
  }

  @Test(expected = IllegalArgumentException.class)
  public void expectClassOnWrongPort() {
    tc.body()
        .expect(Ping.class, pingerPort, IN)
    ;
    assert tc.check();
  }

  @Test(expected = IllegalStateException.class)
  public void connectOnlyAllowedInInitialHeader() {
    assert !tc.body().connect(pingerPort, pongerPort).check();
  }

  @Test(expected = IllegalStateException.class)
  public void createOnlyAllowedInInitialHeader() {
    tc.body().create(Pinger.class);
  }

  @Test(expected = IllegalStateException.class)
  public void setDefaultActionOnlyAllowedInInitialHeader() {
    tc.body().setDefaultAction(Ping.class, new Function<Ping, Action>() {
      @Override
      public Action apply(Ping ping) {
        return Action.DROP;
      }
    });
  }

  @Test(expected = IllegalStateException.class)
  public void setComparatorOnlyAllowedInInitialHeader() {
    tc.body().setComparator(Ping.class, new Comparator<Ping>() {
      @Override
      public int compare(Ping p1, Ping p2) {
        return 0;
      }
    });
  }

  @Test(expected = IllegalStateException.class)
  public void setTimeoutOnlyAllowedInInitialHeader() {
    tc.body().setTimeout(1000);
  }
}
