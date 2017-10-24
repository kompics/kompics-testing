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
import se.sics.kompics.Fault;
import se.sics.kompics.Port;
import se.sics.kompics.PortType;

/**
 * A Transition Label in the NFA, matching a fault event either by
 * class or via a {@link com.google.common.base.Predicate}.
 */
class FaultLabel implements Label {

    // Set to true if this label matches by class.
    private boolean matchExceptionByClass;

    // Set the class of the expected exception.
    private Class<? extends Throwable> exceptionType;

    // Set the predicate matching the expected exception.
    private Predicate<Throwable> exceptionPredicate;

    // The internal control port of the CUT.
    private final Port<? extends PortType> controlPort;

    FaultLabel(Port<? extends PortType> controlPort,
               Class<? extends Throwable> exceptionType) {
        this.controlPort = controlPort;
        this.exceptionType = exceptionType;
        matchExceptionByClass = true;
    }

    FaultLabel(Port<? extends PortType> controlPort,
               Predicate<Throwable> exceptionPredicate) {
        this.controlPort = controlPort;
        this.exceptionPredicate = exceptionPredicate;
    }

    // Return true if this event is a fault event and it
    // is matched by this fault label.
    @Override
    public boolean match(EventSymbol eventSymbol) {
        // Is this a fault event and did it occur on the control port
        // of the CUT?
        if (!(eventSymbol.getEvent() instanceof Fault)
            || controlPort != eventSymbol.getPort())
            return false;

        // If yes, get its exception.
        Fault fault = (Fault) eventSymbol.getEvent();
        Throwable exception = fault.getCause();

        // Can we match this exception by class?
        if (matchExceptionByClass) {
            // If yes, check that the exception is a subtype of what we are expecting?
            return exceptionType.isAssignableFrom(exception.getClass());
        } else {
            // Otherwise, we must match using predicate.
            assert exceptionPredicate != null;
            return exceptionPredicate.apply(exception);
        }
    }

    @Override
    public String toString() {
        return String.format("expectFault(%s)",
            exceptionType != null? exceptionType : exceptionPredicate);
    }
}
