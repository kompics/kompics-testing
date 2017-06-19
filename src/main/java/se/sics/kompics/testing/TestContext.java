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
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.Channel;
import se.sics.kompics.ChannelFactory;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentCore;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Fault;
import se.sics.kompics.Init;
import se.sics.kompics.Kompics;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Negative;
import se.sics.kompics.Port;
import se.sics.kompics.PortCore;
import se.sics.kompics.PortType;
import se.sics.kompics.Positive;
import se.sics.kompics.Scheduler;
import se.sics.kompics.Start;
import se.sics.kompics.scheduler.ThreadPoolScheduler;

import java.util.Comparator;


public class TestContext<T extends ComponentDefinition> {
  private final Proxy<T> proxy;
  private final ComponentCore proxyComponent;
  private T cut;
  private CTRL<T> ctrl;
  private Scheduler scheduler;
  private boolean checked;

  public static final Logger logger = LoggerFactory.getLogger("KompicsTesting");

  private TestContext(Init<? extends ComponentDefinition> initEvent, Class<T> definition) {
    proxy = new Proxy<T>();
    proxyComponent = proxy.getComponentCore();
    init();
    if (initEvent == Init.NONE) {
      cut = proxy.createComponentUnderTest(definition, (Init.None) initEvent);
    } else {
      cut = proxy.createComponentUnderTest(definition, (Init<T>) initEvent);
    }
    initFSM();
  }

  private TestContext(Class<T> definition, Init<T> initEvent) {
    this(initEvent, definition);
  }

  private TestContext(Class<T> definition, Init.None initEvent) {
    this(initEvent, definition);
  }

  public static <T extends ComponentDefinition> TestContext<T> newTestContext(
      Class<T> definition, Init<T> initEvent) {
    checkNotNull(definition, initEvent);
    return new TestContext<T>(definition, initEvent);
  }
  public static <T extends ComponentDefinition> TestContext<T> newTestContext(
      Class<T> definition) {
    return newTestContext(definition, Init.NONE);
  }

  public static <T extends ComponentDefinition> TestContext<T> newTestContext(
      Class<T> definition, Init.None initEvent) {
    checkNotNull(definition, initEvent);
    return new TestContext<T>(definition, initEvent);
  }

  public <T extends ComponentDefinition> Component create(
          Class<T> definition, Init<T> initEvent) {
    checkNotNull(definition, initEvent);
    Component c = proxy.createSetupComponent(definition, initEvent);
    ctrl.addParticipant(c);
    return c;
  }

  public <T extends ComponentDefinition> Component create(Class<T> definition) {
    return create(definition, Init.NONE);
  }

  public <T extends ComponentDefinition> Component create(
          Class<T> definition, Init.None initEvent) {
    checkNotNull(definition, initEvent);
    Component c = proxy.createSetupComponent(definition, initEvent);
    ctrl.addParticipant(c);
    return c;
  }

  public <P extends PortType> TestContext<T> connect(
          Negative<P> negative, Positive<P> positive) {
    return connect(positive, negative);
  }

  public <P extends PortType> TestContext<T> connect(
          Positive<P> positive, Negative<P> negative) {
    return connect(positive, negative, Channel.TWO_WAY);
  }

  <P extends PortType> TestContext<T> connect(
          Negative<P> negative, Positive<P> positive, ChannelFactory factory) {
    return connect(positive, negative, factory);
  }

  <P extends PortType> TestContext<T> connect(
          Positive<P> positive, Negative<P> negative, ChannelFactory factory) {
    checkNotNull(positive, negative, factory);
    ctrl.checkInInitialHeader();

    boolean cutOwnsPositive = positive.getPair().getOwner() == cut.getComponentCore();
    boolean cutOwnsNegative = negative.getPair().getOwner() == cut.getComponentCore();

    // non monitored ports => connect normally
    if (!(cutOwnsPositive || cutOwnsNegative)) {
      factory.connect((PortCore<P>) positive, (PortCore<P>) negative);
    } else {
      proxy.doConnect(positive, negative, factory);
    }

    return this;
  }

  public TestContext<T> repeat(int times) {
    ctrl.repeat(times);
    return this;
  }

  public TestContext<T> repeat() {
    ctrl.repeat();
    return this;
  }

  public TestContext<T> repeat(int times, BlockInit blockInit) {
    checkNotNull(blockInit);
    ctrl.repeat(times, blockInit);
    return this;
  }

  public TestContext<T> repeat(BlockInit blockInit) {
    checkNotNull(blockInit);
    ctrl.repeat(blockInit);
    return this;
  }

  public TestContext<T> end() {
    ctrl.end();
    return this;
  }

  public TestContext<T> body() {
    ctrl.body();
    return this;
  }

  public TestContext<T> unordered() {
    ctrl.setUnorderedMode();
    return this;
  }

  public <P extends  PortType> TestContext<T> expect(
          KompicsEvent event, Port<P> port, Direction direction) {
    checkNotNull(event, port, direction);
    checkValidPort(port, direction);
    ctrl.expectMessage(event, port, direction);
    return this;
  }

  public <P extends  PortType, E extends KompicsEvent> TestContext<T> expect(
          Class<E> eventType, Predicate<E> pred, Port<P> port, Direction direction) {
    checkNotNull(eventType, port, pred, direction);
    checkValidPort(port, direction);
    ctrl.expectMessage(eventType, pred, port, direction);
    return this;
  }

  public <P extends  PortType> TestContext<T> blockExpect(
          KompicsEvent event, Port<P> port, Direction direction) {
    checkNotNull(event, port, direction);
    checkValidPort(port, direction);
    ctrl.expectWithinBlock(event, port, direction);
    return this;
  }

  public <P extends  PortType, E extends KompicsEvent> TestContext<T> blockExpect(
          Class<E> eventType, Predicate<E> pred, Port<P> port, Direction direction) {
    checkNotNull(eventType, pred, port, direction);
    checkValidPort(port, direction);
    ctrl.expectWithinBlock(eventType, pred, port, direction);
    return this;
  }

  public TestContext<T> expectWithMapper() {
    ctrl.setExpectWithMapperMode();
    return this;
  }

  public TestContext<T> requestResponse() {
    ctrl.setExpectWithMapperMode();
    return this;
  }

  public TestContext<T> requestResponse(
      REQUEST_ORDERING request_ordering, RESPONSE_POLICY response_policy) {
    ctrl.setExpectWithMapperMode(request_ordering, response_policy);
    return this;
  }

  public <E extends KompicsEvent, R extends KompicsEvent> TestContext<T> setMapperForNext(
          int expectedEvents, Class<E> eventType, Function<E, R> mapper) {
    checkNotNull(eventType, mapper);
    ctrl.setMapperForNext(expectedEvents, eventType, mapper);
    return this;
  }

  public TestContext<T> expect(
          Port<? extends PortType> listenPort, Port<? extends PortType> responsePort) {
    checkNotNull(listenPort, responsePort);
    checkValidPort(listenPort, Direction.OUT);
    ctrl.addExpectWithMapper(listenPort, responsePort);
    return this;
  }

  public <E extends KompicsEvent, R extends KompicsEvent> TestContext<T> expect(
          Class<E> eventType, Port<? extends PortType> listenPort,
          Port<? extends PortType> responsePort, Function<E, R> mapper) {
    checkNotNull(eventType, listenPort, responsePort, mapper);
    checkValidPort(listenPort, Direction.OUT);
    ctrl.addExpectWithMapper(eventType, listenPort, responsePort, mapper);
    return this;
  }

  public TestContext<T> expectWithFuture() {
    ctrl.setExpectWithFutureMode();
    return this;
  }

  public <E extends KompicsEvent, R extends KompicsEvent> TestContext<T> expect(
          Class<E> eventType, Port<? extends PortType> listenPort, Future<E, R> future) {
    checkNotNull(eventType, listenPort, future);
    checkValidPort(listenPort, Direction.OUT);
    ctrl.addExpectWithFuture(eventType, listenPort, future);
    return this;
  }

  public <E extends KompicsEvent, R extends KompicsEvent, P extends PortType> TestContext<T> trigger(
          Port<P> responsePort, Future<E, R> future) {
    checkNotNull(responsePort, future);
    ctrl.trigger(responsePort, future);
    return this;
  }

  public TestContext<T> either() {
    ctrl.either();
    return this;
  }

  public TestContext<T> or() {
    ctrl.or();
    return this;
  }

  public <P extends PortType> TestContext<T> trigger(
          KompicsEvent event, Port<P> port) {
    checkNotNull(event, port);
    ctrl.trigger(event, port);
    return this;
  }

  // // TODO: 3/31/17 allow matching predicate
  public <P extends  PortType> TestContext<T> disallow(
            KompicsEvent event, Port<P> port, Direction direction) {
    checkNotNull(event, port, direction);
    checkValidPort(port, direction);
    ctrl.addDisallowedEvent(event, port, direction);
    return this;
  }

  public <P extends  PortType> TestContext<T> allow(
            KompicsEvent event, Port<P> port, Direction direction) {
    checkNotNull(event, port, direction);
    checkValidPort(port, direction);
    ctrl.addAllowedEvent(event, port, direction);
    return this;
  }

  public <P extends  PortType> TestContext<T> drop(
            KompicsEvent event, Port<P> port, Direction direction) {
    checkNotNull(event, port, direction);
    checkValidPort(port, direction);
    ctrl.addDroppedEvent(event, port, direction);
    return this;
  }

  public <E extends KompicsEvent> TestContext<T> setComparator(
          Class<E> eventType, Comparator<E> comparator) {
    checkNotNull(eventType, comparator);
    ctrl.addComparator(eventType, comparator);
    return this;
  }

  public <E extends KompicsEvent> TestContext<T> setDefaultAction(
          Class<E> eventType, Function<E, Action> function) {
    checkNotNull(eventType, function);
    ctrl.setDefaultAction(eventType, function);
    return this;
  }

  public Component getComponentUnderTest() {
    return cut.getComponentCore();
  }

  public TestContext<T> inspect(
          Predicate<T> assertPred) {
    checkNotNull(assertPred);
    ctrl.inspect(assertPred);
    return this;
  }

  public TestContext<T> expectFault(
          Class<? extends Throwable> exceptionType) {
    checkNotNull(exceptionType);
    ctrl.expectFault(exceptionType);
    return this;
  }

  public TestContext<T> expectFault(
          Predicate<Throwable> exceptionPredicate) {
    checkNotNull(exceptionPredicate);
    ctrl.expectFault(exceptionPredicate);
    return this;
  }

  public TestContext<T> setTimeout(long timeoutMS) {
    proxy.eventQueue.setTimeout(timeoutMS);
    return this;
  }

  public boolean check() {
    if (checked) {
      throw new IllegalStateException("test has previously been run");
    } else {
      checked = true;
      boolean success = ctrl.start();
      scheduler.shutdown();
      return success;
    }
  }

  // PRIVATE
  private void init() {
    // default scheduler
    scheduler = new ThreadPoolScheduler(1);
    Kompics.setScheduler(scheduler);

    // // TODO: 2/20/17 set worker id
    proxyComponent.getControl().doTrigger(Start.event, 0, proxyComponent);
    assert proxyComponent.state() == Component.State.ACTIVE;
  }

  private void initFSM() {
    ctrl = proxy.getFsm();
    ctrl.addParticipant(cut.getComponentCore());
  }


  private <P extends  PortType> void checkValidPort(
          Port<P> port, Direction direction) {
    if (port.getPair().getOwner() != cut.getComponentCore()) {
      throw new UnsupportedOperationException("Watching messages are allowed only on the tested component's ports");
    }

    if (direction == Direction.IN && !proxy.isConnectedPort(port)) {
      throw new IllegalStateException(String.format("Cannot watch incoming message on an unconnected port %s. Check that this port has been connected" , port));
    }
  }
  
  static void checkNotNull(Object... objects) {
    for (Object o : objects) {
      Preconditions.checkNotNull(o);
    }
  }
}
