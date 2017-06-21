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
import se.sics.kompics.Component;
import se.sics.kompics.Negative;

public class InspectTest extends TestHelper{

  private TestContext<Pinger> tc;
  private Negative<PingPongPort> pingerPort;
  private Counter counter;
  private Pong pong = pong(0);

  @Before
  public void init() {
    counter = new Counter();
    tc = TestContext.newInstance(Pinger.class, new PingerInit(counter));
    Component pinger = tc.getComponentUnderTest();
    pingerPort = pinger.getNegative(PingPongPort.class);
  }

  @Test
  public void singleTest() {
    tc.body()
        .inspect(new Predicate<Pinger>() {
          @Override
          public boolean apply(Pinger pinger) {
            counter.count++;
            return true;
          }
        })
    ;
    assert tc.check();
    assert counter.count == 1;
  }

  @Test
  public void withTriggerTest() {
    tc.body()
        .trigger(pong, pingerPort)
        .inspect(new Predicate<Pinger>() {
          @Override
          public boolean apply(Pinger pinger) {
            return pinger.pongsReceived.count == 1;
          }
        });

    assert tc.check();
  }

  @Test
  public void repeatTest() {
    final int N = 3;
    final Counter timesRun = new Counter();
    tc.body()
        .repeat(N).body()
            .trigger(pong, pingerPort)
            .inspect(new Predicate<Pinger>() {
              @Override
              public boolean apply(Pinger pinger) {
                timesRun.count++;
                return true;
              }
            })
        .end()

        .inspect(new Predicate<Pinger>() {
          @Override
          public boolean apply(Pinger pinger) {
            return pinger.pongsReceived.count == N;
          }
        })
    ;

    assert tc.check();
    assert timesRun.count == N;
  }

  @Test
  public void failTest() {
    tc.body()
        .inspect(new Predicate<Pinger>() {
          @Override
          public boolean apply(Pinger pinger) {
            return false;
          }
        });

    assert !tc.check();
  }
}
