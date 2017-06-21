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
import se.sics.kompics.Positive;

import static org.junit.Assert.assertEquals;


public class UnorderedTest extends TestHelper{

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

  @Test
  public void singleEventOut() {
    int N = 3;
    tc.body().repeat(N).body()
        .trigger(ping(0), pingerPort.getPair())
        .unordered()
            .expect(ping(0), pingerPort, OUT)
        .end()
    .end()
    ;
    assert tc.check();
    assertEquals(N, pingsReceived(ponger));
  }

  @Test
  public void singleEventIn() {
    int N = 3;
    tc.body().repeat(N).body()
        .trigger(pong(0), pongerPort.getPair())
        .unordered()
            .expect(pong(0), pingerPort, IN)
        .end()
    .end()
    ;
    assert tc.check();
    assertEquals(N, pongsReceived(pinger));
  }

  @Test
  public void multipleEvents() {
    int N = 3;
    tc.body().repeat(N).body()
        .trigger(ping(4), pingerPort.getPair())
        .trigger(pong(3), pongerPort.getPair())
        .trigger(ping(2), pingerPort.getPair())
        .trigger(pong(1), pongerPort.getPair())

        .unordered()
            .expect(pong(1), pingerPort, IN)
            .expect(ping(2), pingerPort, OUT)
            .expect(pong(3), pingerPort, IN)
            .expect(ping(4), pingerPort, OUT)
        .end()
    .end()
    ;

    assert tc.check();
    assertEquals(2*N, pongsReceived(pinger));
    assertEquals(2*N, pingsReceived(ponger));
  }

  @Test
  public void matchingFunction() {
    int N = 3;
    tc.body().repeat(N).body()
        .trigger(pong(2), pongerPort.getPair())
        .trigger(ping(1), pingerPort.getPair())

        .unordered()
            .expect(Ping.class, new Predicate<Ping>() {
              @Override
              public boolean apply(Ping ping) {
                return ping.id == 1;
              }
            }, pingerPort, OUT)

            .expect(Pong.class, new Predicate<Pong>() {
              @Override
              public boolean apply(Pong pong) {
                return pong.id == 2;
              }
            }, pingerPort, IN)
        .end()
    .end()
    ;

    assert tc.check();
    assertEquals(N, pongsReceived(pinger));
    assertEquals(N, pingsReceived(ponger));
  }

  @Test
  public void intermediateEvents() {
    int N = 3;
    tc.body().repeat(N).body()
        .trigger(ping(1), pingerPort.getPair())
        .trigger(pong(1), pongerPort.getPair())
        .trigger(ping(2), pingerPort.getPair())
        .trigger(pong(2), pongerPort.getPair())

        .expect(ping(1), pingerPort, OUT)
        .unordered()
            .expect(ping(2), pingerPort, OUT)
            .expect(pong(1), pingerPort, IN)
        .end()
        .expect(pong(2), pingerPort, IN)
    .end()
    ;

    assert tc.check();
    assertEquals(2*N, pongsReceived(pinger));
    assertEquals(2*N, pingsReceived(ponger));
  }
}
