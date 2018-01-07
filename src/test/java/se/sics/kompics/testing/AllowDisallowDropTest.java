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
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Negative;
import se.sics.kompics.PortType;
import se.sics.kompics.Positive;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AllowDisallowDropTest extends TestHelper{

  private Component pinger, ponger;
  private TestContext<Pinger> tc;
  private Negative<PingPongPort> pingerPort;
  private Positive<PingPongPort> pongerPort;
  private Counter pongsReceived;

  @Before
  public void init() {
    pongsReceived = new Counter();
    PingerInit pingerInit = new PingerInit(pongsReceived);
    tc = TestContext.newInstance(Pinger.class, pingerInit);
    pinger = tc.getComponentUnderTest();
    ponger = tc.create(Ponger.class, new PongerInit(new Counter()));
    pingerPort = pinger.getNegative(PingPongPort.class);
    pongerPort = ponger.getPositive(PingPongPort.class);
    tc.connect(pingerPort, pongerPort);
  }

  @Test
  public void allowTest() {
    int N = 3;
    tc.body().repeat(N)
        .allow(pong(0), pingerPort, IN)
    .body()
            .trigger(pong(1), pongerPort.getPair())
            .trigger(pong(0), pongerPort.getPair())
            .trigger(pong(2), pongerPort.getPair())

            .expect(pong(1), pingerPort, IN)
            .expect(pong(2), pingerPort, IN)
    .end()
    ;
    assert tc.check();
    assertEquals(N*3, pongsReceived.count);
  }

  @Test
  public void disallowTest() {
    tc.disallow(pong(0), pingerPort, IN)
        .body()
            .trigger(pong(1), pongerPort.getPair())
            .trigger(pong(0), pongerPort.getPair())
            .trigger(pong(2), pongerPort.getPair())
            .expect(pong(1), pingerPort, IN)
            .expect(pong(2), pingerPort, IN)
    ;
    assert !tc.check();
    assertEquals(1, pongsReceived.count);
  }

  @Test
  public void dropTest() {
    int N = 3;
    tc.body().repeat(N)
        .drop(pong(0), pingerPort, IN)
    .body()
            .trigger(pong(0), pongerPort.getPair())
            .trigger(pong(1), pongerPort.getPair())
            .trigger(pong(0), pongerPort.getPair())
            .trigger(pong(2), pongerPort.getPair())
            .expect(pong(1), pingerPort, IN)
            .expect(pong(2), pingerPort, IN)
    .end()
    ;
    assert tc.check();
    assertEquals(N*2, pongsReceived.count);
  }

  @Test
  public void shadowTest() {
    int N = 4;
    tc.drop(pong(0), pingerPort, IN)
        .drop(pong(1), pingerPort, IN)
        .body()
        .repeat(N).body()
            .trigger(pong(0), pongerPort.getPair())
            .trigger(pong(1), pongerPort.getPair())
        .end()
        .repeat(N - 1)
            .allow(pong(0), pingerPort, IN)
        .body()
            .expect(pong(0), pingerPort, IN)
        .end()
    ;
    assert tc.check();
    assertEquals(N - 1, pongsReceived.count);
  }

  @Test
  public void predicateAllow() {
    int N = 3;
    tc.body().repeat(N)
        .allow(Pong.class, pongPred(0), pingerPort, IN)
    .body()
        .trigger(pong(1), pongerPort.getPair())
        .trigger(pong(0), pongerPort.getPair())
        .trigger(pong(2), pongerPort.getPair())

        .expect(pong(1), pingerPort, IN)
        .expect(pong(2), pingerPort, IN)
    .end()
    ;
    assert tc.check();
    assertEquals(N*3, pongsReceived.count);
  }

  @Test
  public void predicateDisallow() {
    tc.disallow(Pong.class, pongPred(0), pingerPort, IN).body()
        .trigger(pong(1), pongerPort.getPair())
        .trigger(pong(0), pongerPort.getPair())
        .trigger(pong(2), pongerPort.getPair())
        .expect(pong(1), pingerPort, IN)
        .expect(pong(2), pingerPort, IN)
    ;
    assert !tc.check();
    assertEquals(1, pongsReceived.count);
  }

  @Test
  public void predicateDrop() {
    int N = 3;
    tc.body().repeat(N)
        .drop(Pong.class, pongPred(0), pingerPort, IN)
    .body()
        .trigger(pong(0), pongerPort.getPair())
        .trigger(pong(1), pongerPort.getPair())
        .trigger(pong(0), pongerPort.getPair())
        .trigger(pong(2), pongerPort.getPair())

        .expect(pong(1), pingerPort, IN)
        .expect(pong(2), pingerPort, IN)
    .end()
    ;
    assert tc.check();
    assertEquals(N*2, pongsReceived.count);
  }

  @Test
  public void matchByClass() {
    int N = 3;
    tc.body().repeat(N)
          .allow(Pong.class, pingerPort, IN)
          .drop(Ping.class, pingerPort, OUT)
      .body()
          .trigger(pong(1), pongerPort.getPair())
          .trigger(pong(0), pongerPort.getPair())
          .trigger(ping(0), pingerPort.getPair())
          .trigger(pong(2), pongerPort.getPair())

          .expect(pong(1), pingerPort, IN)
          .expect(pong(2), pingerPort, IN)
      .end()
    ;
    assert tc.check();
    assertEquals(N*3, pongsReceived.count);
    assertEquals(0, pingsReceived(ponger));
  }

  @Test
  public void matchByClassDisallow() {
    tc.disallow(Pong.class, pingerPort, IN).body()
        .trigger(pong(1), pongerPort.getPair())
    ;
    assert !tc.check();
    assertEquals(0, pongsReceived.count);
    assertEquals(0, pingsReceived(ponger));
  }

  private static Predicate<Pong> pongPred(final int id) {
    return new Predicate<Pong>() {
      @Override
      public boolean apply(Pong pong) {
        return pong.id == id;
      }
    };
  }

  @Test
  public void declarationAreProcessedInLIFO() {
    int N = 3;
    TestContext<TestComponent> tc = TestContext.newInstance(TestComponent.class);
    Component component = tc.getComponentUnderTest();
    Positive<TestPort> port = component.getPositive(TestPort.class);
    Component dep = tc.create(Dependency.class);
    Negative<TestPort> pair = dep.getNegative(TestPort.class);
    Positive<TestPort> portDep = pair.getPair();
    tc.connect(port, pair);
    tc
        .disallow(Event.class, matchSubC, port, IN)
        .allow(Event.class, matchSubB, port, IN)
        .drop(Event.class, matchSubA, port, IN)
        .body()
          .repeat(N)
          .body()
            .trigger(a, portDep)
            .trigger(b, portDep)
          .end()
          .trigger(c, portDep)
    ;
    assertTrue(!tc.check());
    assertEquals(0, eventCount(component, A.class));
    assertEquals(N, eventCount(component, B.class));
    assertEquals(0, eventCount(component, C.class));
  }

  @Test
  public void lifoHeadersNestedBlockTest() {
    int N = 3;
    int M = N-1;
    TestContext<TestComponent> tc = TestContext.newInstance(TestComponent.class);
    Component component = tc.getComponentUnderTest();
    Positive<TestPort> port = component.getPositive(TestPort.class);
    Component dep = tc.create(Dependency.class);
    Negative<TestPort> pair = dep.getNegative(TestPort.class);
    Positive<TestPort> portDep = pair.getPair();
    tc.connect(port, pair);
    tc
        .allow(Event.class, matchSubC, port, IN)
        .body()
        .repeat(N)
        .body()
          .trigger(a, portDep)
          .trigger(b, portDep)
          .trigger(c, portDep)
        .end()
        .repeat(M)
          .drop(Event.class, matchB, port, IN)
        .body()
          .expect(Event.class, matchC, port, IN)
        .end()
        .repeat(N-M)
          .drop(Event.class, matchA, port, IN)
        .body()
          .expect(Event.class, matchC, port, IN)
        .end()
    ;
    assertTrue(tc.check());
    assertEquals(M, eventCount(component, A.class));
    assertEquals(N - M, eventCount(component, B.class));
    assertEquals(N, eventCount(component, C.class));
  }

  private int eventCount(Component c, Class<? extends Event> event) {
    return ((TestComponent)c.getComponent()).getReceivedCount(event);
  }

  private Predicate<Event> eventForSubtype(final Class<? extends Event> type) {
    return new Predicate<Event>() {
      @Override
      public boolean apply(Event event) {
        return event.getClass().isAssignableFrom(type);
      }
    };
  }

  private Predicate<Event> eventFor(final Class<? extends Event> type) {
    return new Predicate<Event>() {
      @Override
      public boolean apply(Event event) {
        return event.getClass() == type;
      }
    };
  }
  private Predicate<Event> matchSubA = eventForSubtype(A.class);
  private Predicate<Event> matchSubB = eventForSubtype(B.class);
  private Predicate<Event> matchSubC = eventForSubtype(C.class);
  private Predicate<Event> matchA = eventFor(A.class);
  private Predicate<Event> matchB = eventFor(B.class);
  private Predicate<Event> matchC = eventFor(C.class);
  private static Event a = new A();
  private static Event b = new B();
  private static Event c = new C();
  public static class Event implements KompicsEvent {
  }
  public static class A extends Event {
  }
  public static class B extends A {
  }
  public static class C extends B {
  }

  public static class TestComponent extends ComponentDefinition {
    Map<Class<?>, Integer> received = new HashMap<>();
    Negative<TestPort> port = provides(TestPort.class);
    Handler<Event> eventHandler = new Handler<Event>() {
      @Override
      public void handle(Event event) {
        Class<?> clazz = event.getClass();
        Integer i = received.get(clazz);
        int count = i == null? 0 : i;
        received.put(clazz,  count + 1);
      }
    };
    int getReceivedCount(Class<?> clazz) {
      Integer i = received.get(clazz);
      return i == null? 0 : i;
    }
    {
      subscribe(eventHandler, port);
    }
  }
  public static class Dependency extends ComponentDefinition {
    {requires(TestPort.class);}
  }

  public static class TestPort extends PortType {{
    request(Event.class);
  }}
}
