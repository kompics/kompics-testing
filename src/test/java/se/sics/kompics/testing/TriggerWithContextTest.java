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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */
package se.sics.kompics.testing;

import static org.junit.Assert.assertTrue;
import org.junit.Test;
import se.sics.kompics.Component;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Port;
import se.sics.kompics.testing.triggercontext.AComp;
import se.sics.kompics.testing.triggercontext.APort;
import se.sics.kompics.testing.triggercontext.EventA;
import se.sics.kompics.testing.triggercontext.EventB;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class TriggerWithContextTest {

  @Test
  public void test1() {
    TestContext<AComp> tc = TestContext.newInstance(AComp.class);
    Component comp = tc.getComponentUnderTest();
    Port<APort> port = comp.getNegative(APort.class);

    tc = tc.body();
    tc = repeatedEvents(tc, port);
    tc = repeatedEvents(tc, port);
    tc = repeatedEvents(tc, port);
    tc = repeatedEvents(tc, port);
    tc = tc.repeat(1).body().end();

    assertTrue(tc.check());
  }
  
  @Test
  public void test2() {
    TestContext<AComp> tc = TestContext.newInstance(AComp.class);
    Component comp = tc.getComponentUnderTest();
    Port<APort> port = comp.getNegative(APort.class);

    tc = tc.body();
    tc = tc.repeat(5).body();
    tc = repeatedEvents(tc, port);
    tc = tc.end();
    tc = tc.repeat(1).body().end();

    assertTrue(tc.check());
  }

  private TestContext repeatedEvents(TestContext tc, Port port) {
    Future f = repeatedFuture();
    return tc
      .trigger(new EventA(10), port)
      .answerRequest(EventB.class, port, f)
      .repeat(AComp.COUNTER).body()
      .trigger(f, port)
      .end();
  }

  private Future repeatedFuture() {
    return new Future() {
      int i = 0;

      @Override
      public boolean set(KompicsEvent request) {
        i = 0; //reset
        return true;
      }

      @Override
      public KompicsEvent get() {
        return new EventA(i++);
      }
    };
  }
}
