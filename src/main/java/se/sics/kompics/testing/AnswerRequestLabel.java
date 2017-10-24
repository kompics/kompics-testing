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

import com.google.common.base.Function;
import se.sics.kompics.ComponentCore;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Port;
import se.sics.kompics.PortType;

import java.util.List;

/**
 * Match an outgoing KompicsEvent rq as a request and if successful,
 * trigger a response rs on a specified port.
 *
 * The response rs is provided by invoking a mapper {@link com.google.common.base.Function},
 * if one was provided. Otherwise a future {@link Future} is invoked with
 * the matched event rq and no response is triggered.
 */
class AnswerRequestLabel implements SingleLabel{

    // Proxy.
    private final ComponentCore proxyComponent;

    // The class to which the expected request event belongs.
    private final Class<? extends KompicsEvent> requestType;

    /** Mapper **/

    // Expected port on which the request occurs.
    private final Port<? extends PortType> requestPort;

    // Triggered response.
    private KompicsEvent response;

    // The Port on which a response will be triggered.
    private Port<? extends PortType> responsePort;

    // Mapper function for creating a triggered response.
    private Function<? extends KompicsEvent, ? extends KompicsEvent> mapper;

    // Set to true if the response should be triggered immediately
    // after the request is matched.
    private boolean triggerImmediately;

    /** Future **/
    // Future to be invoked with a request if no mapper was provided.
    private Future<? extends KompicsEvent, ? extends KompicsEvent> future;

    // Set to true if a mapper function is available.
    private boolean hasMapper;

    /** If this label belongs to a sequence of answerRequest statements
     * then set the sequence.
     *
     * This is used only in batch mode to trigger the label responses
     * after all requests have been matched. Responses are triggered
     * in the sequence's order.
     * @see TestContext#answerRequests()
     */
    private List<AnswerRequestLabel> requestSequence;

    // Set to true if this is the last request label to be
    // matched in requestSequence.
    private boolean isLastRequest;

    // Init
    private AnswerRequestLabel(Class<? extends KompicsEvent> requestType,
                               Port<? extends PortType> requestPort,
                               ComponentCore proxyComponent) {
        this.requestType = requestType;
        this.requestPort = requestPort;
        this.proxyComponent = proxyComponent;
    }

    // Create with mapper.
    AnswerRequestLabel(Class<? extends KompicsEvent> requestType,
                       Port<? extends PortType> requestPort,
                       Function<? extends KompicsEvent, ? extends KompicsEvent> mapper,
                       Port<? extends PortType> responsePort,
                       boolean triggerImmediately,
                       ComponentCore proxyComponent) {
        this(requestType, requestPort, proxyComponent);
        this.responsePort = responsePort;
        this.mapper = mapper;
        this.triggerImmediately = triggerImmediately;
        hasMapper = true;
    }

    // Create with Future.
    AnswerRequestLabel(Class<? extends KompicsEvent> requestType,
                       Port<? extends PortType> requestPort,
                       Future<? extends KompicsEvent, ? extends KompicsEvent> future,
                       ComponentCore proxyComponent) {
        this(requestType, requestPort, proxyComponent);
        this.future = future;
    }


    // Return true if the class of the event associated with this
    // symbol is a subtype of the expected request type.
    // If a match is successful, the NFA does 'not' forward the event
    // to any recipient.
    @Override
    public boolean match(EventSymbol eventSymbol) {
        // Does the observed event have the same port and subtype
        // as we are expecting?
        if (!requestPort.equals(eventSymbol.getPort())
            || !requestType.isAssignableFrom(eventSymbol.getEvent().getClass()))
            return false;

        // If yes, then we try to consider it as a request using
        // either a mapper or future.
        KompicsEvent request = eventSymbol.getEvent();
        boolean success = hasMapper ? generateResponse(request, mapper)
                                    : invokeFuture(request, future);

        // Prevent the NFA from forwarding this event.
        if (success)
            eventSymbol.setForwardEvent(false);

        return success;
    }

    // Try to generate a response by invoking mapper with request.
    // If successful, either trigger the response immediately or save
    // the response.
    // Return true if response was successfully generated.
    private <RQ extends KompicsEvent, RS extends KompicsEvent>
    boolean generateResponse(KompicsEvent request, Function<RQ, RS> mapper) {
        // Try to generate response.
        KompicsEvent temp = mapper.apply((RQ) request);

        // Were we successful?
        if (temp == null) {
            return false;
        }

        // If yes, save the generated response.
        response = temp;

        // Should we trigger the response now?
        if (triggerImmediately) {
            triggerResponse();
        } else if (isLastRequest) {
            // We are in batch mode.
            // If this is the last request in sequence,
            // then trigger the requests' response in order.
            for (AnswerRequestLabel label : requestSequence) {
                label.triggerResponse();
            }
        }

        return true;
    }

    // Trigger saved response and reset this label.
    void triggerResponse() {
        if (hasMapper) {
            assert response != null;
            responsePort.doTrigger(response, 0, proxyComponent);
            response = null;
        }
    }

    // Invoke the future with the request.
    private <RQ extends KompicsEvent>
    boolean invokeFuture(KompicsEvent request,
                         Future<RQ, ? extends KompicsEvent> future) {
        return future.set((RQ) request);
    }

    void setRequestSequence(List<AnswerRequestLabel> requestSequence) {
        this.requestSequence = requestSequence;
    }

    void setLastRequest(boolean isLastRequest) {
        this.isLastRequest = isLastRequest;
    }
}
