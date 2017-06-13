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

import se.sics.kompics.ComponentCore;
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

  private final Port<? extends PortType> destPort;
  private final PortType portType;
  private final ComponentCore proxyCore;
  // handle incoming Direct.Request
  private final Map<Port, JavaPort> originToGhostPort = new HashMap<Port, JavaPort>();

  InboundHandler(
          Proxy proxy, PortStructure portStruct, Class<? extends KompicsEvent> eventType) {
    super(proxy, portStruct, eventType);
    this.destPort = portStruct.getOutboundPort();
    this.portType = destPort.getPortType();
    this.proxyCore = proxy.getComponentCore();
  }

  @Override
  public void handle(KompicsEvent event) {
    TestContext.logger.trace("received incoming event: {}", event);
    if (event instanceof Request) {
      Request request = (Request) event;
      request.pushPathElement(proxy.getComponentCore());
    }

    if (event instanceof Direct.Request) {
      Direct.Request<Direct.Response> request = (Direct.Request<Direct.Response>) event;
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

  // for incoming requests, responses are normally triggered on origin port.
  // intercept requests and set custom port as origin to route response back to testing
  private <P extends PortType> void setupDirectRequest(Direct.Request request, Port<P> origin) {
    // if previously ghost port for this origin (avoids creating new ports for each incoming request)
    JavaPort<P> ghostOutsidePort = originToGhostPort.get(origin);

    if (ghostOutsidePort == null) {
      assert portType == origin.getPortType();

      boolean positivePort = portStruct.isProvidedPort;
      assert positivePort; // incoming request implies provided port
      P portType = origin.getPortType();

      // response will be triggered directly on outside port
      ghostOutsidePort = Unsafe.createJavaPort(!positivePort, portType, proxyCore);
      // custom handler is subscribed on insider port
      JavaPort<P> ghostInsidePort = Unsafe.createJavaPort(positivePort, portType, proxyCore);

      ghostOutsidePort.setPair(ghostInsidePort);
      ghostInsidePort.setPair(ghostOutsidePort);

      // subscribe a custom handler on custom inside port, for each possible response type
      Collection<Class<? extends KompicsEvent>> insidePortEvents = Unsafe.getPositiveEvents(portType);
      for (Class<? extends KompicsEvent> eventType : insidePortEvents) {
        if (Direct.Response.class.isAssignableFrom(eventType)) {
          ghostInsidePort.doSubscribe(new GhostHandler((Class<? extends Direct.Response>) eventType, origin));
        }
      }
      originToGhostPort.put(origin, ghostOutsidePort);
    }
    Unsafe.setOrigin(request, ghostOutsidePort);
  }

  // handler subscribed on inside custom port, intercepts responses and adds them to event queue
  private class GhostHandler extends ProxyHandler {
    // request origin
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
      origin.doTrigger(response, 0, proxyCore);
    }
  }

}
