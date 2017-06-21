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


/**
 * @author Ifeanyi Ubah
 * @param <T> the ComponentDefinition to test
 */
public class TestContext<T extends ComponentDefinition> {
  private final Proxy<T> proxy;
  private final ComponentCore proxyComponent;
  private T cut;
  private CTRL<T> ctrl;
  private Scheduler scheduler;
  private boolean checked;

  static final Logger logger = LoggerFactory.getLogger("KompicsTesting");

  // constructor
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

  // factory
  /**
   *  Returns a new <code>TestContext</code> instance for the specified <code>ComponentDefinition</code>
   * @param componentDefinition  <code>ComponentDefinition</code> class to test
   * @param init        Init
   * @param <T>         <code>ComponentDefinition</code> type
   * @return a new <code>TestContext</code> instance for the specified <code>ComponentDefinition</code>
   */
  public static <T extends ComponentDefinition> TestContext<T> newInstance(
      Class<T> componentDefinition, Init<T> init) {
    checkNotNull(componentDefinition, init);
    return new TestContext<T>(componentDefinition, init);
  }

  /**
   * Returns a new <code>TestContext</code> instance for the specified <code>ComponentDefinition</code>
   * @param componentDefinition
   * @param <T>
   * @return
   */
  public static <T extends ComponentDefinition> TestContext<T> newInstance(
      Class<T> componentDefinition) {
    return newInstance(componentDefinition, Init.NONE);
  }

  public static <T extends ComponentDefinition> TestContext<T> newInstance(
      Class<T> definition, Init.None init) {
    checkNotNull(definition, init);
    return new TestContext<T>(definition, init);
  }

  // create
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

  // connect
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

  // repeat
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

  // body
  public TestContext<T> body() {
    ctrl.body();
    return this;
  }

  // end
  public TestContext<T> end() {
    ctrl.end();
    return this;
  }

  // expect
  public <P extends  PortType> TestContext<T> expect(
          KompicsEvent event, Port<P> port, Direction direction) {
    checkNotNull(event, port, direction);
    checkValidPort(event.getClass(), port, direction);
    ctrl.expect(ctrl.newEventSpec(event, port, direction));
    return this;
  }

  public <P extends  PortType, E extends KompicsEvent> TestContext<T> expect(
          Class<E> eventType, Predicate<E> predicate, Port<P> port, Direction direction) {
    checkNotNull(eventType, port, predicate, direction);
    checkValidPort(eventType, port, direction);
    ctrl.expect(newPredicateSpec(eventType, predicate, port, direction));
    return this;
  }

  public <P extends  PortType, E extends KompicsEvent> TestContext<T> expect(
      Class<E> eventType, Port<P> port, Direction direction) {
    checkNotNull(eventType, port, direction);
    checkValidPort(eventType, port, direction);
    ctrl.expect(newPredicateSpec(eventType, port, direction));
    return this;
  }

  // trigger
  public <P extends PortType> TestContext<T> trigger(
      KompicsEvent event, Port<P> port) {
    checkNotNull(event, port);
    ctrl.trigger(event, port);
    return this;
  }

  public <E extends KompicsEvent, R extends KompicsEvent, P extends PortType> TestContext<T> trigger(
      Port<P> responsePort, Future<E, R> future) {
    checkNotNull(responsePort, future);
    ctrl.trigger(responsePort, future);
    return this;
  }

  // unordered
  public TestContext<T> unordered() {
    ctrl.setUnorderedMode();
    return this;
  }

  public TestContext<T> unordered(boolean immediateResponse) {
    ctrl.setUnorderedMode(immediateResponse);
    return this;
  }

  // blockExpect
  public <P extends  PortType> TestContext<T> blockExpect(
          KompicsEvent event, Port<P> port, Direction direction) {
    checkNotNull(event, port, direction);
    checkValidPort(event.getClass(), port, direction);
    ctrl.blockExpect(ctrl.newEventSpec(event, port, direction));
    return this;
  }

  public <P extends  PortType, E extends KompicsEvent> TestContext<T> blockExpect(
          Class<E> eventType, Predicate<E> pred, Port<P> port, Direction direction) {
    checkNotNull(eventType, pred, port, direction);
    checkValidPort(eventType, port, direction);
    ctrl.blockExpect(newPredicateSpec(eventType, pred, port, direction));
    return this;
  }

  public <P extends  PortType, E extends KompicsEvent> TestContext<T> blockExpect(
      Class<E> eventType, Port<P> port, Direction direction) {
    checkNotNull(eventType, port, direction);
    checkValidPort(eventType, port, direction);
    ctrl.blockExpect(newPredicateSpec(eventType, port, direction));
    return this;
  }

  // answer request

  public TestContext<T> answerRequests() {
    ctrl.answerRequests();
    return this;
  }

  public <RQ extends KompicsEvent, RS extends KompicsEvent> TestContext<T> answerRequest(
      Class<RQ> requestType, Port<? extends PortType> requestPort,
      Function<RQ, RS> mapper, Port<? extends PortType> responsePort) {
    checkNotNull(requestType, requestPort, mapper, responsePort);
    checkValidPort(requestType, requestPort, Direction.OUT);
    ctrl.answerRequest(requestType, requestPort, mapper, responsePort);
    return this;
  }

  public <RQ extends KompicsEvent, RS extends KompicsEvent> TestContext<T> answerRequest(
      Class<RQ> requestType, Port<? extends PortType> requestPort, Future<RQ, RS> future) {
    checkNotNull(requestType, requestPort, future);
    checkValidPort(requestType, requestPort, Direction.OUT);
    ctrl.answerRequest(requestType, requestPort, future);
    return this;
  }

  // either-or
  public TestContext<T> either() {
    ctrl.either();
    return this;
  }

  public TestContext<T> or() {
    ctrl.or();
    return this;
  }

  // allow disallow drop
  public <P extends  PortType> TestContext<T> disallow(
            KompicsEvent event, Port<P> port, Direction direction) {
    checkNotNull(event, port, direction);
    checkValidPort(event.getClass(), port, direction);
    ctrl.blacklist(ctrl.newEventSpec(event, port, direction));
    return this;
  }

  public <E extends KompicsEvent, P extends  PortType> TestContext<T> disallow(
      Class<E> eventType, Predicate<E> predicate, Port<P> port, Direction direction) {
    checkNotNull(eventType, port, direction);
    checkValidPort(eventType, port, direction);
    ctrl.blacklist(newPredicateSpec(eventType, predicate, port, direction));
    return this;
  }

  public <E extends KompicsEvent, P extends  PortType> TestContext<T> disallow(
      Class<E> eventType, Port<P> port, Direction direction) {
    checkNotNull(eventType, port, direction);
    checkValidPort(eventType, port, direction);
    ctrl.blacklist(newPredicateSpec(eventType, port, direction));
    return this;
  }

  // allow
  public <P extends  PortType> TestContext<T> allow(
            KompicsEvent event, Port<P> port, Direction direction) {
    checkNotNull(event, port, direction);
    checkValidPort(event.getClass(), port, direction);
    ctrl.whitelist(ctrl.newEventSpec(event, port, direction));
    return this;
  }

  public <E extends KompicsEvent, P extends  PortType> TestContext<T> allow(
      Class<E> eventType, Predicate<E> predicate, Port<P> port, Direction direction) {
    checkNotNull(eventType, port, direction);
    checkValidPort(eventType, port, direction);
    ctrl.whitelist(newPredicateSpec(eventType, predicate, port, direction));
    return this;
  }

  public <E extends KompicsEvent, P extends  PortType> TestContext<T> allow(
      Class<E> eventType, Port<P> port, Direction direction) {
    checkNotNull(eventType, port, direction);
    checkValidPort(eventType, port, direction);
    ctrl.whitelist(newPredicateSpec(eventType, port, direction));
    return this;
  }

  // drop
  public <P extends  PortType> TestContext<T> drop(
            KompicsEvent event, Port<P> port, Direction direction) {
    checkNotNull(event, port, direction);
    checkValidPort(event.getClass(), port, direction);
    ctrl.drop(ctrl.newEventSpec(event, port, direction));
    return this;
  }

  public <E extends KompicsEvent, P extends  PortType> TestContext<T> drop(
      Class<E> eventType, Predicate<E> predicate, Port<P> port, Direction direction) {
    checkNotNull(eventType, port, direction);
    checkValidPort(eventType, port, direction);
    ctrl.drop(newPredicateSpec(eventType, predicate, port, direction));
    return this;
  }

  public <E extends KompicsEvent, P extends  PortType> TestContext<T> drop(
      Class<E> eventType, Port<P> port, Direction direction) {
    checkNotNull(eventType, port, direction);
    checkValidPort(eventType, port, direction);
    ctrl.drop(newPredicateSpec(eventType, port, direction));
    return this;
  }

  public <E extends KompicsEvent> TestContext<T> setComparator(
          Class<E> eventType, Comparator<E> comparator) {
    checkNotNull(eventType, comparator);
    ctrl.setComparator(eventType, comparator);
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

  // inspect
  public TestContext<T> inspect(Predicate<T> predicate) {
    checkNotNull(predicate);
    ctrl.inspect(predicate);
    return this;
  }

  // expectFault
  public TestContext<T> expectFault(
          Class<? extends Throwable> exceptionType) {
    checkNotNull(exceptionType);
    FaultSpec spec = new FaultSpec(cut.getControlPort(), exceptionType);
    ctrl.expectFault(spec);
    return this;
  }

  public TestContext<T> expectFault(
          Predicate<Throwable> exceptionPredicate) {
    checkNotNull(exceptionPredicate);
    FaultSpec spec = new FaultSpec(cut.getControlPort(), exceptionPredicate);
    ctrl.expectFault(spec);
    return this;
  }

  public TestContext<T> setTimeout(long timeoutMS) {
    ctrl.setTimeout(timeoutMS);
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

    proxyComponent.getControl().doTrigger(Start.event, 0, proxyComponent);
    assert proxyComponent.state() == Component.State.ACTIVE;
  }

  private void initFSM() {
    ctrl = proxy.getFsm();
    ctrl.addParticipant(cut.getComponentCore());
  }


  private <P extends  PortType> void checkValidPort(
          Class<? extends KompicsEvent> eventType, Port<P> port, Direction direction) {
    if (port.getPair().getOwner() != cut.getComponentCore()) {
      throw new UnsupportedOperationException("Expecting messages are allowed only on the testing component's ports");
    }

    if (direction == Direction.IN && !proxy.portConfig.isConnectedPort(port)) {
      throw new IllegalStateException(String.format("Cannot expect incoming message on an unconnected port %s. Check that this port has been connected" , port));
    }

    if (!proxy.portConfig.portDeclaresEvent(eventType, port, direction)) {
      throw new IllegalArgumentException(String.format("Specified port does not declare %s events as %s",
          eventType.getSimpleName(), direction == Direction.IN? "incoming" : "outgoing"));
    }
  }
  
  private static void checkNotNull(Object... objects) {
    for (Object o : objects) {
      Preconditions.checkNotNull(o);
    }
  }

  private static <E extends KompicsEvent> PredicateSpec newPredicateSpec(
      Class<E> eventType, Port<? extends PortType> port, Direction direction) {
    Predicate<E> predicate = new Predicate<E>() {
      @Override
      public boolean apply(E e) {
        return true;
      }
    };
    return newPredicateSpec(eventType, predicate, port, direction);
  }

  private static <E extends KompicsEvent> PredicateSpec newPredicateSpec(
      Class<E> eventType, Predicate<E> predicate, Port<? extends PortType> port, Direction direction) {
    return new PredicateSpec(eventType, predicate, port, direction);
  }
}
