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

import com.google.common.base.Function;
import se.sics.kompics.ComponentCore;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Port;
import se.sics.kompics.PortType;

import java.util.List;

class AnswerRequestSpec implements SingleEventSpec {
  private final Class<? extends KompicsEvent> requestType;
  private final Port<? extends PortType> requestPort;
  private final ComponentCore proxyComponent;

  private boolean usesMapper;

  // mapper
  private Port<? extends PortType> responsePort;
  private Function<? extends KompicsEvent, ? extends KompicsEvent> mapper;
  private boolean immediateResponse;
  private KompicsEvent response;

  // future
  private Future<? extends KompicsEvent, ? extends KompicsEvent> future;

  List<AnswerRequestSpec> requestSequence;
  boolean isFinalRequest;

  AnswerRequestSpec(Class<? extends KompicsEvent> requestType,
                    Port<? extends PortType> requestPort,
                    Function<? extends KompicsEvent, ? extends KompicsEvent> mapper,
                    Port<? extends PortType> responsePort,
                    boolean immediateResponse,
                    ComponentCore proxyComponent) {
    this(requestType, requestPort, proxyComponent);
    this.responsePort = responsePort;
    this.mapper = mapper;
    this.immediateResponse = immediateResponse;
    usesMapper = true;
  }

  AnswerRequestSpec(Class<? extends KompicsEvent> requestType,
                    Port<? extends PortType> requestPort,
                    Future<? extends KompicsEvent, ? extends KompicsEvent> future,
                    ComponentCore proxyComponent) {
    this(requestType, requestPort, proxyComponent);
    this.future = future;
  }

  private AnswerRequestSpec(Class<? extends KompicsEvent> requestType,
                    Port<? extends PortType> requestPort,
                    ComponentCore proxyComponent) {
    this.requestType = requestType;
    this.requestPort = requestPort;
    this.proxyComponent = proxyComponent;
  }

  @Override
  public boolean match(EventSpec receivedSpec) {
    if (!(receivedSpec.getPort().equals(requestPort) &&
        requestType.isAssignableFrom(receivedSpec.getEvent().getClass()))) {
      return false;
    }

    boolean matched = handleReceivedSpec(receivedSpec);
    if (matched) {
      receivedSpec.disable(); // request should not be forwarded
    }
    return matched;
  }

  private boolean handleReceivedSpec(EventSpec receivedSpec) {
    KompicsEvent event = receivedSpec.getEvent();
    if (usesMapper) {
      return extractResponse(event, mapper);
    } else {
      return setFuture(event, future);
    }
  }

  private <RQ extends KompicsEvent> boolean setFuture(
      KompicsEvent event, Future<RQ, ? extends KompicsEvent> future) {
    return future.set((RQ) event);
  }

  private <RQ extends KompicsEvent, RS extends KompicsEvent> boolean extractResponse(
      KompicsEvent event, Function<RQ, RS> mapper) {
    response = mapper.apply((RQ) event);
    if (response == null) {
      return false;
    }

    if (immediateResponse) {
      triggerResponse();
    } else if (isFinalRequest) {
      for (AnswerRequestSpec spec : requestSequence) {
        spec.triggerResponse();
      }
    }
    return true;
  }

  void triggerResponse() {
    if (usesMapper) {
      assert response != null;
      responsePort.doTrigger(response, 0, proxyComponent);
    }
  }
}
