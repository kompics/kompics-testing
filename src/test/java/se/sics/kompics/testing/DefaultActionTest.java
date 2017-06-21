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
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;

import static org.junit.Assert.assertEquals;
import static se.sics.kompics.testing.Action.*;

public class DefaultActionTest extends TestHelper{

  private TestContext<Pinger> tc;
  private Component pinger;
  private Component ponger;
  private Negative<PingPongPort> pingerPort;
  private Positive<PingPongPort> pongerPort;

  private static Function<SubPing, Action> pingDefaultHandle = defaultAction(HANDLE);
  private static Function<SubPing, Action> pingDefaultDrop = defaultAction(DROP);
  private static Function<SubPing, Action> pingDefaultFail = defaultAction(FAIL);
  private static Function<KompicsEvent, Action> defaultHandle = defaultAction(HANDLE);
  private static Function<KompicsEvent, Action> defaultDrop = defaultAction(DROP);
  private static Function<KompicsEvent, Action> defaultFail = defaultAction(FAIL);

  private static <E extends KompicsEvent> Function<E, Action> defaultAction(final Action action) {
    return new Function<E, Action>() {
      @Override
      public Action apply(E e) {
        return action;
      }
    };
  }

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
  public void basicHandle() {
    int N = 3;
    tc.setDefaultAction(SubPing.class, pingDefaultHandle).body()
        .repeat(N).body()
            .trigger(sping(0), pingerPort.getPair())
        .end()
    ;
    assert tc.check();
    assertEquals(N, pingsReceived(ponger));
  }

  @Test
  public void basicFail() {
    tc.setDefaultAction(SubPing.class, pingDefaultFail).body()
        .trigger(sping(0), pingerPort.getPair())
    ;
    assert !tc.check();
    assertEquals(0, pingsReceived(ponger));
  }

  @Test
  public void basicDrop() {
    int N = 3;
    tc.setDefaultAction(SubPing.class, pingDefaultDrop).body()
        .repeat(N).body()
            .trigger(sping(0), pingerPort.getPair())
        .end()
    ;
    assert tc.check();
    assertEquals(0, pingsReceived(ponger));
  }

  @Test
  public void defaultHandle() {
    int N = 3;
    tc.setDefaultAction(KompicsEvent.class, defaultHandle).body()
        .repeat(N).body()
            .trigger(sping(0), pingerPort.getPair())
        .end()
    ;
    assert tc.check();
    assertEquals(N, pingsReceived(ponger));
  }

  @Test
  public void defaultFail() {
    tc.setDefaultAction(KompicsEvent.class, defaultFail).body()
        .trigger(sping(0), pingerPort.getPair())
    ;
    assert !tc.check();
    assertEquals(0, pingsReceived(ponger));
  }

  @Test
  public void defaultDrop() {
    int N = 3;
    tc.setDefaultAction(KompicsEvent.class, defaultDrop).body()
        .repeat(N).body()
            .trigger(sping(0), pingerPort.getPair())
        .end()
    ;
    assert tc.check();
    assertEquals(0, pingsReceived(ponger));
  }

  @Test
  public void matchSubClassEvents() {
    class V extends SubPing {
      V(int id) {
        super(id);
      }
    }
    int N = 3;
    tc.setDefaultAction(SubPing.class, pingDefaultHandle).body() // use default handler for SubPing instead
        .repeat(N).body()
            .trigger(new V(0), pingerPort.getPair())
        .end()
    ;
    assert tc.check();
    assertEquals(N, pingsReceived(ponger));
  }
}
