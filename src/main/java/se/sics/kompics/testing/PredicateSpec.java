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
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Port;
import se.sics.kompics.PortType;

class PredicateSpec implements SingleEventSpec{

  private final Class<? extends KompicsEvent> eventType;
  final Predicate<? extends KompicsEvent> predicate;
  final Port<? extends PortType> port;
  final Direction direction;

  <E extends KompicsEvent> PredicateSpec(
          Class<E> eventType, Predicate<E> predicate,
          Port<? extends PortType> port, Direction direction) {
    this.port = port;
    this.direction = direction;
    this.eventType = eventType;
    this.predicate = predicate;
  }

  @Override
  public boolean match(EventSpec receivedSpec) {
    KompicsEvent receivedEvent = receivedSpec.getEvent();
    return eventType.equals(receivedEvent.getClass()) &&
            port.equals(receivedSpec.getPort()) &&
            direction.equals(receivedSpec.getDirection()) &&
           matchHelper(predicate, receivedEvent);
  }

  private <E extends KompicsEvent> boolean matchHelper(Predicate<E> predicate, KompicsEvent receivedEvent) {
    E r = (E) receivedEvent;
    return predicate.apply(r);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof EventSpec)) { // only comparable with EventSpec (lookups for received events)
      return false;
    }
    EventSpec eventSpec = (EventSpec) o;
    return match(eventSpec);
  }

  @Override
  public int hashCode() {
    int result = 31 * port.hashCode();
    result = 31 * result + direction.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "( " + direction + " " + predicate.toString() + " )";
  }
}
