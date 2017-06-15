package se.sics.kompics.testing;

import com.google.common.base.Predicate;
import org.junit.Before;
import org.junit.Test;
import se.sics.kompics.Component;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;

import static org.junit.Assert.assertEquals;

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

public class blockExpect  extends TestHelper{

  private TestContext<Pinger> tc;
  private Component pinger, ponger;
  private Negative<PingPongPort> pingerPort;
  private Positive<PingPongPort> pongerPort;

  @Before
  public void init() {
    tc = TestContext.newTestContext(Pinger.class, new PingerInit(new Counter()));
    pinger = tc.getComponentUnderTest();
    ponger = tc.create(Ponger.class, new PongerInit(new Counter()));
    pingerPort = pinger.getNegative(PingPongPort.class);
    pongerPort = ponger.getPositive(PingPongPort.class);
    tc.connect(pingerPort, pongerPort);
  }

  @Test
  public void singleEventSingleBlock() {
    tc.blockExpect(ping(1), pingerPort, OUT).body()
        .trigger(ping(1), pingerPort.getPair())
    ;
    assert tc.check();
    assertEquals(1, pingsReceived(ponger));
  }

  @Test
  public void singleEventSpansNestedBlock() {
    tc.blockExpect(pong(1), pingerPort, IN).body()
        .repeat(1).body()
            .trigger(pong(1), pongerPort.getPair())
        .end()
    ;
    assert tc.check();
    assertEquals(1, pongsReceived(pinger));
  }

  @Test
  public void multipleEventsSingleBlock() {
    tc.blockExpect(ping(1), pingerPort, OUT)
        .blockExpect(ping(2), pingerPort, OUT)
        .body()
            .trigger(ping(2), pingerPort.getPair())
            .trigger(ping(1), pingerPort.getPair())
    ;

    assert tc.check();
    assertEquals(2, pingsReceived(ponger));
  }

  @Test
  public void multipleEventsSpanNestedBlock() {
    tc.blockExpect(ping(1), pingerPort, OUT)
        .blockExpect(ping(2), pingerPort, OUT)
        .body()
            .trigger(ping(2), pingerPort.getPair())

            .repeat(1).body()
                .trigger(ping(1), pingerPort.getPair())
            .end()
    ;

    assert tc.check();
    assertEquals(2, pingsReceived(ponger));
  }

  @Test
  public void multipleEventsNestedBlocks() {
    tc.blockExpect(ping(1), pingerPort, OUT)
        .blockExpect(ping(2), pingerPort, OUT)
        .body()
            .repeat(1)
                .blockExpect(ping(3), pingerPort, OUT)
                .blockExpect(ping(4), pingerPort, OUT)
            .body()
                .repeat(1).body()
                    .trigger(ping(2), pingerPort.getPair())
                    .trigger(ping(3), pingerPort.getPair())
                    .trigger(ping(4), pingerPort.getPair())
                .end()
            .end()
        .trigger(ping(1), pingerPort.getPair())
    ;
    assert tc.check();
    assertEquals(4, pingsReceived(ponger));
  }

  @Test
  public void equalEventsSingleBlock() {
    tc.blockExpect(ping(1), pingerPort, OUT)
        .blockExpect(ping(1), pingerPort, OUT)
        .body()
            .trigger(ping(1), pingerPort.getPair())
            .trigger(ping(1), pingerPort.getPair())
    ;
    assert tc.check();
    assertEquals(2, pingsReceived(ponger));
  }

  @Test
  public void equalEventsNestedBlock() {
    tc.blockExpect(ping(1), pingerPort, OUT)
        .blockExpect(ping(1), pingerPort, OUT)
        .body()
        .repeat(1).body()
            .trigger(ping(1), pingerPort.getPair())
            .trigger(ping(1), pingerPort.getPair())
        .end()
    ;
    assert tc.check();
    assertEquals(2, pingsReceived(ponger));
  }

  @Test
  public void nestedEmptyBlocks() {
    tc.body()
        .trigger(ping(1), pingerPort.getPair())
        .trigger(ping(2), pingerPort.getPair())
        .trigger(ping(3), pingerPort.getPair())
        .repeat(1).blockExpect(ping(1), pingerPort, OUT).body()
            .repeat(1).blockExpect(ping(2), pingerPort, OUT).body()
                .repeat(1).blockExpect(ping(3), pingerPort, OUT).body().end()
            .end()
        .end()
    ;

    assert tc.check();
    assertEquals(3, pingsReceived(ponger));
  }

  @Test
  public void repeat() {
    int N = 3;
    tc.body()
        .repeat(N)
            .blockExpect(ping(1), pingerPort, OUT)
        .body()
            .repeat(N)
                .blockExpect(ping(2), pingerPort, OUT)
            .body()
                .trigger(ping(2), pingerPort.getPair())
            .end()
            .trigger(ping(1), pingerPort.getPair())
        .end()
    ;
    assert tc.check();
    assertEquals(N + N * N, pingsReceived(ponger));
  }

  @Test
  public void failOnPendingEvents() {
    tc.blockExpect(ping(1), pingerPort, OUT)
        .blockExpect(ping(1), pingerPort, OUT)
        .body()
            .trigger(ping(1), pingerPort.getPair()) // only send one ping
    ;
    assert !tc.check();
    assertEquals(1, pingsReceived(ponger));
  }

  @Test
  public void usesMatchingFunction() {
    tc.blockExpect(Ping.class, new Predicate<Ping>() {
      @Override
      public boolean apply(Ping ping) {
        return ping.id == 1;
      }
    }, pingerPort, OUT).body()
        .trigger(ping(1), pingerPort.getPair())
    ;

    assert tc.check();
    assertEquals(1, pingsReceived(ponger));
  }
}
