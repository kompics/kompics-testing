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

import com.google.common.base.Predicate;
import org.slf4j.Logger;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.JavaComponent;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Port;
import se.sics.kompics.PortType;

/**
 * A state transition label that carries out some predetermined
 * action as opposed to matching an event symbol.
 * @see #InternalLabel#InternalAction for performable actions.
 *
 * An active-state has at least one internal transition (A transition
 * with an InternalLabel).
 */
class InternalLabel implements Label {

    // Possible internal actions.
    private enum InternalAction {
        // Trigger an event.
        TRIGGER,
        // Inspect CUT.
        INSPECT
    }

    // The action to be performed by this label.
    private final InternalAction ACTION;

    // LOG.
    private Logger logger = TestContext.logger;

    // The CUT's definition.
    private ComponentDefinition definitionUnderTest;

    // If ACTION is INSPECT, this predicate is invoked.
    private Predicate<? extends ComponentDefinition> inspectPredicate;

    // If ACTION is TRIGGER and this event will be triggered if non-null.
    private KompicsEvent event;

    // If ACTION is TRIGGER and event is null, an event is retrieved by
    // invoking this Future.
    private Future<? extends KompicsEvent, ? extends KompicsEvent> future;

    // The port on which we trigger an event when performing a TRIGGER action.
    private Port<? extends PortType> port;


    // Create an Inspection Label.
    <T extends ComponentDefinition>
    InternalLabel(T definitionUnderTest,
                  Predicate<T> inspectPredicate) {
        this.definitionUnderTest = definitionUnderTest;
        this.inspectPredicate = inspectPredicate;
        ACTION = InternalAction.INSPECT;
    }

    // Create a Trigger Label - Trigger the provided event.
    InternalLabel(KompicsEvent event, Port<? extends PortType> port) {
        this.event = event;
        this.port = port;
        ACTION = InternalAction.TRIGGER;
    }

    // Create a Trigger Label - Use future to get triggered event.
    InternalLabel(Future<? extends KompicsEvent, ? extends KompicsEvent> future,
                  Port<? extends PortType> port) {
        this.port = port;
        this.future = future;
        ACTION = InternalAction.TRIGGER;
    }

    @Override
    public boolean match(EventSymbol eventSymbol) {
        return false;
    }

    // Perform the predetermined action for this label and
    // return an null if no error occurred. Otherwise, return
    // an error message.
    String executeAction() {
        switch (ACTION) {
            case TRIGGER:
                return doTrigger();
            case INSPECT:
                return doInspect();
            default:
                throw new RuntimeException("No action specified");
        }
    }

    // Trigger an event on the specified port.
    private String doTrigger() {
        // Were we provided with an event to trigger?
        if (event == null || future != null) {
            // If no, get event from provided future.
            event = future.get();
            if (event == null) {
                // Return error message.
                return String.format("Future %s returned null", future);
            }
        }

        logger.trace("triggered({})\t", event);

        // Finally, trigger the event.
        port.doTrigger(event, 0, port.getOwner());
        return null;
    }

    // Perform a component inspection.
    private String doInspect() {
        logger.trace("Inspecting Component");
        JavaComponent cut = (JavaComponent)
                            definitionUnderTest.getComponentCore();

        // Wait until all pending events have been handled by the CUT.
        while (cut.workCount.get() > 0) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Call the provided Predicate with the CUT.
        boolean successful = inspectHelper(definitionUnderTest,
                                           inspectPredicate);

        return successful? null : "Component assertion failed";
    }

    // Call the provided Predicate with the CUT.
    @SuppressWarnings("unchecked")
    private <T extends ComponentDefinition>
    boolean inspectHelper(ComponentDefinition definitionUnderTest,
                          Predicate<T> inspectPredicate) {
        return inspectPredicate.apply((T) definitionUnderTest);
    }

    @Override
    public String toString() {
        switch (ACTION) {
            case TRIGGER:
                return String.format("Trigger(%s)",
                                     event != null ? event : future);
            case INSPECT:
                return inspectPredicate.toString();
            default:
                return super.toString();
        }
    }
}
