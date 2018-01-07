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
import java.util.Comparator;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Port;
import se.sics.kompics.PortType;

/**
 * A state transition label in the NFA that matches a single
 * input event symbol using an equivalent event instance or
 * predicate.
 * @see Ctrl#createEventLabel(KompicsEvent, Port, Direction)
 * @see Ctrl#createPredicateLabel(Class, Port, Direction)
 * @see Ctrl#createPredicateLabel(Class, Predicate, Port, Direction)
 *
 * A default Event Label is created if a {@link se.sics.kompics.KompicsEvent}
 * instance e is provided by the expect statement from which this label
 * is created, then e is used to match symbol i either via a comparator if
 * provided or e's {@link #equals(Object)} method.
 *
 * A Predicate Label is created if a {@link com.google.common.base.Predicate} p
 * is instead provided by the expect statement, then p is invoked using i. If p
 * returns true, then the label matched i.
 */
class EventLabel implements SingleLabel {

    // The port on which we expect an event.
    private Port<? extends PortType> port;

    // The direction, with respect to the CUT, of the expected event.
    private Direction direction;

    // This is used to determine equivalence with an observed event.
    private KompicsEvent event;

    // This is used to determine equivalence between the expected and observed event.
    private Comparator<? extends KompicsEvent> comparator;

    // Set to true if this label matches an event symbol via a Predicate.
    private boolean isPredicateLabel;

    // Set a predicate to match an input event symbol.
    private Predicate<? extends KompicsEvent> predicate;

    // Set predicate's eventType.
    private Class<? extends KompicsEvent> eventType;

    private EventLabel() {}

    // Create this label with event matcher.
    <E extends KompicsEvent> EventLabel(E event,
                                        Port<? extends PortType> port,
                                        Direction direction,
                                        Comparator<E> comparator) {
        this.port = port;
        this.direction = direction;
        this.event = event;
        this.comparator = comparator;
    }

    // Create a label with predicate matcher.
    <E extends KompicsEvent> EventLabel(Class<E> eventType,
                                        Predicate<E> predicate,
                                        Port<? extends PortType> port,
                                        Direction direction) {
        this.port = port;
        this.direction = direction;
        this.eventType = eventType;
        this.predicate = predicate;
        isPredicateLabel = true;
    }

    // Return true if this label matches the supplied event symbol.
    @Override
    public boolean match(EventSymbol eventSymbol) {
        // Are we expecting an event on the same port and direction.
        if (!getPort().equals(eventSymbol.getPort())
            || !getDirection().equals(eventSymbol.getDirection()))
            return false;

        // Is this a predicate or event label?
        if (isPredicateLabel) return predicateMatch(eventSymbol.getEvent());
        else return eventMatch(eventSymbol.getEvent());
    }

    // Return true if this label's predicate matches the observed event.
    private boolean predicateMatch(KompicsEvent observed) {
        // Is this the expected class of event?
        if (!(eventType.isAssignableFrom(observed.getClass())))
            return false;

        // If yes, Invoke predicate with the observed event.
        return predicateMatchHelper(predicate, observed);
    }

    // Return true if this label matches the event.
    private boolean eventMatch(KompicsEvent observed) {
        // Was a comparator registered for event's type?
        if (comparator != null) {
            // If yes, use it to determine equivalence.
            return eventMatchHelper(comparator, event, observed);
        } else {
            // Otherwise default to equals method.
            return event.equals(observed);
        }
    }

    // Return true if e1 and e2 have the same class and e1 equals e2 according to comp.
    @SuppressWarnings("unchecked")
    static
    private <V extends KompicsEvent> boolean eventMatchHelper(Comparator<V> comp,
                                                              KompicsEvent e1,
                                                              KompicsEvent e2) {
        return e1.getClass() == e2.getClass()
               && comp.compare((V) e1, (V) e2) == 0;
    }

    // Return true if predicate matches event.
    @SuppressWarnings("unchecked")
    private <E extends KompicsEvent>
    boolean predicateMatchHelper(Predicate<E> predicate, KompicsEvent event) {
        return predicate.apply((E) event);
    }

    @Override
    public String toString() {
        return String.format("%s %s", direction,
               (isPredicateLabel? "Predicate(" + eventType.getSimpleName() + ")" : event));
    }

    KompicsEvent getEvent() {
        return event;
    }

    Port<? extends PortType> getPort() {
        return port;
    }

    Direction getDirection() {
        return direction;
    }

    // Label that matches the epsilon symbol.
    static EventLabel EPSILON_LABEL = new EventLabel() {
        @Override
        public boolean match(EventSymbol eventSymbol) {
            return eventSymbol == EventSymbol.EPSILON;
        }

        @Override
        public boolean equals(Object o) {
            return this == o;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public String toString() {
            return "EPSILON";
        }
    };
}
