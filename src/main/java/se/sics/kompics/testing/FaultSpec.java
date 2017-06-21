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
import se.sics.kompics.Fault;
import se.sics.kompics.Port;
import se.sics.kompics.PortType;

class FaultSpec implements Spec {

  private final Class<? extends Throwable> exceptionType;
  private final Predicate<Throwable> exceptionPredicate;
  private final Port<? extends PortType> port;
  Direction direction = Direction.OUT;

  FaultSpec(
      Port<? extends PortType> port, Class<? extends Throwable> exceptionType) {
    this.exceptionType = exceptionType;
    this.exceptionPredicate = null;
    this.port = port;
  }

  FaultSpec(
      Port<? extends PortType> port, Predicate<Throwable> exceptionPredicate) {
    this.exceptionPredicate = exceptionPredicate;
    this.exceptionType = null;
    this.port = port;
  }

  @Override
  public boolean match(EventSpec receivedSpec) {
    if (!(receivedSpec.getEvent() instanceof Fault)) {
      return false;
    }
    Fault fault = (Fault) receivedSpec.getEvent();
    Throwable exception = fault.getCause();
    if (exceptionType != null) {
      return exceptionType.isAssignableFrom(fault.getCause().getClass());
    } else {
      assert exceptionPredicate != null;
      return exceptionPredicate.apply(exception);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof FaultSpec) {
      FaultSpec other = (FaultSpec) o;
      return exceptionType != null && other.exceptionType != null
             && exceptionType == other.exceptionType;
    }
    if (o instanceof EventSpec) {
      EventSpec eventSpec = (EventSpec) o;
      return this.match(eventSpec);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int result = 31 * port.hashCode();
    result = 31 * result + direction.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return String.format("expectFault(%s)",
        exceptionType != null? exceptionType : exceptionPredicate);
  }
}
