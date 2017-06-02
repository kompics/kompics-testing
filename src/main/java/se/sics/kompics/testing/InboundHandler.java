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

import se.sics.kompics.Direct;
import se.sics.kompics.JavaPort;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Port;
import se.sics.kompics.PortType;
import se.sics.kompics.Request;
import se.sics.kompics.Unsafe;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

class InboundHandler extends ProxyHandler {

  private Port<? extends PortType> destPort;
  private PortType portType;

  InboundHandler(
          Proxy proxy, PortStructure portStruct, Class<? extends KompicsEvent> eventType) {
    super(proxy, portStruct, eventType);
    this.destPort = portStruct.getOutboundPort();
    this.portType = destPort.getPortType();
  }

  @Override
  public void handle(KompicsEvent event) {
    TestContext.logger.trace("received incoming event: {}", event);
    if (event instanceof Request) {
      Request request = (Request) event;
      request.pushPathElement(proxy.getComponentCore());
    }

    if (event instanceof Direct.Request) {
      Direct.Request request = (Direct.Request) event;
      setupDirectRequest(request, Unsafe.getOrigin(request));
    }

    EventSpec eventSpec = proxy.getFsm().newEventSpec(event, destPort, Direction.IN);
    eventSpec.setHandler(this);
    eventQueue.offer(eventSpec);
  }

  @Override
  public void doHandle(KompicsEvent event) {
    destPort.doTrigger(event, 0, proxy.getComponentCore());
  }

  // handle incoming Direct.Request
  private Map<Port, JavaPort> originToGhostPort = new HashMap<Port, JavaPort>();

  private <P extends PortType> void setupDirectRequest(Direct.Request request, Port<P> origin) {
    // // TODO: 6/1/17 getOrigin is not public in Kompics
    JavaPort<P> ghostOutsidePort = originToGhostPort.get(origin);
    if (ghostOutsidePort == null) {
      assert portType == origin.getPortType();
      // // TODO: 6/1/17 javaport constructor is not public in Kompics
      boolean isPositive = portStruct.isProvidedPort;
      ghostOutsidePort = Unsafe.createJavaPort(!isPositive, origin.getPortType(), proxy.getComponentCore());
      JavaPort<P> ghostInsidePort = Unsafe.createJavaPort(isPositive, origin.getPortType(), proxy.getComponentCore());

      ghostOutsidePort.setPair(ghostInsidePort);
      ghostInsidePort.setPair(ghostOutsidePort);

      // subscribe ghosthandler
      Collection<Class<? extends KompicsEvent>> insidePortEvents = isPositive
              ? Unsafe.getPositiveEvents(portType)
              : Unsafe.getNegativeEvents(portType);
      for (Class<? extends KompicsEvent> eventType : insidePortEvents) {
        if (Direct.Response.class.isAssignableFrom(eventType)) {
          ghostInsidePort.doSubscribe(new GhostHandler((Class<? extends Direct.Response>) eventType, origin));
        }
      }
      originToGhostPort.put(origin, ghostOutsidePort);
    }
    Unsafe.setOrigin(request, ghostOutsidePort);
  }

  private class GhostHandler extends ProxyHandler {
    Port origin;

    GhostHandler(Class<? extends Direct.Response> eventType, Port origin) {
      super(InboundHandler.this.proxy, InboundHandler.this.portStruct, eventType);
      this.origin = origin;
    }

    @Override
    public void handle(KompicsEvent response) {
      EventSpec eventSpec = proxy.getFsm().newEventSpec(response, destPort, Direction.OUT);
      eventSpec.setHandler(this);
      eventQueue.offer(eventSpec);
    }

    @Override
    void doHandle(KompicsEvent response) {
      assert response instanceof Direct.Response;
      origin.doTrigger(response, 0, proxy.getComponentCore());
    }
  }

}
