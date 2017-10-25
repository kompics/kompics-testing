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

import se.sics.kompics.Channel;
import se.sics.kompics.ChannelCore;
import se.sics.kompics.ChannelFactory;
import se.sics.kompics.Handler;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Port;
import se.sics.kompics.PortCore;
import se.sics.kompics.PortType;
import se.sics.kompics.Unsafe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *  Maintains for a single port P owned by the CUT, the
 *  connected ports to P. Sets up event monitoring on P by
 *  subscribing appropriate {@link ProxyHandler} handlers on
 *  the inside port mirroring P or outside ports of P.
 */
class PortStructure {

    // Contains the ports connected to P.
    private List<Port<? extends PortType>> connectedPorts =
        new ArrayList<Port<? extends PortType>>();

    // The inside port of proxy component (mirroring
    // the CUT's inside port of P).
    // This port receives incoming events to the CUT on port P.
    private Port<? extends PortType> mirroredInsidePort;

    /**
     *  Contains {@link ProxyHandler} that handles events received on
     *  the proxy's mirrored inside port of P {@link #mirroredInsidePort}.
     *  A handler is added for each possible event type expected
     *  in the test case
     */
    private Set<InboundHandler> inboundHandlers =
        new HashSet<InboundHandler>();

    /**
     * The outside port of P.
     * Incoming events, received on the {@link #mirroredInsidePort}
     * are triggered on this port (if forwarded).
     * This port receives outgoing events from the CUT on port P.
     */
    private Port<? extends PortType> outsidePort;

    /**
     *  Contains {@link ProxyHandler} that handles events
     *  going out of port P.
     *  Each handler is subscribed on the outside port
     *  of P {@link #outsidePort} for each possible event
     *  on outgoing on P.
     */
    private Set<OutBoundHandler> outBoundHandlers =
        new HashSet<OutBoundHandler>();

    // Set to true P is a provided port of the CUT and
    // false if it is a required port.
    boolean isProvidedPort;

    // Proxy component.
    private Proxy proxy;

    // Contains the channels connecting P to a given port.
    private Map<Port<? extends PortType>, Channel> portToChannel =
        new HashMap<Port<? extends PortType>, Channel>();

    // Create a port structure for a port P and subscribe handlers
    // for all possible events on P.
    PortStructure(Proxy proxy,
                  Port<? extends PortType> mirroredInsidePort,
                  Port<? extends PortType> outsidePort,
                  boolean isProvidedPort) {
        this.proxy = proxy;
        this.mirroredInsidePort = mirroredInsidePort;
        this.outsidePort = outsidePort;
        this.isProvidedPort = isProvidedPort;

        assert mirroredInsidePort.getPortType() == outsidePort.getPortType();

        // Add intercepting handlers for events on P.
        addProxyHandlers();
    }

    // Return the ports connected to P.
    List<Port<? extends PortType>> getConnectedPorts() {
        return connectedPorts;
    }

    // Return the outside port of the CUT
    Port<? extends PortType> getOutsidePort() {
        return outsidePort;
    }

    // Return the mirrored inside port of P.
    Port<? extends PortType> getMirroredInsidePort() {
        return mirroredInsidePort;
    }

    /**
     * Connect the provided port $pair to the outside port {@link #outsidePort}
     * of P using the provided channel $factory.
     * This works by adding $pair to the list of ports connected to P and
     * using $factory to connect P's mirrored port
     * {@link #mirroredInsidePort} with $pair.
     */
    @SuppressWarnings("unchecked")
    <P extends PortType> void connect(PortCore<P> pair,
                                      ChannelFactory factory) {
        // Save the connection channel.
        Channel<P> channel;

        // Get the outside of the mirrored port.
        PortCore<P> outsideMirror = (PortCore<P>) mirroredInsidePort.getPair();

        // factory.connect takes the positive port as first argument
        // and negative port as second argument so find them.
        if (isProvidedPort)
            channel = factory.connect(outsideMirror, pair);
        else
            channel = factory.connect(pair, outsideMirror);

        // Save pair.
        connectedPorts.add(pair);

        // Save channel connected to pair.
        portToChannel.put(pair, channel);
    }

    // Return the channel connected from this mirrored port
    // to the given port pair
    @SuppressWarnings("unchecked")
    <P extends PortType> ChannelCore<P> getChannel(Port<P> pair) {
        // Retrieve previously saved channel.
        Channel channel = portToChannel.get(pair);
        assert channel != null;
        return (ChannelCore<P>) channel;
    }

    // Set up handlers to intercept events on port P as well as
    // its mirrored port.
    // For each possible supertype of event e allowed on P's port type,
    // subscribe a handler exactly once - no handlers are subscribed for
    // subtypes since they are already covered by the handlers of supertypes.
    private void addProxyHandlers() {
        // Get the port type of P.
        PortType portType = mirroredInsidePort.getPortType();

        // Get P's allowed event types in the positive direction.
        Collection<Class<? extends KompicsEvent>> positiveEvents =
            Unsafe.getPositiveEvents(portType);

        // Get P's allowed event types in the negative direction.
        Collection<Class<? extends KompicsEvent>> negativeEvents =
            Unsafe.getNegativeEvents(portType);

        // Is P is a provided port?
        if (isProvidedPort) {
            // If yes, positive events are outgoing while
            // negative events are incoming on P.
            monitorOutgoingEvents(positiveEvents);
            monitorIncomingEvents(negativeEvents);
        } else {
            // Otherwise, negative events are outgoing while
            // positive events are incoming on P.
            monitorOutgoingEvents(negativeEvents);
            monitorIncomingEvents(positiveEvents);
        }
    }

    // Subscribe intercepting handlers for outgoing events of each provided event type.
    private void monitorOutgoingEvents(
        Collection<Class<? extends KompicsEvent>> eventTypes) {

        for (Class<? extends KompicsEvent> eventType : eventTypes) {
            // Have we already subscribed a handler for a super type?
            if (!hasEquivalentHandler(eventType, outBoundHandlers)) {
                // If no, then subscribe handler for this type.
                OutBoundHandler outBoundHandler =
                    new OutBoundHandler(proxy, this, eventType);

                // Subscribe the handler on P's outside port.
                outsidePort.doSubscribe(outBoundHandler);

                // Remember subscribed handler.
                outBoundHandlers.add(outBoundHandler);
            }
        }
    }

    // Subscribe intercepting handlers for incoming events of each provided event type.
    private void monitorIncomingEvents(
        Collection<Class<? extends KompicsEvent>> eventTypes) {

        for (Class<? extends KompicsEvent> eventType : eventTypes) {
            // Have we already subscribed a handler for a super type?
            if (!hasEquivalentHandler(eventType, inboundHandlers)) {
                // If no, then subscribe handler for this type.
                InboundHandler inboundHandler =
                    new InboundHandler(proxy, this, eventType);

                // Subscribe the handler on P's outside port.
                mirroredInsidePort.doSubscribe(inboundHandler);

                // Remember subscribed handler.
                inboundHandlers.add(inboundHandler);
            }
        }
    }

    // Return true if a handler for a supertype of eventType has
    // already been subscribed.
    // If a handler h is a subType of eventType, h is removed from handlers.
    private boolean hasEquivalentHandler(Class<? extends KompicsEvent> eventType,
                                         Set<? extends Handler> handlers) {
        Iterator<? extends Handler> it = handlers.iterator();
        while (it.hasNext()) {
            Handler h = it.next();
            if (h.getEventType().isAssignableFrom(eventType)) {
                // A handler for a superType exists.
                return true;
            } else if (eventType.isAssignableFrom(h.getEventType())) {
                // A handler for a subType exists - remove it.
                it.remove();
                // Now no equivalent handler exists.
                break;
            }
        }

        // No equivalent handler exists.
        return false;
    }
}
