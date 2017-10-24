package se.sics.kompics.testing;

import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Port;
import se.sics.kompics.PortType;

/**
 *  Represents an 'external' event performed by a component under test CUT.
 *  An external event is a triple (e,p,d) where e is the KompicsEvent instance
 *  observed/intercepted by a {@link ProxyHandler}, p is the port on
 *  which e occurred and d is the direction (incoming or outgoing) of e
 *  with respect to the CUT.
 *  @see EventSymbol#event
 *  @see EventSymbol#port
 *  @see EventSymbol#direction
 *  Instances of this class form the input symbols to be consumed by the constructed NFA.
 */
class EventSymbol {
    // The intercepted KompicsEvent message associated with this symbol.
    private final KompicsEvent event;

    // The port that event occurred.
    private final Port<? extends PortType> port;

    // The event's direction with respect to the CUT.
    private final Direction direction;

    // The handler that observed this event.
    private final ProxyHandler handler;

    // Set to true if event should be forwarded to its recipient(s).
    private boolean forwardEvent = true;

    EventSymbol(KompicsEvent event,
                Port<? extends PortType> port,
                Direction direction,
                ProxyHandler handler) {
        this.event = event;
        this.port = port;
        this.direction = direction;
        this.handler = handler;
    }

    // Forward the event associated with this symbol.
    void forwardEvent() {
        if (forwardEvent) {
            handler.forwardEvent(event);
             // forward event exactly once
            forwardEvent = false;
        }
    }

    KompicsEvent getEvent() {
        return event;
    }

    Port<? extends PortType> getPort() {
        return port;
    }

    Direction getDirection() {
        return direction;
    }

    ProxyHandler getHandler() {
        return handler;
    }

    void setForwardEvent(boolean forwardEvent) {
        this.forwardEvent = forwardEvent;
    }

    public String toString() {
        return direction + " " + event;
    }

    static final EventSymbol EPSILON = new EventSymbol(
        new KompicsEvent() { }, null, null, null) {
        @Override
        public String toString() {
            return "EPSILON";
        }
    };
}
