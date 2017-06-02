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

class OutBoundHandler extends ProxyHandler {

  private Port<? extends PortType> sourcePort;
  private List<? extends Port<? extends PortType>> destPorts;

  OutBoundHandler(
          Proxy proxy, PortStructure portStruct, Class<? extends KompicsEvent> eventType) {
    super(proxy, portStruct, eventType);
    this.sourcePort = portStruct.getOutboundPort();
    this.destPorts = portStruct.getConnectedPorts();
  }

  @Override
  public void handle(KompicsEvent event) {
    TestContext.logger.trace("received event: {}, connected to {} ports", event, destPorts.size());

    if (event instanceof Request) {
      Request request = (Request) event;
      request.pushPathElement(proxy.getComponentCore());
    }

    if (event instanceof Direct.Request) {
      Direct.Request request = (Direct.Request) event;
      //// TODO: 6/1/17 not public
      Unsafe.setOrigin(request, portStruct.getInboundPort().getPair());
    }

    EventSpec eventSpec = proxy.getFsm().newEventSpec(event, sourcePort, Direction.OUT);
    eventSpec.setHandler(this);
    eventQueue.offer(eventSpec);
  }

  @Override
  public void doHandle(KompicsEvent event) {
    if (event instanceof Response) {
      deliverToSingleChannel((Response) event);
    } else {
      deliverToAllConnectedPorts(event);
    }
  }

  private void deliverToAllConnectedPorts(KompicsEvent event) {
    for (Port<? extends PortType> port : destPorts) {
      port.doTrigger(event, 0, portStruct.getChannel(port));
    }
  }

  private void deliverToSingleChannel(Response response) {
    RequestPathElement pe = response.getTopPathElement();
    if (pe != null && pe.isChannel()) {
      ChannelCore<?> caller = pe.getChannel();
      if (portStruct.isProvidedPort) {
        caller.forwardToNegative(response, 0);
      } else {
        caller.forwardToPositive(response, 0);
      }
    }
  }
}
