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

/**
 *  Intercept incoming events on a specified port.
 */
class InboundHandler extends ProxyHandler {

    // The destination port onto which intercepted
    // incoming events are forwarded.
    private final Port<? extends PortType> destPort;

    /**
     * The port type for {@link #destPort}
     */
    private final PortType portType;

    // The proxy component.
    private final ComponentCore proxyCore;

    /**
     *  For a new incoming Direct.Request event R, we replace R's origin
     *  port O with a custom port P and save P for any future
     *  events originating from O.
     */
    private final Map<Port, JavaPort> originToGhostPort =
        new HashMap<Port, JavaPort>();

    InboundHandler(Proxy proxy,
                   PortStructure portStruct,
                   Class<? extends KompicsEvent> eventType) {
        super(proxy, portStruct, eventType);
        this.destPort = portStruct.getOutsidePort();
        this.portType = destPort.getPortType();
        this.proxyCore = proxy.getComponentCore();
    }

    // Intercept an event.
    @Override
    public void handle(KompicsEvent event) {
        TestContext.logger.trace("received incoming event: {}", event);

        // Is this a Request event?
        if (event instanceof Request) {
            // If yes, add the proxy to the request's path
            // so that the response would go through proxy's
            // handlers (In this case the corresponding OutBoundHandler).
            Request request = (Request) event;
            request.pushPathElement(proxyCore);
        }

        // Is this a (incoming) Direct.Request?
        if (event instanceof Direct.Request) {
            // If yes, then make sure we route its response
            // back through us instead of directly to the
            // origin.
            Direct.Request<Direct.Response> request =
                (Direct.Request<Direct.Response>) event;

            setupDirectRequest(request, Unsafe.getOrigin(request));
        }

        // Create an input event symbol for this observed event.
        EventSymbol eventSymbol =new EventSymbol(event,
                                                 destPort,
                                                 Direction.IN,
                                                 this);

        // Add the symbol to the queue.
        eventQueue.offer(eventSymbol);
    }

    // Forward event to the destination port.
    @Override
    public void forwardEvent(KompicsEvent event) {
        destPort.doTrigger(event, 0, proxyCore);
    }

    /**
     *  For incoming {@link Direct.Request} events, responses are
     *  normally triggered directly on the origin port.
     *
     *  This method routes makes sure that the response is
     *  triggered onto a custom port instead, after which the saved
     *  origin port can be looked up and forwarded if needed.
     *
     *  This works by creating the custom (ghost) port G for an
     *  incoming request R and replacing R's origin with G.
     *  Special handlers are subscribed to G that consequently
     *  intercept R's response.
     *
     *  To prevent G being created every time an event from the
     *  same origin O is received, we store G in {@link #originToGhostPort}.
     */
    private <P extends PortType>
    void setupDirectRequest(Direct.Request request,
                            Port<P> origin) {
        // Get the ghost port for this origin if available.
        JavaPort<P> ghostOutsidePort = originToGhostPort.get(origin);

        // Is this the first time we are receiving an event from origin?
        if (ghostOutsidePort == null) {
            // If yes, then we instead create a new ghost port for origin.
            assert portType == origin.getPortType();

            // An incoming request implies that this is a provided port.
            boolean positivePort = portStruct.isProvidedPort;
            assert positivePort;

            // The port type of origin.
            P portType = origin.getPortType();

            // Create the custom ghost outside port.
            // This is the port on which the response will
            // be triggered directly.
            ghostOutsidePort = Unsafe.createJavaPort(!positivePort,
                                                     portType,
                                                     proxyCore);

            // Create the custom ghost inside port.
            // This is the port on which we subscribe handlers for
            // the triggered response.
            JavaPort<P> ghostInsidePort = Unsafe.createJavaPort(positivePort,
                                                                portType,
                                                                proxyCore);

            // Create G as pair of inside,outside ports.
            ghostOutsidePort.setPair(ghostInsidePort);
            ghostInsidePort.setPair(ghostOutsidePort);

            subscribeGhostHandlers(origin, ghostInsidePort);

            // Save created ghost port for next time.
            originToGhostPort.put(origin, ghostOutsidePort);
        }
        Unsafe.setOrigin(request, ghostOutsidePort);
    }

    // Subscribe a custom handler on the (inside) ghost port for
    // each possible response type
    private <P extends PortType>
    void subscribeGhostHandlers(Port<P> origin, JavaPort<P> ghost) {
        // Get the allowed types on the origin port.
        Collection<Class<? extends KompicsEvent>> insidePortEvents =
            Unsafe.getPositiveEvents(origin.getPortType());

        for (Class<? extends KompicsEvent> eventType : insidePortEvents) {
            // Skip non response event types.
            if (!Direct.Response.class.isAssignableFrom(eventType))
                continue;

            // Only add handlers to superTypes so that we don't receive
            // an event multiple times.
            boolean isSupertype = true;

            // If this is a subtype of some other response type - skip.
            for (Class<? extends KompicsEvent> other : insidePortEvents) {
                if (eventType != other && other.isAssignableFrom(eventType)) {
                    isSupertype = false;
                    break;
                }
            }

            // Otherwise, this is a super type - so we subscribe a handler.
            if (isSupertype)
                ghost.doSubscribe(new GhostHandler(
                    (Class<? extends Direct.Response>) eventType,
                    origin)
                );
        }
    }

    /**
     *  A ProxyHandler for intercepting {@link Direct.Response}
     *  events.
     *  This handler is subscribed on inside custom (ghost) port.
     */
    private class GhostHandler extends ProxyHandler {
      // The request origin.
      Port origin;

      GhostHandler(Class<? extends Direct.Response> eventType, Port origin) {
          super(InboundHandler.this.proxy,
                InboundHandler.this.portStruct,
                eventType);
          this.origin = origin;
      }

      @Override
      public void handle(KompicsEvent response) {
          // Create an input event symbol for this observed event.
          EventSymbol eventSymbol = new EventSymbol(response,
                                                    destPort,
                                                    Direction.OUT,
                                                    this);

          // Add the symbol to the queue.
          eventQueue.offer(eventSymbol);
      }

      // Forward event to the origin.
      @Override
      void forwardEvent(KompicsEvent response) {
          assert response instanceof Direct.Response;
          origin.doTrigger(response, 0, proxyCore);
      }
  }

}
