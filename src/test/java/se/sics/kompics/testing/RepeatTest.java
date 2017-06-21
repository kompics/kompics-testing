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

public class RepeatTest extends TestHelper{

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
  public void repeatBasic() {
    int N = 3;
    tc.body()
        .repeat(N).body()
            .trigger(ping(0), pingerPort.getPair())
            .trigger(pong(0), pongerPort.getPair())
        .end()

        .repeat(N).body()
            .expect(ping(0), pingerPort, OUT)
            .expect(pong(0), pingerPort, IN)
        .end()
    ;

    assert tc.check();
    assertEquals(N, pingsReceived(ponger));
    assertEquals(N, pongsReceived(pinger));
  }

  @Test
  public void nestedRepeat() {
    int M = 2, N = 3;
    tc.body()
        .repeat(M).body()
            .repeat(N).body()
                .trigger(ping(0), pingerPort.getPair())
            .end()
            .trigger(pong(0), pongerPort.getPair())
        .end()

        .repeat(M).body()
            .repeat(N).body()
                .expect(ping(0), pingerPort, OUT)
            .end()
            .expect(pong(0), pingerPort, IN)
        .end()
    ;
    assert tc.check();
    assertEquals(M, pongsReceived(pinger));
    assertEquals(M * N, pingsReceived(ponger));
  }

  @Test
  public void initTest() {
    final Counter counter = new Counter();
    BlockInit init = new BlockInit() {
      @Override
      public void init() {
        counter.count++;
      }
    };

    int N = 3;
    tc.body()
        .repeat(N, init).body()
            .repeat(N, init).body()
                .repeat(N, init).body().end()
            .end()
        .end()
    ;
    assert tc.check();
    assertEquals(N + (N*N) + (N*N*N), counter.count);
  }
}
