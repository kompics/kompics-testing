/*
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
import se.sics.kompics.PortType;
import se.sics.kompics.Positive;
import se.sics.kompics.Unsafe;
import se.sics.kompics.testing.scheduler.CallingThreadScheduler;
import java.util.Map;

/**
 *  The parent component of the CUT and its external dependencies.
 */
class Proxy<T extends ComponentDefinition> extends ComponentDefinition{

    // LOG.
    private Logger logger = TestContext.logger;

    // The event symbol queue.
    private final EventQueue eventQueue = new EventQueue();

    // The component under test.
    private Component cut;

    // The CUT definition.
    private T definitionUnderTest;

    // Configure monitoring events on the CUT's ports.
    PortConfig portConfig;

    Proxy() {
        // Set the proxy scheduler.
        getComponentCore().setScheduler(new CallingThreadScheduler());
    }

    // Create the CUT with the provided component definition and init.
    T createComponentUnderTest(Class<T> definition, Init<T> initEvent) {
        init(definition, initEvent);
        return definitionUnderTest;
    }

    // Create the CUT with the provided component definition and init.
    T createComponentUnderTest(Class<T> definition, Init.None initEvent) {
        init(definition, initEvent);
        return definitionUnderTest;
    }

    // Return the CUT.
    Component getComponentUnderTest() {
        return cut;
    }

    // Return the event queue.
    EventQueue getEventQueue() {
        return eventQueue;
    }

    // Create an external dependency component.
    <T extends ComponentDefinition>
    Component createSetupComponent(Class<T> cClass, Init<T> initEvent) {
        Component c = create(cClass, initEvent);
        // Set the scheduler initially to null.
        // It will be set to the default by Kompics when the
        // component receives its first event.
        c.getComponent().getComponentCore().setScheduler(null);
        return c;
    }

    // Create an external dependency component.
    <T extends ComponentDefinition>
    Component createSetupComponent(Class<T> cClass, Init.None initEvent) {
        Component c = create(cClass, initEvent);
        // Set the scheduler initially to null.
        // It will be set to the default by Kompics when the
        // component receives its first event.
        c.getComponent().getComponentCore().setScheduler(null);
        return c;
    }

    // Create a mirror of the specified provided port on this proxy component.
    <P extends PortType> Negative<P> providePort(Class<P> portType) {
        return provides(portType);
    }

    // Create a mirror of the specified provided port on this proxy component.
    <P extends PortType> Positive<P> requirePort(Class<P> portType) {
        return requires(portType);
    }

    // Get the port types for the provided ports of the CUT.
    Map<Class<? extends PortType>, JavaPort<? extends PortType>> getCutPositivePorts() {
        return Unsafe.getPositivePorts(cut);
    }

    // Get the port types for the required ports of the CUT.
    Map<Class<? extends PortType>, JavaPort<? extends PortType>> getCutNegativePorts() {
        return Unsafe.getNegativePorts(cut);
    }

    // Connect the specified ports using the specified factory.
    <P extends PortType> void doConnect(Positive<P> positive,
                                        Negative<P> negative,
                                        ChannelFactory factory) {
        portConfig.connect(positive, negative, factory);
    }

    // Set action to be taken when an exception is thrown
    // within the test case - e.g while executing CUT or
    // external dependency handlers.
    @Override
    public Fault.ResolveAction handleFault(Fault fault) {
        logger.debug("Fault was thrown {}", fault);

        // Add the fault to the event queue (it might be expected).
        return addFaultToEventQueue(fault);
    }

    // Adds the specified fault to the event symbol queue.
    private Fault.ResolveAction addFaultToEventQueue(Fault fault) {
        // Create event symbol for fault event.
        EventSymbol eventSymbol = new EventSymbol(fault,
                                                  definitionUnderTest.getControlPort(),
                                                  Direction.OUT,
                                                  ProxyHandler.faultHandler);

        // Add as the first element in the queue.
        // If the fault is expected, then the next
        // expect statement must be a match.
        // If the fault is not expected
        // then the exception is detected as soon as possible.
        // and the test case should fail.
        eventQueue.addFirst(eventSymbol);

        // We wouldn't know how to resolve the fault.
        return Fault.ResolveAction.IGNORE;
    }

    // initialize proxy.
    @SuppressWarnings("unchecked")
    private void init(Class<T> definition, Init<? extends ComponentDefinition> initEvent) {
        // Have we already run this init before?
        if (definitionUnderTest != null)
            // If yes, do nothing.
            return;

        // Create the component under test as a child component.
        if (initEvent == Init.NONE)
            cut = create(definition, (Init.None) initEvent);
        else
            cut = create(definition, (Init<T>) initEvent);

        // set the CUT definition.
        definitionUnderTest = (T) cut.getComponent();

        // Initialize the port configuration.
        portConfig = new PortConfig(this);

        // Set the default Kompics fault handler.
        setKompicsFaultHandler();
    }

    // Set the default Kompics fault handler for handling exceptions
    // within the framework or Kompics.
    private void setKompicsFaultHandler() {
        FaultHandler fh = new FaultHandler() {
            @Override
            public Fault.ResolveAction handle(Fault f) {
                // Add the fault to the event queue.
                return addFaultToEventQueue(f);
            }
        };

        // Set handler.
        Kompics.setFaultHandler(fh);
    }
}
