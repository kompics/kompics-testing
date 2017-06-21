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
   *  Creates a new {@link TestContext} for the specified {@link ComponentDefinition}.
   *  The init is used to instantiate <code>componentDefinition</code>.
   * @param componentDefinition  {@link ComponentDefinition} class
   * @param init        {@link Init} to instantiate componentDefinition
   * @param <T>         <code>componentDefinition</code> type
   * @return a new {@link TestContext} for the specified {@link ComponentDefinition}.
   */
  public static <T extends ComponentDefinition> TestContext<T> newInstance(
      Class<T> componentDefinition, Init<T> init) {
    checkNotNull(componentDefinition, init);
    return new TestContext<T>(componentDefinition, init);
  }

  /**
   * Equivalent to newInstance(componentDefinition, Init.NONE).
   */
  public static <T extends ComponentDefinition> TestContext<T> newInstance(
      Class<T> componentDefinition) {
    return newInstance(componentDefinition, Init.NONE);
  }

  /**
   *  Creates a new {@link TestContext} for the specified {@link ComponentDefinition}.
   *  The init is used to instantiate <code>componentDefinition</code>.
   * @param componentDefinition  {@link ComponentDefinition} class
   * @param init        {@link se.sics.kompics.Init.None} to instantiate componentDefinition
   * @param <T>         <code>componentDefinition</code> type
   * @return a new {@link TestContext} for the specified {@link ComponentDefinition}.
   */
  public static <T extends ComponentDefinition> TestContext<T> newInstance(
      Class<T> componentDefinition, Init.None init) {
    checkNotNull(componentDefinition, init);
    return new TestContext<T>(componentDefinition, init);
  }

  // create

  /**
   * Creates a new component as a dependency.
   *  <p>Allowed modes - first (initial) {@link MODE#HEADER HEADER}.</p>
   * @param componentDefinition {@link ComponentDefinition} class of dependency.
   * @param init                {@link Init} to instantiate componentDefinition
   * @param <T>                 {@link ComponentDefinition} type
   * @return created {@link Component}
   */
  public <T extends ComponentDefinition> Component create(
          Class<T> componentDefinition, Init<T> init) {
    checkNotNull(componentDefinition, init);
    Component c = proxy.createSetupComponent(componentDefinition, init);
    ctrl.addParticipant(c);
    return c;
  }

  /**
   * Equivalent to create(componentDefinition, Init.NONE)
   */
  public <T extends ComponentDefinition> Component create(Class<T> componentDefinition) {
    return create(componentDefinition, Init.NONE);
  }

  /**
   * Creates a new component as a dependency.
   *  <p>Allowed modes - first (initial) {@link MODE#HEADER HEADER}.</p>
   * @param componentDefinition {@link ComponentDefinition} class of dependency.
   * @param init                {@link Init} to instantiate componentDefinition
   * @param <T>                 {@link ComponentDefinition} type
   * @return created {@link Component}
   */
  public <T extends ComponentDefinition> Component create(
          Class<T> componentDefinition, Init.None init) {
    checkNotNull(componentDefinition, init);
    Component c = proxy.createSetupComponent(componentDefinition, init);
    ctrl.addParticipant(c);
    return c;
  }

  // connect

  /**
   * Equivalent to connect(negative, positive, {@link Channel#TWO_WAY}).
   */
  public <P extends PortType> TestContext<T> connect(
          Negative<P> negative, Positive<P> positive) {
    return connect(positive, negative);
  }

  /**
   * Equivalent to connect(positive, negative, {@link Channel#TWO_WAY}).
   */
  public <P extends PortType> TestContext<T> connect(
          Positive<P> positive, Negative<P> negative) {
    return connect(positive, negative, Channel.TWO_WAY);
  }

  /**
   * Equivalent to connect(positive, negative, factory).
   */
  public <P extends PortType> TestContext<T> connect(
          Negative<P> negative, Positive<P> positive, ChannelFactory factory) {
    return connect(positive, negative, factory);
  }

  /**
   * Connects two ports with the specified {@link ChannelFactory}.
   *  <p>Allowed modes - first (initial) {@link MODE#HEADER HEADER}.</p>
   * @param negative  {@link Negative} port.
   * @param positive  {@link Positive} port.
   * @param factory   {@link ChannelFactory} to connect both ports.
   * @param <P>       {@link PortType}.
   * @return          current {@link TestContext}.
   */
  public <P extends PortType> TestContext<T> connect(
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

  /**
   * Enters {@link MODE#HEADER HEADER} mode and begins creating a block.
   * The block matches a sequence the specified number of times.
   *  <p>Allowed modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
   * @param times  number of times to match a sequence.
   * @return current {@link TestContext}.
   */
  public TestContext<T> repeat(int times) {
    ctrl.repeat(times);
    return this;
  }

  /**
   * Enters {@link MODE#HEADER HEADER} mode and begins creating a block.
   * The block matches a sequence zero or more times.
   * <p>Allowed modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
   * @return current {@link TestContext}.
   */
  public TestContext<T> repeat() {
    ctrl.repeat();
    return this;
  }

  /**
   * Enters {@link MODE#HEADER HEADER} mode and begins creating a block.
   * The block matches a sequence the specified number of times.
   * The blockInit is executed at the beginning of every iteration of the block - e.g If times = 5, then blockInit is run 5 times.
   * <p>Allowed modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
   * @param times number of times to match a sequence.
   * @param blockInit {@link BlockInit} implementation to be called at the beginning of each iteration of the created block.
   * @return current {@link TestContext}.
   */
  public TestContext<T> repeat(int times, BlockInit blockInit) {
    checkNotNull(blockInit);
    ctrl.repeat(times, blockInit);
    return this;
  }

  /**
   * Enters {@link MODE#HEADER HEADER} mode and begins creating a block.
   * The block matches a sequence zero or more times.
   * The blockInit is executed at the beginning of every iteration of the block -
   * <p>Allowed modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
   * e.g If the block matches 5 sequences, then blockInit is run 5 times.
   * @param blockInit {@link BlockInit} implementation to be called at the beginning of each iteration of the created block.
   * @return current {@link TestContext}.
   */
  public TestContext<T> repeat(BlockInit blockInit) {
    checkNotNull(blockInit);
    ctrl.repeat(blockInit);
    return this;
  }

  // body

  /**
   * Begins the body of a block.
   * <p>Allowed modes - {@link MODE#HEADER HEADER}.</p>
   * @return current {@link TestContext}.
   */
  public TestContext<T> body() {
    ctrl.body();
    return this;
  }

  // end

  /**
   * Marks the end of a a {@link #repeat()}, {@link #either()}, {@link #unordered()}, {@link #answerRequests()}.
   * Exits the current mode.
   * <p>Allowed modes - {@link MODE#BODY BODY}, {@link MODE#UNORDERED UNORDERED},
   * {@link MODE#ANSWER_REQUEST ANSWER_REQUEST}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
   * @return current {@link TestContext}.
   */
  public TestContext<T> end() {
    ctrl.end();
    return this;
  }

  // expect

  /**
   * Matches the occurrence of a single event going in or out of the component under test.
   * Event equivalence is determined using a comparator provided via method {@link #setComparator(Class, Comparator)},
   * otherwise it defaults to the {@link Object#equals(Object)} method of event.
   * <p>Allowed modes - {@link MODE#BODY BODY}, {@link MODE#UNORDERED UNORDERED}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
   * @param event       Event message to be matched.
   * @param port        Port on which event should occur.
   * @param direction   Direction IN or OUT of expected event.
   * @param <P>         Port type
   * @return            current {@link TestContext}
   */
  public <P extends  PortType> TestContext<T> expect(
          KompicsEvent event, Port<P> port, Direction direction) {
    checkNotNull(event, port, direction);
    checkValidPort(event.getClass(), port, direction);
    ctrl.expect(ctrl.newEventSpec(event, port, direction));
    return this;
  }

  /**
   * Matches the occurrence of a single event going in or out of the component under test.
   * The specified predicate is used to determine whether or not the expected message matches and
   * should return true only in that case.
   * <p>Allowed modes - {@link MODE#BODY BODY}, {@link MODE#UNORDERED UNORDERED}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
   * @param eventType   Class of expected event.
   * @param predicate   predicate that determines a matched event message.
   * @param port        port on which event should occur.
   * @param direction   Direction IN or OUT of expected event.
   * @param <P>         port type
   * @param <E>         Event type
   * @return            current {@link TestContext}
   */
  public <P extends  PortType, E extends KompicsEvent> TestContext<T> expect(
          Class<E> eventType, Predicate<E> predicate, Port<P> port, Direction direction) {
    checkNotNull(eventType, port, predicate, direction);
    checkValidPort(eventType, port, direction);
    ctrl.expect(newPredicateSpec(eventType, predicate, port, direction));
    return this;
  }

  /**
   * Matches the occurrence of a single event of the specified class, going in or out of the component under test.
   * <p>Allowed modes - {@link MODE#BODY BODY}, {@link MODE#UNORDERED UNORDERED}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
   * @param eventType   Class of expected event.
   * @param port        port on which event should occur.
   * @param direction   Direction IN or OUT of expected event.
   * @param <P>         port type.
   * @param <E>         event type.
   * @return            current {@link TestContext}.
   */
  public <P extends  PortType, E extends KompicsEvent> TestContext<T> expect(
      Class<E> eventType, Port<P> port, Direction direction) {
    checkNotNull(eventType, port, direction);
    checkValidPort(eventType, port, direction);
    ctrl.expect(newPredicateSpec(eventType, port, direction));
    return this;
  }

  // trigger

  /**
   * Triggers an event on the specified port.
   * <p>Allowed modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
   * @param event    event to be triggered.
   * @param port     port on which to trigger event.
   * @param <P>      port type.
   * @return         current {@link TestContext}.
   */
  public <P extends PortType> TestContext<T> trigger(
      KompicsEvent event, Port<P> port) {
    checkNotNull(event, port);
    ctrl.trigger(event, port);
    return this;
  }

  /**
   * Triggers an event provided by future, as a response on specified port.
   * The future must have been set in a previous call to {@link #answerRequest(Class, Port, Future)}.
   * The {@link Future#get()} method is called to retrieve the triggered event.
   * <p>Allowed modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
   * @param responsePort   port on which event is triggered.
   * @param future         future providing event to be triggered.
   * @param <RQ>           request event type.
   * @param <RS>           response event type.
   * @param <P>            port type.
   * @return               current {@link TestContext}.
   */
  public <RQ extends KompicsEvent, RS extends KompicsEvent, P extends PortType> TestContext<T> trigger(
      Future<RQ, RS> future, Port<P> responsePort) {
    checkNotNull(responsePort, future);
    ctrl.trigger(responsePort, future);
    return this;
  }

  // unordered

  /**
   * Equivalent to unordered(true).
   * @return current {@link TestContext}.
   */
  public TestContext<T> unordered() {
    ctrl.setUnorderedMode();
    return this;
  }

  /**
   * Enters {@link MODE#UNORDERED}.
   * Allowed method calls in this mode are:
   * <li>{@link #expect(KompicsEvent, Port, Direction)}</li>
   * <li>{@link #expect(Class, Port, Direction)}</li>
   * <li>{@link #expect(Class, Predicate, Port, Direction)}</li>
   * <li>{@link #answerRequest(Class, Port, Future)}</li>
   * <li>{@link #answerRequest(Class, Port, Function, Port)}</li>
   * <p>Expected events here are matched in the order that they occur at runtime.
   * When answering requests in this mode, the immediateResponse toggles whether or not
   * to trigger response events on provided port once a request is matched.</p>
   * <p>Allowed modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
   * @param immediateResponse - where applicable, respond to requests immediately(true) or when all events have been received(false).
   * @return   current {@link TestContext}.
   */
  public TestContext<T> unordered(boolean immediateResponse) {
    ctrl.setUnorderedMode(immediateResponse);
    return this;
  }

  // blockExpect

  /**
   * Match the occurrence of the specified event at any position within the sequence of the current block.
   * If the specified event does not occur after executing the last statement of the block, the test case fails.
   * <p>Allowed modes - {@link MODE#HEADER HEADER}.</p>
   * @param event       expected event to match
   * @param port        port on which event should occur.
   * @param direction   Direction IN or OUT.
   * @param <P>         port type.
   * @return            current {@link TestContext}
   */
  public <P extends  PortType> TestContext<T> blockExpect(
          KompicsEvent event, Port<P> port, Direction direction) {
    checkNotNull(event, port, direction);
    checkValidPort(event.getClass(), port, direction);
    ctrl.blockExpect(ctrl.newEventSpec(event, port, direction));
    return this;
  }

  /**
   *  Similar to {@link #blockExpect(KompicsEvent, Port, Direction)}.
   *  Instead it matches the expected event with the specified predicate.
   */
  public <P extends  PortType, E extends KompicsEvent> TestContext<T> blockExpect(
          Class<E> eventType, Predicate<E> pred, Port<P> port, Direction direction) {
    checkNotNull(eventType, pred, port, direction);
    checkValidPort(eventType, port, direction);
    ctrl.blockExpect(newPredicateSpec(eventType, pred, port, direction));
    return this;
  }

  /**
   *  Similar to {@link #blockExpect(KompicsEvent, Port, Direction)}.
   *  Instead an event of the specified class is matched.
   */
  public <P extends  PortType, E extends KompicsEvent> TestContext<T> blockExpect(
      Class<E> eventType, Port<P> port, Direction direction) {
    checkNotNull(eventType, port, direction);
    checkValidPort(eventType, port, direction);
    ctrl.blockExpect(newPredicateSpec(eventType, port, direction));
    return this;
  }

  // answer request

  /**
   * Enters {@link MODE#ANSWER_REQUEST ANSWER_REQUEST} mode.
   * In this mode, only method {@link #answerRequest(Class, Port, Function, Port)} can be used.
   * Responses are triggered in their received order, only when all requests have been received.
   * <p>Allowed modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
   * @return current {@link TestContext}.
   */
  public TestContext<T> answerRequests() {
    ctrl.answerRequests();
    return this;
  }

  /**
   * Tries to match an outgoing message on the specified requestPort as a request message by calling the provided
   * mapper function with it as an argument.
   * If mapper returns null, then the match fails otherwise the returned message is treated as a response and triggered
   * on responsePort.
   * <p>Allowed modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}, {@link MODE#ANSWER_REQUEST ANSWER_REQUEST}.</p>
   * <p>If used in {@link MODE#ANSWER_REQUEST [ANSWER_REQUEST]} mode then the
   * responses are triggered only when all requests have been matched. Otherwise responses are triggered immediately.</p>
   * @param requestType    events of this class are treated as requests.
   * @param requestPort    port on which to expect request events.
   * @param mapper         mapper function from a matched request message to a response message otherwise null.
   * @param responsePort   port on which to trigger a response message.
   * @param <RQ>           request type.
   * @param <RS>           response type.
   * @return               current {@link TestContext}.
   */
  public <RQ extends KompicsEvent, RS extends KompicsEvent> TestContext<T> answerRequest(
      Class<RQ> requestType, Port<? extends PortType> requestPort,
      Function<RQ, RS> mapper, Port<? extends PortType> responsePort) {
    checkNotNull(requestType, requestPort, mapper, responsePort);
    checkValidPort(requestType, requestPort, Direction.OUT);
    ctrl.answerRequest(requestType, requestPort, mapper, responsePort);
    return this;
  }

  /**
   * Tries to match an outgoing message M on the specified requestPort as a request message by calling the provided
   * {@link Future#set(KompicsEvent)} method of future with M as an argument.
   * If future returns true, then the match succeeded and a response should have been generated and set by the future.
   * This response can later be used in a call to {@link #trigger(Future, Port)}.
   * <p>Allowed modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}, {@link MODE#ANSWER_REQUEST ANSWER_REQUEST}.</p>
   * @param requestType    events of this class are treated as requests.
   * @param requestPort    port on which to expect request events.
   * @param future         future to match a request and set a response.
   * @param <RQ>           request type.
   * @param <RS>           response type.
   * @return               current {@link TestContext}.
   */
  public <RQ extends KompicsEvent, RS extends KompicsEvent> TestContext<T> answerRequest(
      Class<RQ> requestType, Port<? extends PortType> requestPort, Future<RQ, RS> future) {
    checkNotNull(requestType, requestPort, future);
    checkValidPort(requestType, requestPort, Direction.OUT);
    ctrl.answerRequest(requestType, requestPort, future);
    return this;
  }

  // either-or

  /**
   * Enters a new {@link MODE#CONDITIONAL CONDITIONAL} mode, creating a new conditional statement.
   * A conditional statement consists of <code>either() A or() B end()</code> of
   * which only one of sequences A or B is matched at runtime depending on the events that actually occur.
   * Conditional statements can be nested using more calls to this statement
   * e.g <code>either() A either() C or() D end() or() B end()</code> is similar to
   * the regular expression A(C|D)|B while
   * <code>either() A or either() C or() D end () B end()</code> is similar to A | (C|D)B.
   * <p>Allowed modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
   * @return  current {@link TestContext}.
   */
  public TestContext<T> either() {
    ctrl.either();
    return this;
  }

  /**
   * Begins the 'or' sequence of a {@link #either() [conditional statment]}.
   * Must be called exactly once for the current conditional statement.
   * <p>Allowed modes - {@link MODE#CONDITIONAL [CONDITIONAL]}.</p>
   * @return  current {@link TestContext}.
   */
  public TestContext<T> or() {
    ctrl.or();
    return this;
  }

  // allow disallow drop

  /**
   * Blacklists the specified event within the current repeat block.
   * This means that if an event matching the specified one occurs at any position within the block then the test case fails.
   * <p>Allowed modes - {@link MODE#HEADER HEADER}.</p>
   * @param event      event to be matched against.
   * @param port       port on which the matched event should occur.
   * @param direction  Direction IN or OUT of the event.
   * @param <P>        port type.
   * @return           current {@link TestContext}
   */
  public <P extends  PortType> TestContext<T> disallow(
            KompicsEvent event, Port<P> port, Direction direction) {
    checkNotNull(event, port, direction);
    checkValidPort(event.getClass(), port, direction);
    ctrl.blacklist(ctrl.newEventSpec(event, port, direction));
    return this;
  }

  /**
   * Similar to {@link #disallow(KompicsEvent, Port, Direction)}.
   * Matches events only if predicate returns true instead.
   */

  public <E extends KompicsEvent, P extends  PortType> TestContext<T> disallow(
      Class<E> eventType, Predicate<E> predicate, Port<P> port, Direction direction) {
    checkNotNull(eventType, port, direction);
    checkValidPort(eventType, port, direction);
    ctrl.blacklist(newPredicateSpec(eventType, predicate, port, direction));
    return this;
  }

  /**
   * Similar to {@link #disallow(KompicsEvent, Port, Direction)}.
   * Matches event by class instead.
   */
  public <E extends KompicsEvent, P extends  PortType> TestContext<T> disallow(
      Class<E> eventType, Port<P> port, Direction direction) {
    checkNotNull(eventType, port, direction);
    checkValidPort(eventType, port, direction);
    ctrl.blacklist(newPredicateSpec(eventType, port, direction));
    return this;
  }

  // allow
  /**
   * Whitelists the specified event within the current repeat block.
   * This means that if an event matching the specified one occurs at any position within the block then
   * that event is handled normally. However such events are not necessary for a successful testcase.
   * <p>Allowed modes - {@link MODE#HEADER HEADER}.</p>
   * @param event      event to be matched against.
   * @param port       port on which the matched event should occur.
   * @param direction  Direction IN or OUT of the event.
   * @param <P>        port type.
   * @return           current {@link TestContext}
   */
  public <P extends  PortType> TestContext<T> allow(
            KompicsEvent event, Port<P> port, Direction direction) {
    checkNotNull(event, port, direction);
    checkValidPort(event.getClass(), port, direction);
    ctrl.whitelist(ctrl.newEventSpec(event, port, direction));
    return this;
  }

  /**
   * Similar to {@link #allow(KompicsEvent, Port, Direction)}.
   * Matches events only if predicate returns true instead.
   */
  public <E extends KompicsEvent, P extends  PortType> TestContext<T> allow(
      Class<E> eventType, Predicate<E> predicate, Port<P> port, Direction direction) {
    checkNotNull(eventType, port, direction);
    checkValidPort(eventType, port, direction);
    ctrl.whitelist(newPredicateSpec(eventType, predicate, port, direction));
    return this;
  }

  /**
   * Similar to {@link #allow(KompicsEvent, Port, Direction)}.
   * Matches events by class instead.
   */
  public <E extends KompicsEvent, P extends  PortType> TestContext<T> allow(
      Class<E> eventType, Port<P> port, Direction direction) {
    checkNotNull(eventType, port, direction);
    checkValidPort(eventType, port, direction);
    ctrl.whitelist(newPredicateSpec(eventType, port, direction));
    return this;
  }

  // drop
  /**
   * Drops the specified event within the current repeat block.
   * This means that if an event matching the specified one occurs at any position within the block then
   * that message of that event is not forwarded to the recipient(s).
   * <p>Allowed modes - {@link MODE#HEADER HEADER}.</p>
   * @param event      event to be matched against.
   * @param port       port on which the matched event should occur.
   * @param direction  Direction IN or OUT of the event.
   * @param <P>        port type.
   * @return           current {@link TestContext}
   */
  public <P extends  PortType> TestContext<T> drop(
            KompicsEvent event, Port<P> port, Direction direction) {
    checkNotNull(event, port, direction);
    checkValidPort(event.getClass(), port, direction);
    ctrl.drop(ctrl.newEventSpec(event, port, direction));
    return this;
  }

  /**
   * Similar to {@link #drop(KompicsEvent, Port, Direction)}.
   * Matches events only if predicate returns true instead.
   */
  public <E extends KompicsEvent, P extends  PortType> TestContext<T> drop(
      Class<E> eventType, Predicate<E> predicate, Port<P> port, Direction direction) {
    checkNotNull(eventType, port, direction);
    checkValidPort(eventType, port, direction);
    ctrl.drop(newPredicateSpec(eventType, predicate, port, direction));
    return this;
  }

  /**
   * Similar to {@link #drop(KompicsEvent, Port, Direction)}.
   * Matches events by class instead.
   */
  public <E extends KompicsEvent, P extends  PortType> TestContext<T> drop(
      Class<E> eventType, Port<P> port, Direction direction) {
    checkNotNull(eventType, port, direction);
    checkValidPort(eventType, port, direction);
    ctrl.drop(newPredicateSpec(eventType, port, direction));
    return this;
  }

  /**
   * Sets a comparator for events of class eventType.
   * The comparator is used for determining the equivalence of two messages of the specified eventType.
   * If no comparator is provided for a class, then the {@link Object#equals(Object)} is used.
   *  <p>Allowed modes - first (initial) {@link MODE#HEADER HEADER}.</p>
   * @param eventType   class of event.
   * @param comparator  comparator for eventType.
   * @param <E>         eventType.
   * @return            current {@link TestContext}
   */
  public <E extends KompicsEvent> TestContext<T> setComparator(
          Class<E> eventType, Comparator<E> comparator) {
    checkNotNull(eventType, comparator);
    ctrl.setComparator(eventType, comparator);
    return this;
  }

  /**
   * Set policy for handling unmatched/unexpected/unspecified events.
   * If such an event M of type eventType is observed, then function is called with M as an argument.
   * If the return value of function is <code>null</code> then the test case fails.
   * Otherwise, an {@link Action} is returned determining whether to drop, whitelist or blacklist M.
   *  <p>Allowed modes - first (initial) {@link MODE#HEADER HEADER}.</p>
   * @param eventType  classes and subclasses of events to handle with function.
   * @param function   function to specify the taken action for an event.
   * @param <E>        eventType.
   * @return           current {@link TestContext}.
   */
  public <E extends KompicsEvent> TestContext<T> setDefaultAction(
          Class<E> eventType, Function<E, Action> function) {
    checkNotNull(eventType, function);
    ctrl.setDefaultAction(eventType, function);
    return this;
  }

  /**
   * Returns the created component under test.
   * <p>Allowed modes - All modes.</p>
   * @return  component under test.
   */
  public Component getComponentUnderTest() {
    return cut.getComponentCore();
  }

  // inspect

  /**
   * Specifies a predicate to be called by the framework with the {@link ComponentDefinition} as an argument.
   * If the predicate returns false, the test case fails immediately. Otherwise the test case continues.
   * <p>Allowed modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
   * @param predicate predicate to be called by framework.
   * @return current {@link TestContext}.
   */
  public TestContext<T> inspect(Predicate<T> predicate) {
    checkNotNull(predicate);
    ctrl.inspect(predicate);
    return this;
  }

  // expectFault

  /**
   * Verifies that an exception is thrown at the current position in the sequence.
   * If no exception is thrown the test case fails.
   * <p>Allowed modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
   * @param exceptionType  class (or superclass) of expected exception
   * @return current {@link TestContext}.
   */
  public TestContext<T> expectFault(
          Class<? extends Throwable> exceptionType) {
    checkNotNull(exceptionType);
    FaultSpec spec = new FaultSpec(cut.getControlPort(), exceptionType);
    ctrl.expectFault(spec);
    return this;
  }

  /**
   * Similar to {@link #expectFault(Class)}.
   * Matches thrown exception by specified predicate.
   */
  public TestContext<T> expectFault(
          Predicate<Throwable> exceptionPredicate) {
    checkNotNull(exceptionPredicate);
    FaultSpec spec = new FaultSpec(cut.getControlPort(), exceptionPredicate);
    ctrl.expectFault(spec);
    return this;
  }

  /**
   * Specify timeout value waiting for an event to occur.
   *  <p>Allowed modes - first (initial) {@link MODE#HEADER HEADER}.</p>
   * @param timeoutMS  timeout in milliseconds
   * @return current {@link TestContext}.
   */
  public TestContext<T> setTimeout(long timeoutMS) {
    ctrl.setTimeout(timeoutMS);
    return this;
  }

  /**
   * Runs the specified testcase
   * @return true if observed event sequence conforms to testcase otherwise false.
   *  <p>Allowed modes - {@link MODE#BODY BODY}.</p>
   */
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
