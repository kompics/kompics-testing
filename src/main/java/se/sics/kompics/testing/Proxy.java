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
import se.sics.kompics.Component;
import se.sics.kompics.ComponentCore;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Fault;
import se.sics.kompics.FaultHandler;
import se.sics.kompics.Init;
import se.sics.kompics.Kompics;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.PortCore;
import se.sics.kompics.Scheduler;
import se.sics.kompics.Tracer;
import se.sics.kompics.scheduler.ThreadPoolScheduler;
import se.sics.kompics.testing.scheduler.CallingThreadScheduler;

/**
 *  The parent component of the CUT and its external dependencies.
 */
class Proxy<T extends ComponentDefinition> extends ComponentDefinition {
    // LOG.
    private Logger logger = TestContext.logger;

    // The component under test.
    private Component cut;

    // The Components' scheduler for handling events.
    private Scheduler scheduler;

    // The CUT definition.
    private T definitionUnderTest;

    private final Simulator simulator;

    public Proxy(Simulator simulator) {
        this.simulator = simulator;

        // Use CallingThreadScheduler for proxy and the default
        // scheduler for all other components.
        getComponentCore().setScheduler(new CallingThreadScheduler());
        scheduler = new ThreadPoolScheduler(1);
        Kompics.setScheduler(scheduler);
    }

    // Create the CUT with the provided component definition and init.
    public T createComponentUnderTest(Class<T> definition, Init<T> initEvent) {
        init(definition, initEvent);
        return definitionUnderTest;
    }

    // Create the CUT with the provided component definition and init.
    public T createComponentUnderTest(Class<T> definition, Init.None initEvent) {
        init(definition, initEvent);
        return definitionUnderTest;
    }

    // Return the CUT.
    public Component getComponentUnderTest() {
        return cut;
    }

    // Create an external dependency component.
    public <T extends ComponentDefinition>
    Component createDependency(Class<T> cClass, Init<T> initEvent) {
        Component c = create(cClass, initEvent);
        // Set the scheduler initially to null.
        // It will be set to the default by Kompics when the
        // component receives its first event.
        c.getComponent().getComponentCore().setScheduler(null);
        return c;
    }

    // Create an external dependency component.
    public <T extends ComponentDefinition>
    Component createDependency(Class<T> cClass, Init.None initEvent) {
        Component c = create(cClass, initEvent);
        // Set the scheduler initially to null.
        // It will be set to the default by Kompics when the
        // component receives its first event.
        c.getComponent().getComponentCore().setScheduler(null);
        return c;
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    // Set action to be taken when an exception is thrown
    // within the test case - e.g while executing CUT or
    // external dependency handlers.
    @Override
    public Fault.ResolveAction handleFault(Fault fault) {
        logger.debug("Fault was thrown {}", fault);

        // Process the fault (it might be expected).
        return processFault(fault);
    }

    // Adds the specified fault to the event symbol queue.
    private Fault.ResolveAction processFault(Fault fault) {
        EventSymbol eventSymbol = new EventSymbol(fault, definitionUnderTest.getControlPort(), Direction.OUT);
        eventSymbol.setForwardEvent(false);
        simulator.doTransition(eventSymbol);

        // We wouldn't know how to resolve the fault.
        return Fault.ResolveAction.IGNORE;
    }

    // initialize proxy.
    @SuppressWarnings("unchecked")
    private void init(Class<T> definition,
                      Init<? extends ComponentDefinition> initEvent) {
        // Create CUT with Init and tracer.
        Tracer t = new Tracer() {
            @Override
            public boolean triggeredOutgoing(KompicsEvent event, PortCore<?> port) {
                return simulator.doTransition(event, port, Direction.OUT);
            }

            @Override
            public boolean triggeredIncoming(KompicsEvent event, PortCore<?> port) {
                return simulator.doTransition(event, port, Direction.IN);
            }
        };

        // Have we already run this init before?
        if (definitionUnderTest != null) {
            // If yes, do nothing.
            return;
        }

        ComponentCore.childTracer.set(t);

        // Create the component under test as a child component.
        if (initEvent == Init.NONE) {
            cut = create(definition, (Init.None) initEvent);
        } else {
            cut = create(definition, (Init<T>) initEvent);
        }

        // set the CUT definition.
        definitionUnderTest = (T) cut.getComponent();

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
                return processFault(f);
            }
        };

        // Set handler.
        Kompics.setFaultHandler(fh);
    }
}
