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

import org.slf4j.Logger;
import se.sics.kompics.ChannelFactory;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Fault;
import se.sics.kompics.FaultHandler;
import se.sics.kompics.Init;
import se.sics.kompics.JavaPort;
import se.sics.kompics.Kompics;
import se.sics.kompics.Negative;
import se.sics.kompics.Port;
import se.sics.kompics.PortType;
import se.sics.kompics.Positive;
import se.sics.kompics.Unsafe;
import se.sics.kompics.testing.scheduler.CallingThreadScheduler;
import java.util.Map;

class Proxy<T extends ComponentDefinition> extends ComponentDefinition{

  final EventQueue eventQueue = new EventQueue();

  private T definitionUnderTest;
  PortConfig portConfig;
  private Component cut;
  private CTRL<T> fsm;

  private Logger logger = TestContext.logger;

  Proxy() {
    getComponentCore().setScheduler(new CallingThreadScheduler());
  }

  T createComponentUnderTest(Class<T> definition, Init<T> initEvent) {
    init(definition, initEvent);
    return definitionUnderTest;
  }

  T createComponentUnderTest(Class<T> definition, Init.None initEvent) {
    init(definition, initEvent);
    return definitionUnderTest;
  }

  CTRL<T> getFsm() {
    return fsm;
  }

  Component getComponentUnderTest() {
    return cut;
  }

  EventQueue getEventQueue() {
    return eventQueue;
  }

  <T extends ComponentDefinition> Component createSetupComponent(Class<T> cClass, Init<T> initEvent) {
    Component c = create(cClass, initEvent);
    c.getComponent().getComponentCore().setScheduler(null);
    return c;
  }

  <T extends ComponentDefinition> Component createSetupComponent(Class<T> cClass, Init.None initEvent) {
    Component c = create(cClass, initEvent);
    c.getComponent().getComponentCore().setScheduler(null);
    return c;
  }

  <P extends PortType> Negative<P> providePort(Class<P> portType) {
    return provides(portType);
  }

  <P extends PortType> Positive<P> requirePort(Class<P> portType) {
    return requires(portType);
  }

  Map<Class<? extends PortType>, JavaPort<? extends PortType>> getCutPositivePorts() {
    return Unsafe.getPositivePorts(cut);
  }

  Map<Class<? extends PortType>, JavaPort<? extends PortType>> getCutNegativePorts() {
    return Unsafe.getNegativePorts(cut);
  }

  <P extends PortType> void doConnect(
          Positive<P> positive, Negative<P> negative, ChannelFactory factory) {
    portConfig.doConnect(positive, negative, factory);
  }

  @Override
  public Fault.ResolveAction handleFault(Fault fault) {
    logger.debug("Fault was thrown {}", fault);
    return addFaultToEventQueue(fault);
  }

  private Fault.ResolveAction addFaultToEventQueue(Fault fault) {
    EventSpec eventSpec = fsm.newEventSpec(fault, definitionUnderTest.getControlPort(), Direction.OUT);
    eventSpec.setHandler(ProxyHandler.faultHandler);
    eventQueue.addFirst(eventSpec);
    return Fault.ResolveAction.IGNORE;
  }

  @SuppressWarnings("unchecked")
  private void init(Class<T> definition, Init<? extends ComponentDefinition> initEvent) {
    if (definitionUnderTest != null) {
      return;
    }
    if (initEvent == Init.NONE) {
      cut = create(definition, (Init.None) initEvent);
    } else {
      cut = create(definition, (Init<T>) initEvent);
    }

    portConfig = new PortConfig(this);
    definitionUnderTest = (T) cut.getComponent();
    fsm = new CTRL<T>(this, definitionUnderTest);
    setFaultHandler();
  }

  private void setFaultHandler() {
    FaultHandler fh = new FaultHandler() {
      @Override
      public Fault.ResolveAction handle(Fault f) {
        return addFaultToEventQueue(f);
      }
    };
    Kompics.setFaultHandler(fh);
  }
}
