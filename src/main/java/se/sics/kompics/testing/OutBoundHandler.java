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

import se.sics.kompics.ChannelCore;
import se.sics.kompics.Direct;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Port;
import se.sics.kompics.PortType;
import se.sics.kompics.Request;
import se.sics.kompics.RequestPathElement;
import se.sics.kompics.Response;
import se.sics.kompics.Unsafe;

import java.util.List;

/**
 *  Intercept outgoing events on a specified port.
 */
class OutBoundHandler extends ProxyHandler {

    // The port on which we intercept events.
    private Port<? extends PortType> sourcePort;

    // The recipients of intercepted events.
    // These are the ports that are connected to sourcePort.
    private List<? extends Port<? extends PortType>> destPorts;

    OutBoundHandler(Proxy proxy,
                    PortStructure portStruct,
                    Class<? extends KompicsEvent> eventType) {
        super(proxy, portStruct, eventType);
        this.sourcePort = portStruct.getOutsidePort();
        this.destPorts = portStruct.getConnectedPorts();
    }

    // Intercept an event.
    @Override
    public void handle(KompicsEvent event) {
        TestContext.logger.trace("received event: {}", event);

        // Is this a Request event?
        if (event instanceof Request) {
            // If yes, add the proxy to the request's path
            // so that the response would go through proxy's
            // handlers (In this case the corresponding InBoundHandler).
            Request request = (Request) event;
            request.pushPathElement(proxy.getComponentCore());
        }

        // Is this a (outgoing) Direct Request?
        if (event instanceof Direct.Request) {
            // If yes, then by default, the response will be directly
            // triggered on the source port which means that we
            // will be unable to prevent the CUT's handlers from
            // handling it if needed.
            Direct.Request request = (Direct.Request) event;

            // To avoid this, we replace the origin of the request with
            // the outside port of the proxy's mirrored port so that the
            // response is instead triggered there. This way we properly
            // intercept and forward the response as needed.
            Unsafe.setOrigin(request,
                             portStruct.getMirroredInsidePort().getPair());
        }

        // Create an input event symbol for this observed event.
        EventSymbol eventSymbol = new EventSymbol(event,
                                                  sourcePort,
                                                  Direction.OUT,
                                                  this);

        // Add the symbol to the queue.
        eventQueue.offer(eventSymbol);
    }

    // Forward event to recipient(s).
    @Override
    public void forwardEvent(KompicsEvent event) {
        // Is this event a Response?
        if (event instanceof Response)
            // If yes, then only deliver through the request channel.
            deliverToSingleChannel((Response) event);
        else
            // Otherwise, broadcast event to all connected ports.
            deliverToAllConnectedPorts(event);
    }

    // Broadcast event to all ports connected to the source port.
    private void deliverToAllConnectedPorts(KompicsEvent event) {
        for (Port<? extends PortType> port : destPorts) {
            // Trigger directly via the port's connected channel.
            port.doTrigger(event, 0, portStruct.getChannel(port));
        }
    }

    // Trigger response event on next channel.
    private void deliverToSingleChannel(Response response) {
        // Get the next recipient in sequence.
        RequestPathElement pe = response.getTopPathElement();

        // Is it a channel?
        if (pe != null && pe.isChannel()) {
            // If yes, then trigger the response on the
            // port on the other end of the channel.
            ChannelCore<?> caller = pe.getChannel();
            if (portStruct.isProvidedPort)
                caller.forwardToNegative(response, 0);
            else
                caller.forwardToPositive(response, 0);
        }
    }
}
