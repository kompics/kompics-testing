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

import se.sics.kompics.ChannelFactory;
import se.sics.kompics.ControlPort;
import se.sics.kompics.JavaPort;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.LoopbackPort;
import se.sics.kompics.Negative;
import se.sics.kompics.Port;
import se.sics.kompics.PortCore;
import se.sics.kompics.PortType;
import se.sics.kompics.Positive;
import se.sics.kompics.Unsafe;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static se.sics.kompics.testing.Direction.*;

/**
 * Set up the ports owned (provided and required) by the CUT
 * in order to monitor events that go in and out of them.
 */
class PortConfig {
    /**
     *  Contains a {@link PortStructure} instance for each
     *  port owned by the CUT.
     */
    private final Map<Port<? extends PortType>, PortStructure> portStructs =
        new HashMap<Port<? extends PortType>, PortStructure>();

    // The proxy component.
    private final Proxy proxy;

    PortConfig(Proxy proxy) {
        this.proxy = proxy;

        // Monitor provided ports of the CUT.
        monitorPorts(true);

        // Monitor required ports of the CUT.
        monitorPorts(false); // required ports
    }

    /**
     *  Create a {@link PortStructure} instance for each
     *  port provided or required by the CUT.
     *  if provided is set to true, then the provided
     *  ports are set up - otherwise the required ports are
     *  set up.
     */
    @SuppressWarnings("unchecked")
    private void monitorPorts(boolean provided) {
        // Contains the provided or required outside ports of the CUT
        // depending on the value of $provided.
        // These ports have their owners set to proxy.
        Map<Class<? extends PortType>, JavaPort<? extends PortType>> ports;

        // Should we set up the provided ports?
        if (provided)
            // If yes, then get the CUT's positive ports.
            ports = proxy.getCutPositivePorts();
        else
            // Otherwise, we get its negative ports.
            ports = proxy.getCutNegativePorts();

        // For each port P, create a similar port on the proxy
        // component and initialize a PortStructure for P.
        for (Map.Entry<Class<? extends PortType>, JavaPort<? extends PortType>> entry : ports.entrySet()) {
            // The PortType of P.
            Class<? extends PortType> portType = entry.getKey();

            // The outside port of P.
            Port<? extends PortType> outsidePort = entry.getValue();

            // Do not monitor Control and Loopback ports.
            if (!isMonitoredPort(portType))
                continue;

            // Create a port mirroring P and return its inside port.
            Port<? extends PortType> mirroredInsidePort;
            if (provided)
                mirroredInsidePort = proxy.providePort(portType);
            else
                mirroredInsidePort = proxy.requirePort(portType);

            // Create a PortStructure for P.
            PortStructure portStruct = new PortStructure(proxy,
                                                         mirroredInsidePort,
                                                         outsidePort,
                                                         provided);

            // Finally, add the created PortStructure for P.
            portStructs.put(outsidePort, portStruct);
        }
    }

    // Connect the positive and negative port via the provided
    // channel factory and monitor the ports owned by the CUT.
    <P extends PortType> void connect(Positive<P> positive,
                                      Negative<P> negative,
                                      ChannelFactory factory) {

        // Mark the inside port(s) of the CUT
        // (outside ports will always belong to proxy).
        boolean cutOwnsPositive =
            positive.getPair().getOwner() == proxy.getComponentUnderTest();
        boolean cutOwnsNegative =
            negative.getPair().getOwner() == proxy.getComponentUnderTest();

        // Set the port owned by the CUT. This port will be monitored.
        PortCore<P> cutPort = (PortCore<P>) (cutOwnsPositive ? positive
                                                             : negative);

        // Set the port owned by the other component C. This will only be
        // monitored if C is also the CUT.
        PortCore<P> otherPort = (PortCore<P>) (cutOwnsPositive ? negative
                                                               : positive);

        // Monitor events going in and out of the CUT's port.
        portStructs.get(cutPort).connect(otherPort, factory);

        // Monitor events on the other port if the CUT owns it as well.
        if (cutOwnsPositive && cutOwnsNegative)
            portStructs.get(otherPort).connect(cutPort, factory);
    }

    // Return true if the provided port P is monitored by this PortConfig and
    // there is at least one other port connected to P.
    boolean isConnectedPort(Port<? extends PortType> P) {
        // Get the PortStructure for P.
        PortStructure portStruct = portStructs.get(P);

        if (portStruct != null && !portStruct.getConnectedPorts().isEmpty())
            return true;

        // Port is not monitored and connected.
        return false;
    }

    // Return true if the port type of port P allows events belonging
    // to class eventType in the specified direction.
    boolean portDeclaresEvent(Class<? extends KompicsEvent> eventType,
                              Port<? extends PortType> P,
                              Direction direction) {

        // Get the PortStructure for port P.
        PortStructure portStruct = portStructs.get(P);

        // Contains the event types allowed by P's portType in the
        // specified direction.
        Collection<Class<? extends KompicsEvent>> allowedTypes;

        // Is P a provided port of the CUT?
        if (portStruct.isProvidedPort) {
            // If yes, Negative events are incoming to P while
            // Positive events are outgoing from P.
            if (direction == IN)
                allowedTypes = Unsafe.getNegativeEvents(P.getPortType());
            else
                allowedTypes = Unsafe.getPositiveEvents(P.getPortType());
        } else {
            // Otherwise, Positive events are incoming to P while
            // Negative events are outgoing from P.
            if (direction == IN)
                allowedTypes = Unsafe.getPositiveEvents(P.getPortType());
            else
                allowedTypes = Unsafe.getNegativeEvents(P.getPortType());
        }

        // Return true if the specified eventType is allowed.
        for (Class<? extends KompicsEvent> type : allowedTypes) {
            if (type.isAssignableFrom(eventType)) {
                return true;
            }
        }
        return false;
    }

    // Return true if the specified port class should not be monitored
    // for events by the framework.
    private boolean isMonitoredPort(Class<? extends PortType> portClass) {
        return !(portClass.equals(LoopbackPort.class) || portClass.equals(ControlPort.class));
    }

}
