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

import com.google.common.base.Predicate;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import se.sics.kompics.Component;
import se.sics.kompics.Port;
import se.sics.kompics.testing.eventsubtype.AComp;
import se.sics.kompics.testing.eventsubtype.APort;
import se.sics.kompics.testing.eventsubtype.EventA;
import se.sics.kompics.testing.eventsubtype.EventB;
import se.sics.kompics.testing.eventsubtype.EventInterface;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class EventSubtypeTest {

  @Test
  public void test1() {
    TestContext<AComp> tc = TestContext.newInstance(AComp.class);
    Component comp = tc.getComponentUnderTest();
    Port<APort> port = comp.getNegative(APort.class);

    tc = tc.body()
      .trigger(new EventA(), port)
      .expect(EventInterface.class, eventB1(), port, Direction.OUT)
      .repeat(1).body().end();

    assertTrue(tc.check());
  }
  
  //@Test
  public void test2() {
    TestContext<AComp> tc = TestContext.newInstance(AComp.class);
    Component comp = tc.getComponentUnderTest();
    Port<APort> port = comp.getNegative(APort.class);

    tc = tc.body()
      .trigger(new EventA(), port)
      .expect(EventB.class, eventB2(), port, Direction.OUT)
      .repeat(1).body().end();

    assertTrue(tc.check());
  }

  Predicate<EventInterface> eventB1() {
    return new Predicate<EventInterface>() {

      @Override
      public boolean apply(EventInterface t) {
        return t instanceof EventB;
      }
    };
  }
  
  Predicate<EventB> eventB2() {
    return new Predicate<EventB>() {

      @Override
      public boolean apply(EventB t) {
        return t instanceof EventB;
      }
    };
  }
}
