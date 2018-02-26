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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.slf4j.Logger;

import se.sics.kompics.ComponentCore;
import se.sics.kompics.ControlPort;
import se.sics.kompics.Direct;
import se.sics.kompics.Fault;
import se.sics.kompics.Handler;
import se.sics.kompics.JavaPort;
import se.sics.kompics.PortType;
import se.sics.kompics.Port;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Start;
import se.sics.kompics.Unsafe;

import static se.sics.kompics.testing.Direction.IN;
import static se.sics.kompics.testing.Direction.OUT;

/**
 * Simulate an NFA.
 */
class Simulator {
    // LOG.
    private static final Logger logger = TestContext.logger;

    // The result of running this testcase.
    private SettableFuture<Boolean> result;

    /** The {@link Proxy} for this testcase  */
    private ComponentCore proxyComponent;

    private long timeoutMS = TestContext.timeout;

    private final EventQueue eventQueue = new EventQueue();

    // Timer task that ends the test case if no event was observed
    // within the specified timeout.
    private TimerTask eventTimerTask;

    // Timestamp for the last scheduled timer task.
    private long latestScheduledTimeout;

    private final Timer eventTimer = new Timer("kompics-testing-event-timer");

    // Result of the last transition traversed.
    private TransitionResult lastTransition;

    // Synchronize access between to the fsm the component
    // and event timer threads.
    private final Semaphore semaphore = new Semaphore(1);

    // The Non-Deterministic Finite Automaton with which we run the
    // entire test case.
    private NFA nfa;

    void setNfa(NFA nfa) {
        this.nfa = nfa;
    }

    void setProxyComponent(ComponentCore proxyComponent) {
        this.proxyComponent = proxyComponent;
    }

    // Set the maximum we should wait for events to be observed
    // in the test case environment.
    void setTimeout(long timeoutMS) {
        if (timeoutMS < 0) {
            throw new IllegalStateException("Timeout value must be non-negative.");
        }

        this.timeoutMS = timeoutMS;
    }

    // Run this test case.
    public ListenableFuture<Boolean> run() {
        // Have we run this test case previously?
        if (result != null) {
            SettableFuture<Boolean> future = SettableFuture.create();
            future.setException(new IllegalStateException("State machine has previously been executed"));
            return future;
        }

        result = SettableFuture.create();

        // Send start event to proxy (and all child components).
        proxyComponent.getControl().doTrigger(Start.event, 0, proxyComponent);

        // Start event timeout.
        rescheduleEventTimeout();

        return result;
    }

    public boolean doTransition(KompicsEvent event, Port<?> port, Direction direction) {
        return doTransition(new EventSymbol(event, port, direction));
    }

    public boolean doTransition(EventSymbol eventSymbol) {
        KompicsEvent event = eventSymbol.getEvent();
        Port port = eventSymbol.getPort();
        Direction direction = eventSymbol.getDirection();

        boolean isFault = isComponentFaultEvent(eventSymbol);

        // Are we supposed to ignore this event?
        if (portIsIgnored(port) && !isFault) {
            return true;
        }

        // Has this test case already ended?
        if (testcaseTerminated()) {
            // If yes, then we can not do anything with it.
            return false;
        }

        // We need to handle incoming Direct Requests specially.
        if (direction == IN && event instanceof Direct.Request) {
            setupResponsePortForIncomingRequest((Direct.Request) event, port);
        }

        // Add the event to the queue.
        if (isFault) {
            // Since we want to immediately assert that an exception
            // was thrown while executing some handler, we add Fault
            // events to the queue's head.
            eventQueue.addFirst(eventSymbol);
        } else {
            eventQueue.offer(eventSymbol);
        }

        // Try to acquire the lock and do transitions.
        boolean hasLock = semaphore.tryAcquire();
        try {
            // If we are able to acquire the lock then we can
            // transition the fsm.
            if (hasLock && !result.isDone()) {
                doEventTransitions();
            }

            if (!testcaseTerminated()) {
                // Always reschedule timeout on any observed event.
                rescheduleEventTimeout();
            }
        } finally {
            // Release lock after rescheduling a new timeout so it is visible to
            // any previous timeout.
            if (hasLock) {
                semaphore.release();
            }
        }

        // We always queue the event and trigger later.
        return false;
    }

    private boolean portIsIgnored(Port<?> port) {
        // Return true if we should not observe events on this port.
        return port.getPortType().getClass() == ControlPort.class;
    }

    private boolean isComponentFaultEvent(EventSymbol eventSymbol) {
        return eventSymbol.getPort().getPortType().getClass() == ControlPort.class
            && eventSymbol.getEvent() instanceof Fault;
    }

    private boolean doEventTransitions() {
        // Set to true if we manage to follow any transition
        // based on an observed event.
        boolean transitioned = false;

        EventSymbol eventSymbol;
        while ((eventSymbol = eventQueue.poll()) != null) {
            transitioned = true;

            // Try any internal events first.
            lastTransition = nfa.tryInternalEventTransitions();

            // If we end up in an error state, then return immediately
            // otherwise try to match the event.
            if (lastTransition == null || !lastTransition.inErrorState) {
                lastTransition = nfa.doTransition(eventSymbol);
            }

            if (lastTransition.inErrorState) {
                completeTestcaseResult(false);
                break;
            }

            // Forward the event if needed.
            if (lastTransition.forwardEvent) {
                eventSymbol.forwardEvent();
            }
        }

        return transitioned;
    }

    private void rescheduleEventTimeout() {
        // The timestamp for this timeout task.
        final long timestamp = System.currentTimeMillis();

        // Cancel the previous timeout task.
        if (eventTimerTask != null) {
            eventTimerTask.cancel();
        }

        // Create the event task and mark as the latest.
        latestScheduledTimeout = timestamp;
        eventTimerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    endTestcase(timestamp);
                } catch (Throwable t) {
                    t.printStackTrace();
                    eventTimer.cancel();
                    completeTestcaseResult(false);
                }
            }
        };

        try {
            eventTimer.schedule(eventTimerTask, timeoutMS);
        } catch (IllegalStateException ignored) {
            // If timeout is already cancelled.
        }
    }

    private void endTestcase(long scheduledTimeout) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        try {
            // If the testcase has already completed or a new timeout task
            // has already been scheduled (since we woke up) then we don't do
            // anything.
            if (testcaseTerminated() || latestScheduledTimeout != scheduledTimeout) {
                return;
            }

            // We try to make some progress and if unable to,
            // then we end the testcase.
            // Did we perform any transitions during this timeout?
            if (makeProgress()) {
                // If yes, then we reschedule the event timeout for next time.
                rescheduleEventTimeout();
            } else {
                // Otherwise we end the test case based on the last transition made if any.
                if (lastTransition != null) {
                    completeTestcaseResult(
                        !lastTransition.inErrorState && lastTransition.inFinalState);
                } else {
                    // No events were received for this test case.
                    completeTestcaseResult(false);
                    eventTimer.cancel();
                }
            }
        } finally {
            semaphore.release();
        }
    }

    private boolean makeProgress() {
        // Make progress on the fsm by following as
        // many transitions as allowed.
        // First we 1) try to take observed event
        // transitions followed by 2) internal transitions.
        // Force an event transitions only if both alternatives fail.

        boolean madeProgress = false;
        boolean performedTransition;
        do {
            // First we try to clear the event queue for any observed
            // events yet to be handled.
            boolean hadPendingEvents = doEventTransitions();
            // If this led to the test case terminating, then we're done.
            if (testcaseTerminated()) {
                break;
            }

            // Next, we can always try to perform internal
            // transitions if we are not expecting an event.
            TransitionResult internalTransition = nfa.tryInternalEventTransitions();
            if (internalTransition != null) {
                lastTransition = internalTransition;
                if (setResultIfInErrorState(internalTransition)) {
                    break;
                }
            } else if (!hadPendingEvents && !madeProgress){
                // We have been are unable to perform any transitions at all
                // so we try to force an internal transition.
                internalTransition = nfa.performInternalEventTransitions();
                if (internalTransition != null) {
                    lastTransition = internalTransition;
                    if (setResultIfInErrorState(internalTransition)) {
                        break;
                    }
                }
            }

            performedTransition = hadPendingEvents || internalTransition != null;
            madeProgress |= performedTransition;
        } while (performedTransition);

        return madeProgress;
    }

    private boolean setResultIfInErrorState(TransitionResult tResult) {
        if (tResult.inErrorState) {
            result.set(false);
        }
        return tResult.inErrorState;
    }

    private void completeTestcaseResult(boolean testcaseResult) {
        if (!testcaseTerminated()) {
            result.set(testcaseResult);
        }
    }

    private boolean testcaseTerminated() {
        return result.isDone();
    }

    /**
     *  For a new incoming Direct.Request event R, we replace R's origin
     *  port O with a custom port P and save P for any future
     *  events originating from O.
     */
    private final Map<Port, JavaPort> originToResponsePort =
        new HashMap<Port, JavaPort>();
    /**
     *  For incoming {@link Direct.Request} events, responses are
     *  normally triggered directly on the origin port.
     *
     *  We instead make sure that the response is
     *  routed back onto a custom port instead, after which the
     *  response can be forwarded if needed.
     *
     *  This works by creating the custom (response) port G for an
     *  incoming request R and replacing R's origin with G.
     *  Special handlers are subscribed to G that intercept R's response.
     *
     *  To prevent G being created every time an event from the
     *  same origin O is received, we store G in {@link #originToResponsePort}.
     */
    private <P extends PortType>
    void setupResponsePortForIncomingRequest(Direct.Request<?> request,
                                             Port<P> port) {
        Port origin = Unsafe.getOrigin(request);

        if (origin == null) {
            logger.warn("Origin port was Null for incoming request [{}].", request);
            return;
        }

        JavaPort<P> responseOutsidePort = originToResponsePort.get(origin);

        if (responseOutsidePort == null) {
            responseOutsidePort = Unsafe.createJavaPort(true, port.getPortType(), proxyComponent);
            JavaPort<P> responseInsidePort =
                Unsafe.createJavaPort(true, port.getPortType(), proxyComponent);
            responseOutsidePort.setPair(responseInsidePort);
            responseInsidePort.setPair(responseOutsidePort);
            subscribeResponseHandlers(origin, port, responseInsidePort);
        }

        Unsafe.setOrigin(request, responseOutsidePort);
    }

    // Subscribe a custom handler on the (inside) response port for
    // each possible response type
    private <P extends PortType>
    void subscribeResponseHandlers(final Port origin, final Port<P> CUTOutsidePort, JavaPort<P> responseInsidePort) {
        // Get the allowed types on the origin port.
        Collection<Class<? extends KompicsEvent>> insidePortEvents =
            Unsafe.getPositiveEvents(origin.getPortType());

        for (final Class<? extends KompicsEvent> eventType : insidePortEvents) {
            // Skip non response event types.
            if (!Direct.Response.class.isAssignableFrom(eventType)) {
                continue;
            }

            // Only add handlers to super types so that we don't receive
            // an event multiple times.
            boolean isSuperType = true;
            // If this is a subtype of some other response type - skip.
            for (Class<? extends KompicsEvent> other : insidePortEvents) {
                if (eventType != other && other.isAssignableFrom(eventType)) {
                    isSuperType = false;
                    break;
                }
            }

            if (isSuperType) {
                responseInsidePort.doSubscribe(new Handler() {
                    {
                        setEventType(eventType);
                    }

                    @Override
                    public void handle(KompicsEvent response) {
                        EventSymbol eventSymbol = new EventSymbol(
                            response, CUTOutsidePort, OUT
                        );

                        // Set the origin as the destination port
                        // for the response.
                        eventSymbol.setForwardingPort(origin);

                        // Do transition for response.
                        doTransition(eventSymbol);
                    }
                });
            }
        }
    }
}
