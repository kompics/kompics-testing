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
import org.slf4j.Logger;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.JavaComponent;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Port;
import se.sics.kompics.PortType;

class InternalEventSpec implements Spec {

  private Logger logger = TestContext.logger;

  private boolean inspect;
  private Predicate<? extends ComponentDefinition> inspectPredicate;
  private ComponentDefinition definitionUnderTest;

  private boolean trigger;
  private KompicsEvent event;
  private Port<? extends PortType> port;
  private Future<? extends KompicsEvent, ? extends KompicsEvent> future; // or use future

  <T extends ComponentDefinition> InternalEventSpec(T definitionUnderTest, Predicate<T> inspectPredicate) {
    this.definitionUnderTest = definitionUnderTest;
    this.inspectPredicate = inspectPredicate;
    inspect = true;
  }

  InternalEventSpec(KompicsEvent event, Port<? extends PortType> port) {
    this.event = event;
    this.port = port;
    trigger = true;
  }

  InternalEventSpec(
      Future<? extends KompicsEvent, ? extends KompicsEvent> future,
      Port<? extends PortType> port) {
    this.port = port;
    this.future = future;
    trigger = true;
  }

  @Override
  public boolean match(EventSpec receivedSpec) {
    return false;
  }

  String performInternalEvent() {
    if (inspect) {
      return doInspect();
    }
    if (trigger) {
      return doTrigger();
    }
    return null;
  }

  private String doTrigger() {
    if (event == null) {
      event = future.get();
      if (event == null) {
        return String.format("Invalid return value from Future %s", future);
      }
    }
    logger.trace("triggered({})\t", event);
    port.doTrigger(event, 0, port.getOwner());
    return null;
  }

  private String doInspect() {
    logger.trace("Inspecting Component");
    JavaComponent cut = (JavaComponent) definitionUnderTest.getComponentCore();

    while (cut.workCount.get() > 0) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    boolean successful = inspect(definitionUnderTest, inspectPredicate);

    return successful? null : "Component assertion failed";
  }

  private <T extends ComponentDefinition> boolean inspect(
      ComponentDefinition definitionUnderTest, Predicate<T> inspectPredicate) {
    return inspectPredicate.apply((T) definitionUnderTest);
  }

  @Override
  public String toString() {
    if (inspectPredicate != null) {
      return inspectPredicate.toString();
    }
    if (event != null) {
      return "trigger(" + event + ")";
    }
    return null;
  }
}
