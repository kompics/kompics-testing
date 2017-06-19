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

import com.google.common.base.Function;
import se.sics.kompics.ComponentCore;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Port;
import se.sics.kompics.PortType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static se.sics.kompics.testing.REQUEST_ORDERING.*;
import static se.sics.kompics.testing.RESPONSE_POLICY.*;

class ExpectMapper implements MultiEventSpec{

  private final REQUEST_ORDERING request_ordering;
  private final RESPONSE_POLICY response_policy;

  private final List<MapperStruct> expected = new ArrayList<MapperStruct>();

  private final LinkedList<MapperStruct> pending = new LinkedList<MapperStruct>();
  private final LinkedList<MapperStruct> received = new LinkedList<MapperStruct>();

  private ComponentCore proxyComponent;

  private int numExpectedEvents;
  private Class<? extends KompicsEvent> eventType;
  private Function<? extends KompicsEvent, ? extends KompicsEvent> currentMapper;

  ExpectMapper(ComponentCore proxyComponent) {
    this.proxyComponent = proxyComponent;
    request_ordering = ORDERED;
    response_policy = IMMEDIATE;
  }

  ExpectMapper(ComponentCore proxyComponent,
               REQUEST_ORDERING request_ordering, RESPONSE_POLICY response_policy) {
    this.proxyComponent = proxyComponent;
    this.request_ordering = request_ordering;
    this.response_policy = response_policy;
  }

  <E extends KompicsEvent, R extends KompicsEvent> void setMapperForNext(
          int numExpectedEvents, Class<E> eventType, Function<E, R> mapper) {
    if (this.numExpectedEvents != 0) {
      throw new IllegalStateException(this.numExpectedEvents + " events have not yet been specified");
    }
    if (numExpectedEvents <= 0) {
      throw new IllegalArgumentException("number of expected events (" + numExpectedEvents + ") must be positive");
    }

    this.numExpectedEvents = numExpectedEvents;
    this.eventType = eventType;
    currentMapper = mapper;
  }

  void addExpectedEvent(
          Port<? extends PortType> listenPort, Port<? extends PortType> responsePort) {
    if (numExpectedEvents <= 0) {
      throw new IllegalStateException("no mapper was specified");
    }

    numExpectedEvents--;
    addNewMapperStruct(eventType, listenPort, responsePort, currentMapper);
  }

  <E extends KompicsEvent, R extends KompicsEvent> void addExpectedEvent(
          Class<E> eventType, Port<? extends PortType> listenPort,
          Port<? extends PortType> responsePort, Function<E, R> mapper) {
    addNewMapperStruct(eventType, listenPort, responsePort, mapper);
  }

  @Override
  public boolean match(EventSpec receivedSpec) {
    MapperStruct mapper = getMapper(receivedSpec);
    if (mapper == null) {
      return false;
    }

    if (response_policy == IMMEDIATE) {
      mapper.doTrigger();
    }

    if (pending.isEmpty()) { // RECEIVE_ALL
      if (response_policy == RECEIVE_ALL) {
        for (MapperStruct m : received) {
          m.doTrigger();
        }
      }
    }

    return true;
  }

  private MapperStruct getMapper(EventSpec receivedSpec) {
    if (pending.isEmpty()) {
      return null;
    }
    MapperStruct mapper;

    if (request_ordering == ORDERED) {
      mapper = pending.getFirst();
      if (mapper.map(receivedSpec)) {
        pending.removeFirst();
        received.add(mapper);
        return mapper;
      }
    }

    if (request_ordering == UNORDERED) {
      Iterator<MapperStruct> it = pending.iterator();
      while (it.hasNext()) {
        mapper = it.next();
        if (mapper.map(receivedSpec)) {
          it.remove();
          received.add(mapper);
          return mapper;
        }
      }
    }
    return null;
  }

  private void reset() {
    assert pending.isEmpty();
    assert received.size() == expected.size();
    received.clear();
    pending.addAll(expected);
  }

  @Override
  public boolean isComplete() {
    if (pending.isEmpty()) {
      reset();
      return true;
    }
    return false;
  }

  boolean isEmpty() {
    return expected.isEmpty();
  }

  private void addNewMapperStruct(
          Class<? extends KompicsEvent> eventType, Port<? extends PortType> listenPort,
          Port<? extends PortType> responsePort,
          Function<? extends KompicsEvent, ? extends KompicsEvent> mapper) {
    MapperStruct mapperStruct = new MapperStruct(eventType, mapper, listenPort, responsePort);
    expected.add(mapperStruct);
    pending.add(mapperStruct);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("RequestResponse<");
    sb.append(request_ordering).append(", ").append(response_policy).append(">");
    sb.append("Pending").append(pending.toString());
    sb.append("  Received").append(received.toString());
    return sb.toString();
  }

  private class MapperStruct {
    private final Class<? extends KompicsEvent> eventType;
    private final Function<? extends KompicsEvent, ? extends KompicsEvent> mapper;
    private final Port<? extends PortType> listenPort;
    private final Port<? extends PortType> responsePort;

    private EventSpec receivedSpec;
    private KompicsEvent response;

    private MapperStruct(Class<? extends KompicsEvent> eventType,
                 Function<? extends KompicsEvent, ? extends KompicsEvent> mapper,
                 Port<? extends PortType> listenPort,
                 Port<? extends PortType> responsePort) {
      this.eventType = eventType;
      this.mapper = mapper;
      this.listenPort = listenPort;
      this.responsePort = responsePort;
    }

    private boolean map(EventSpec receivedSpec) {
      if (eventType.isAssignableFrom(receivedSpec.getEvent().getClass())) {
        response = getResponse(receivedSpec.getEvent(), mapper);
        return response != null;
      }
      return false;
    }

    private <E extends KompicsEvent, R extends KompicsEvent> R getResponse(
        KompicsEvent event, Function<E, R> mapper) {
      return mapper.apply((E) event);
    }

    private void doTrigger() {
      responsePort.doTrigger(response, 0, proxyComponent);
    }

    @Override
    public String toString() {
      return eventType.getSimpleName();
    }
  }

}
