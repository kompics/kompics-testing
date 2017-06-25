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

public class KleeneTest extends TestHelper{

  private final Counter counter = new Counter();
  private EntryFunction increment = new EntryFunction() {
    @Override
    public void run() {
      counter.count++;
    }
  };

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

  @Test
  public void basicKleene() {
    int N = 3;
    tc.body()
        .repeat(N).body()
            .trigger(ping(0), pingerPort.getPair())
        .end()

        .trigger(ping(0), pingerPort.getPair())

        .repeat().body()
            .expect(ping(0), pingerPort, OUT)
        .end()
    ;

    assert tc.check();
    assertEquals(N + 1, pingsReceived(ponger));
  }

  @Test
  public void initSupport() {
    int N = 3;
    tc.body()
        .repeat(N).body()
            .trigger(ping(0), pingerPort.getPair())
        .end()

        .trigger(ping(0), pingerPort.getPair()) // + 1

        .repeat(increment).body()
            .expect(ping(0), pingerPort, OUT)
        .end()
    ;

    assert tc.check();
    assertEquals(N + 1, pingsReceived(ponger));
    assertEquals(N + 1, counter.count);
  }

  @Test
  public void withBlockExpect() {
    tc.blockExpect(ping(3), pingerPort, OUT)
        .blockExpect(ping(4), pingerPort, OUT)
        .body()
            .trigger(ping(0), pingerPort.getPair())
            .trigger(ping(0), pingerPort.getPair())
            .trigger(ping(0), pingerPort.getPair())
            .trigger(ping(4), pingerPort.getPair()) // block expect
            .trigger(ping(1), pingerPort.getPair())

            .repeat().body()
                .expect(ping(0), pingerPort, OUT)
            .end()

        .expect(ping(1), pingerPort, OUT)
        .trigger(ping(3), pingerPort.getPair()) // block expect
    ;

    assert tc.check();
    assertEquals(6, pingsReceived(ponger));
  }

  @Test
  public void nestedKleene() {
    int N = 3;
    tc.body()
        .repeat(N).body()
            .trigger(ping(0), pingerPort.getPair())
            .trigger(ping(1), pingerPort.getPair())
        .end()

        .repeat(increment).body()
            .expect(ping(0), pingerPort, OUT)
            .repeat(increment).body()
                .expect(ping(1), pingerPort, OUT)
            .end()
        .end()
    ;

    assert tc.check();
    assertEquals(N * 2, pingsReceived(ponger));
    assertEquals(N * 2, counter.count);
  }

  @Test
  public void initIsNotRunUnlessBlockIsEntered() {
    int N = 3;
    tc.body()
        .repeat(N).body()
            .trigger(ping(0), pingerPort.getPair())
        .end()

        .repeat(increment).body()
            .expect(ping(0), pingerPort, OUT)
            .repeat(increment).body() // init is not run here
                .expect(ping(1), pingerPort, OUT)
            .end()
        .end()
    ;

    assert tc.check();
    assertEquals(N, pingsReceived(ponger));
    assertEquals(N, counter.count);
  }

  @Test
  public void nestedRepeat() {
    int N = 3;
    tc.body()
        .repeat(N).body()
            .trigger(ping(0), pingerPort.getPair())
            .repeat(N).body()
                .trigger(ping(1), pingerPort.getPair())
                .trigger(ping(2), pingerPort.getPair())
            .end()
        .end()

        .repeat(increment).body()
            .expect(ping(0), pingerPort, OUT)
            .repeat(N, increment).body()
                .expect(ping(1), pingerPort, OUT)
                .expect(ping(2), pingerPort, OUT)
            .end()
        .end()
    ;

    assert tc.check();
    assertEquals(N * N * 2 + N, pingsReceived(ponger));
    assertEquals(N * N  + N, counter.count);
  }

  @Test
  public void nestedWithinRepeat() {
    int N = 3;
    tc.body()
        .repeat(N).body()
            .trigger(ping(0), pingerPort.getPair())
            .repeat(N).body()
                .trigger(ping(1), pingerPort.getPair())
                .trigger(ping(2), pingerPort.getPair())
            .end()
        .end()

        .repeat(N, increment).body()
            .expect(ping(0), pingerPort, OUT)
            .repeat(increment).body()
                .expect(ping(1), pingerPort, OUT)
                .expect(ping(2), pingerPort, OUT)
            .end()
        .end()
    ;

    assert tc.check();
    assertEquals(N * N * 2 + N, pingsReceived(ponger));
    assertEquals(N * N  + N, counter.count);
  }
}
