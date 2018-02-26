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
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;

import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.ExecutionException;

import com.google.common.base.Supplier;
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
import se.sics.kompics.Tracer;
import se.sics.kompics.Unsafe;
import se.sics.kompics.scheduler.ThreadPoolScheduler;
import se.sics.kompics.testing.scheduler.CallingThreadScheduler;

import static se.sics.kompics.testing.Direction.IN;

/**
 * @author Ifeanyi Ubah
 * @param <T> the ComponentDefinition to test
 */
public class TestContext<T extends ComponentDefinition> {

    // LOG.
    static final Logger logger = LoggerFactory.getLogger("KompicsTesting");

    // The Proxy component for this test case.
    private Proxy<T> proxy;

    // The component under test.
    private T cut;

    // Validate a test specification and create NFA.
    private Ctrl<T> ctrl = new Ctrl<T>();

    // The Components' scheduler for handling events.
    private Scheduler scheduler;

    /**
     * Timeout value in milliseconds - Default 400ms
     */
    public static final long timeout = 400;

    // Create a new test case for the component with the provided
    // definition and initialize it with initEvent.
    @SuppressWarnings("unchecked")
    private TestContext(Init<? extends ComponentDefinition> initEvent,
                        Class<T> definition) {
        // Initialize proxy and test case scheduler.
        proxy = new Proxy<T>(ctrl);

        // Use CallingThreadScheduler for proxy and the default
        // scheduler for all other components.
        ComponentCore proxyComponent = proxy.getComponentCore();
        proxyComponent.setScheduler(new CallingThreadScheduler());
        scheduler = new ThreadPoolScheduler(1);
        Kompics.setScheduler(scheduler);

        // Create CUT with Init and tracer.
        Tracer t = new Tracer() {
            @Override
            public boolean triggeredOutgoing(KompicsEvent event, PortCore<?> port) {
                return ctrl.doTransition(event, port, Direction.OUT);
            }

            @Override
            public boolean triggeredIncoming(KompicsEvent event, PortCore<?> port) {
                return ctrl.doTransition(event, port, IN);
            }
        };

        if (initEvent == Init.NONE) {
            cut = proxy.createComponentUnderTest(definition, (Init.None) initEvent, t);
        } else {
            cut = proxy.createComponentUnderTest(definition, (Init<T>) initEvent, t);
        }

        ctrl.setProxyComponent(proxyComponent);
        ctrl.setDefinitionUnderTest(cut);
    }

    // Create a new testcase with the CUT's init set to initEvent.
    private TestContext(Class<T> definition, Init<T> initEvent) {
        this(initEvent, definition);
    }

    // Create a new testcase with the CUT's init set to initEvent.
    private TestContext(Class<T> definition, Init.None initEvent) {
        this(initEvent, definition);
    }

    // Factory methods.
    /**
     *  Creates a new {@link TestContext} for the specified {@link ComponentDefinition}.
     *  The init is used to instantiate <code>componentDefinition</code>.
     * @param componentDefinition  {@link ComponentDefinition} class
     * @param init        {@link Init} to instantiate componentDefinition
     * @param <T>         <code>componentDefinition</code> type
     * @return a new {@link TestContext} for the specified {@link ComponentDefinition}.
     */
    public static <T extends ComponentDefinition>
    TestContext<T> newInstance(Class<T> componentDefinition, Init<T> init) {
        checkNotNull(componentDefinition, init);
        return new TestContext<T>(componentDefinition, init);
    }

    /**
     * Equivalent to newInstance(componentDefinition, Init.NONE).
     */
    public static <T extends ComponentDefinition>
    TestContext<T> newInstance(Class<T> componentDefinition) {
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
    public static <T extends ComponentDefinition>
    TestContext<T> newInstance(Class<T> componentDefinition, Init.None init) {
        checkNotNull(componentDefinition, init);
        return new TestContext<T>(componentDefinition, init);
    }

    // Create components.

    /**
     * Create a new component as a dependency.
     *  <p>Modes - setup {@link MODE#HEADER HEADER}.</p>
     * @param componentDefinition {@link ComponentDefinition} class of dependency.
     * @param init                {@link Init} to instantiate componentDefinition
     * @param <T>                 {@link ComponentDefinition} type
     * @return created {@link Component}
     */
    public <T extends ComponentDefinition>
    Component create(Class<T> componentDefinition, Init<T> init) {
        checkNotNull(componentDefinition, init);
        Component c = proxy.createDependency(componentDefinition, init);

        // Check that we are in the initial header.
        ctrl.checkInInitialHeader();
        return c;
    }

    /**
     * Equivalent to create(componentDefinition, Init.NONE)
     */
    public <T extends ComponentDefinition>
    Component create(Class<T> componentDefinition) {
        return create(componentDefinition, Init.NONE);
    }

    /**
     * Create a new component as a dependency.
     * <p>Modes - setup {@link MODE#HEADER HEADER}.</p>
     * @param componentDefinition {@link ComponentDefinition} class of dependency.
     * @param init                {@link Init} to instantiate componentDefinition
     * @param <T>                 {@link ComponentDefinition} type
     * @return created {@link Component}
     */
    public <T extends ComponentDefinition>
    Component create(Class<T> componentDefinition,
                     Init.None init) {
        checkNotNull(componentDefinition, init);
        Component c = proxy.createDependency(componentDefinition, init);

        // Check that we are in the initial header.
        ctrl.checkInInitialHeader();
        return c;
    }

  // connect

    /**
     * Equivalent to connect(negative, positive, {@link Channel#TWO_WAY}).
     */
    public <P extends PortType>
    TestContext<T> connect(Negative<P> negative, Positive<P> positive) {
        return connect(positive, negative);
    }

    /**
     * Equivalent to connect(positive, negative, {@link Channel#TWO_WAY}).
     */
    public <P extends PortType>
    TestContext<T> connect(Positive<P> positive,
                           Negative<P> negative) {
        return connect(positive, negative, Channel.TWO_WAY);
    }

    /**
     * Equivalent to connect(positive, negative, factory).
     */
    public <P extends PortType>
    TestContext<T> connect(Negative<P> negative,
                           Positive<P> positive,
                           ChannelFactory factory) {
        return connect(positive, negative, factory);
    }

    /**
     * Connects two ports with the specified {@link ChannelFactory}.
     * <p>Modes - setup {@link MODE#HEADER HEADER}.</p>
     * @param negative  {@link Negative} port.
     * @param positive  {@link Positive} port.
     * @param factory   {@link ChannelFactory} to connect ports.
     * @param <P>       {@link PortType}.
     * @return          current {@link TestContext}.
     */
    public <P extends PortType>
    TestContext<T> connect(Positive<P> positive,
                           Negative<P> negative,
                           ChannelFactory factory) {

        checkNotNull(positive, negative, factory);

        // Verify allowed mode for this statement.
        ctrl.checkInInitialHeader();

        factory.connect((PortCore<P>) positive, (PortCore<P>) negative);

        return this;
    }

    // Blocks.

    /**
     * Enters {@link MODE#HEADER HEADER} mode and begins creating a block.
     * The block matches a sequence the specified number of times.
     * <p>Modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
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
     * <p>Modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
     * @return current {@link TestContext}.
     */
    public TestContext<T> repeat() {
        ctrl.repeat();
        return this;
    }

    /**
     * Enters {@link MODE#HEADER HEADER} mode and begins creating a block.
     * The block matches a sequence the specified number of times.
     * The entryFunction is executed at the beginning of every iteration of the block.
     * <p>Modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
     * @param times number of times to match a sequence.
     * @param entryFunction {@link EntryFunction} implementation to be called at the beginning of each iteration of the created block.
     * @return current {@link TestContext}.
     */
    public TestContext<T> repeat(int times, EntryFunction entryFunction) {
        checkNotNull(entryFunction);
        ctrl.repeat(times, entryFunction);
        return this;
    }

    /**
     * Enters {@link MODE#HEADER HEADER} mode and begins creating a block.
     * The block matches a sequence zero or more times.
     * The entryFunction is executed at the beginning of every iteration of the block -
     * <p>Allowed modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
     * @param entryFunction {@link EntryFunction} instance
     * @return current {@link TestContext}.
     */
    public TestContext<T> repeat(EntryFunction entryFunction) {
        checkNotNull(entryFunction);
        ctrl.repeat(entryFunction);
        return this;
    }

    // Body

    /**
     * Begin the body of a block.
     * <p>Modes - {@link MODE#HEADER HEADER}.</p>
     * @return current {@link TestContext}.
     */
    public TestContext<T> body() {
        ctrl.body();
        return this;
    }

    // end

    /**
     * Mark the end of a a {@link #repeat()}, {@link #either()}, {@link #unordered()}, {@link #answerRequests()}.
     * Exits the current mode and restores the previous one.
     * <p>Modes - {@link MODE#BODY BODY}, {@link MODE#UNORDERED UNORDERED},
     * {@link MODE#ANSWER_REQUEST ANSWER_REQUEST}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
     * @return current {@link TestContext}.
     */
    public TestContext<T> end() {
        ctrl.end();
        return this;
    }

    // expect

    /**
     * Match the occurrence of a single event going in or out of the component under test.
     * Event equivalence is determined using a comparator provided via method {@link #setComparator(Class, Comparator)},
     * otherwise it defaults to the {@link Object#equals(Object)} method of event.
     * <p>Modes - {@link MODE#BODY BODY}, {@link MODE#UNORDERED UNORDERED}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
     * @param event       Event message to be matched.
     * @param port        Port on which event should occur.
     * @param direction   Direction IN or OUT of expected event.
     * @param <P>         Port type
     * @return            current {@link TestContext}
     */
    public <P extends  PortType> TestContext<T> expect(KompicsEvent event,
                                                       Port<P> port,
                                                       Direction direction) {
        checkNotNull(event, port, direction);

        // Verify that expect statement is valid.
        checkValidPort(event.getClass(), port, direction);

        ctrl.expect(ctrl.createEventLabel(event, port, direction));
        return this;
    }

    /**
     * Match the occurrence of a single event going in or out of the component under test.
     * The predicate returns true iff a specified event argument is expected.
     * <p>Modes - {@link MODE#BODY BODY}, {@link MODE#UNORDERED UNORDERED}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
     * @param eventType   Class of expected event.
     * @param predicate   predicate that determines a matched event message.
     * @param port        port on which event should occur.
     * @param direction   Direction IN or OUT of expected event.
     * @param <P>         port type
     * @param <E>         Event type
     * @return            current {@link TestContext}
     */
    public <P extends  PortType, E extends KompicsEvent>
    TestContext<T> expect(Class<E> eventType,
                          Predicate<E> predicate,
                          Port<P> port,
                          Direction direction) {
        checkNotNull(eventType, port, predicate, direction);

        // Verify that expect statement is valid.
        checkValidPort(eventType, port, direction);

        ctrl.expect(
            ctrl.createPredicateLabel(eventType, predicate, port, direction));

        return this;
    }

    /**
     * Match the occurrence of a single event of the specified class, going in or out of the component under test.
     * <p>Modes - {@link MODE#BODY BODY}, {@link MODE#UNORDERED UNORDERED}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
     * @param eventType   Class of expected event.
     * @param port        port on which event should occur.
     * @param direction   Direction IN or OUT of expected event.
     * @param <P>         port type.
     * @param <E>         event type.
     * @return            current {@link TestContext}.
     */
    public <P extends  PortType, E extends KompicsEvent>
    TestContext<T> expect(Class<E> eventType,
                          Port<P> port,
                          Direction direction) {
        checkNotNull(eventType, port, direction);

        // Verify that the expected event is possible.
        checkValidPort(eventType, port, direction);

        ctrl.expect(ctrl.createPredicateLabel(eventType, port, direction));
        return this;
    }

    // Trigger

    /**
     * Trigger an event on the specified port.
     * <p>Modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
     * @param event    event to be triggered.
     * @param port     port on which to trigger event.
     * @param <P>      port type.
     * @return         current {@link TestContext}.
     */
    public <P extends PortType> TestContext<T> trigger(KompicsEvent event,
                                                       Port<P> port) {
        checkNotNull(event, port);
        ctrl.trigger(event, port);
        return this;
    }

    /**
     * Trigger an event provided by the supplier's {@link com.google.common.base.Supplier#get()} method on the specified port.
     * <p>Modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
     * @param supplier supplier to be invoked.
     * @param port     port on which to trigger event.
     * @param <P>      port type.
     * @return         current {@link TestContext}.
     */
    public <P extends PortType> TestContext<T> trigger(Supplier<? extends KompicsEvent> supplier,
                                                       Port<P> port) {
        checkNotNull(supplier, port);
        ctrl.trigger(supplier, port);
        return this;
    }

    /**
     * Trigger the event provided by future as a response on specified port.
     * The future must have been {@link Future#set(KompicsEvent) set}in a previous call to {@link #answerRequest(Class, Port, Future)}.
     * The {@link Future#get()} method is called to retrieve the triggered event.
     * <p>Modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
     * @param responsePort   port on which event is triggered.
     * @param future         future providing event to be triggered.
     * @param <RQ>           request event type.
     * @param <RS>           response event type.
     * @param <P>            port type.
     * @return               current {@link TestContext}.
     */
    public <RQ extends KompicsEvent, RS extends KompicsEvent, P extends PortType>
    TestContext<T> trigger(Future<RQ, RS> future,
                           Port<P> responsePort) {
        checkNotNull(responsePort, future);
        ctrl.trigger(future, responsePort);
        return this;
    }

    // Unordered

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
     * <p>
     * Match events in the order that they occur at runtime as opposed to their sequential specified order.
     * When answering requests in this mode, setting immediateResponse to true causes
     * each response event to be triggered as soon the request is matched, otherwise it is
     * triggered when all requests have been matched.
     * The immediateResponse flag has no effect when using {@link #answerRequest(Class, Port, Future)}.
     * </p>
     * <p>Modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
     * @param immediateResponse - where applicable, respond to requests immediately(true) or when all events have been received(false).
     * @return   current {@link TestContext}.
     */
    public TestContext<T> unordered(boolean immediateResponse) {
        ctrl.setUnorderedMode(immediateResponse);
        return this;
    }

    // BlockExpect

    /**
     * Match the occurrence of the specified event at any position within the sequence of the current block.
     * If the specified event does not occur after executing the last statement of the block, the test case fails on timeout.
     * <p>Modes - {@link MODE#HEADER HEADER}.</p>
     * @param event       expected event to match
     * @param port        port on which event should occur.
     * @param direction   Direction IN or OUT.
     * @param <P>         port type.
     * @return            current {@link TestContext}
     */
    public <P extends  PortType>
    TestContext<T> blockExpect(KompicsEvent event,
                               Port<P> port,
                               Direction direction) {
        checkNotNull(event, port, direction);

        // Verify that the expected event is possible.
        checkValidPort(event.getClass(), port, direction);

        ctrl.blockExpect(ctrl.createEventLabel(event, port, direction));
        return this;
    }

    /**
     *  Similar to {@link #blockExpect(KompicsEvent, Port, Direction)}.
     *  Matches the expected event with the specified predicate.
     */
    public <P extends  PortType, E extends KompicsEvent>
    TestContext<T> blockExpect(Class<E> eventType,
                               Predicate<E> pred,
                               Port<P> port,
                               Direction direction) {
        checkNotNull(eventType, pred, port, direction);

        // Verify that the expected event is possible.
        checkValidPort(eventType, port, direction);

        ctrl.blockExpect(ctrl.createPredicateLabel(eventType,
                                                   pred,
                                                   port,
                                                   direction));
        return this;
    }

    /**
     *  Similar to {@link #blockExpect(KompicsEvent, Port, Direction)}.
     *  Event of the specified class is matched.
     */
    public <P extends  PortType, E extends KompicsEvent>
    TestContext<T> blockExpect(Class<E> eventType,
                               Port<P> port,
                               Direction direction) {
        checkNotNull(eventType, port, direction);

        // Verify that the expected event is possible.
        checkValidPort(eventType, port, direction);

        ctrl.blockExpect(ctrl.createPredicateLabel(eventType, port, direction));
        return this;
    }

    // Answer requests.

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
     * Match an outgoing message on the specified requestPort as a request message by calling the provided
     * mapper function with it as an argument.
     * If mapper returns null, then the match fails otherwise the returned message is treated as a response and triggered
     * on responsePort.
     * <p>Modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}, {@link MODE#UNORDERED UNORDERED},
     * {@link MODE#ANSWER_REQUEST ANSWER_REQUEST}.</p>
     * <p>If used in {@link MODE#ANSWER_REQUEST ANSWER_REQUEST} mode then the
     * responses are triggered only when all requests have been matched.
     * If in {@link MODE#UNORDERED UNORDERED} this can be {@link #unordered(boolean) configured}.
     * Otherwise responses are triggered immediately.</p>
     * @param requestType    events of this class are treated as requests.
     * @param requestPort    port on which to expect request events.
     * @param mapper         mapper function from a matched request message to a response message otherwise null.
     * @param responsePort   port on which to trigger a response message.
     * @param <RQ>           request type.
     * @param <RS>           response type.
     * @return               current {@link TestContext}.
     */
    public <RQ extends KompicsEvent, RS extends KompicsEvent>
    TestContext<T> answerRequest(Class<RQ> requestType,
                                 Port<? extends PortType> requestPort,
                                 Function<RQ, RS> mapper,
                                 Port<? extends PortType> responsePort) {
        checkNotNull(requestType, requestPort, mapper, responsePort);

        // Verify that the expected event is possible.
        checkValidPort(requestType, requestPort, Direction.OUT);

        ctrl.answerRequest(requestType, requestPort.getPair(), mapper, responsePort);
        return this;
    }

    /**
     * Match an outgoing message on the specified requestPort as a request message by calling the provided
     * {@link Future#set(KompicsEvent)} method of future with an observed event as an argument.
     * If future returns true, then the match succeeded and a response should have been generated such that
     * a subsequent call to {@link Future#get()} returns it instead of null.
     * This response can later be used in a call to {@link #trigger(Future, Port)}.
     * <p>Modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}, {@link MODE#UNORDERED UNORDERED},
     * {@link MODE#ANSWER_REQUEST ANSWER_REQUEST}.</p>
     * @param requestType    events of this class are treated as requests.
     * @param requestPort    port on which to expect request events.
     * @param future         future to match a request and set a response.
     * @param <RQ>           request type.
     * @param <RS>           response type.
     * @return               current {@link TestContext}.
     */
    public <RQ extends KompicsEvent, RS extends KompicsEvent>
    TestContext<T> answerRequest(Class<RQ> requestType,
                                 Port<? extends PortType> requestPort,
                                 Future<RQ, RS> future) {
        checkNotNull(requestType, requestPort, future);

        // Verify that the expected event is possible.
        checkValidPort(requestType, requestPort, Direction.OUT);

        ctrl.answerRequest(requestType, requestPort.getPair(), future);
        return this;
    }

    // Conditionals.

    /**
     * Enters a {@link MODE#CONDITIONAL CONDITIONAL} mode, and begins creating a new conditional.
     * A conditional consists of <code>either() A or() B end()</code> of
     * which only one of sequences of statements A or B is executed at runtime depending on the observed events.
     * Conditionals can be nested.
     * e.g <code>either() A either() C or() D end() or() B end()</code> is similar to
     * the regular expression A(C|D)|B while
     * <code>either() A or either() C or() D end () B end()</code> is similar to A | (C|D)B.
     * <p>Modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
     * @return  current {@link TestContext}.
     */
    public TestContext<T> either() {
        ctrl.either();
        return this;
    }

    /**
     * Begins the 'or' sequence of a {@link #either() conditional statment}.
     * Must be called exactly once for the current conditional statement.
     * <p>Modes - {@link MODE#CONDITIONAL CONDITIONAL}.</p>
     * @return  current {@link TestContext}.
     */
    public TestContext<T> or() {
        ctrl.or();
        return this;
    }

    // Allow, Disallow, Drop.

    /**
     * Blacklist the specified event within the current block.
     * This means that if an event matching the specified one occurs at any position within the block then the test case fails.
     * <p>Modes - {@link MODE#HEADER HEADER}.</p>
     * @param event      event to be matched against.
     * @param port       port on which the matched event should occur.
     * @param direction  Direction IN or OUT of the event.
     * @param <P>        port type.
     * @return           current {@link TestContext}
     */
    public <P extends  PortType>
    TestContext<T> disallow(KompicsEvent event,
                            Port<P> port,
                            Direction direction) {
        checkNotNull(event, port, direction);

        // Verify that the expected event is possible.
        checkValidPort(event.getClass(), port, direction);

        ctrl.blacklist(ctrl.createEventLabel(event, port, direction));
        return this;
    }

    /**
     * Similar to {@link #disallow(KompicsEvent, Port, Direction)}.
     * Matches events only if predicate returns true instead.
     */
    public <E extends KompicsEvent, P extends  PortType>
    TestContext<T> disallow(Class<E> eventType,
                            Predicate<E> predicate,
                            Port<P> port,
                            Direction direction) {
        checkNotNull(eventType, port, direction);

        // Verify that the expected event is possible.
        checkValidPort(eventType, port, direction);

        ctrl.blacklist(ctrl.createPredicateLabel(eventType, predicate, port, direction));
        return this;
    }

    /**
     * Similar to {@link #disallow(KompicsEvent, Port, Direction)}.
     * Matches event by class instead.
     */
    public <E extends KompicsEvent, P extends  PortType>
    TestContext<T> disallow(Class<E> eventType,
                            Port<P> port,
                            Direction direction) {
        checkNotNull(eventType, port, direction);

        // Verify that the expected event is possible.
        checkValidPort(eventType, port, direction);

        ctrl.blacklist(ctrl.createPredicateLabel(eventType, port, direction));
        return this;
    }

    // Allow.
    /**
     * Whitelist the specified event within the current block.
     * This means that if an event matching the specified one occurs at any position within the block then
     * that event is handled normally. However such events are not necessary for a successful testcase.
     * <p>Modes - {@link MODE#HEADER HEADER}.</p>
     * @param event      event to be matched against.
     * @param port       port on which the matched event should occur.
     * @param direction  Direction IN or OUT of the event.
     * @param <P>        port type.
     * @return           current {@link TestContext}
     */
    public <P extends  PortType>
    TestContext<T> allow(KompicsEvent event,
                         Port<P> port,
                         Direction direction) {
        checkNotNull(event, port, direction);

        // Verify that the expected event is possible.
        checkValidPort(event.getClass(), port, direction);

        ctrl.whitelist(ctrl.createEventLabel(event, port, direction));
        return this;
    }

    /**
     * Similar to {@link #allow(KompicsEvent, Port, Direction)}.
     * Matches events only if predicate returns true instead.
     */
    public <E extends KompicsEvent, P extends  PortType>
    TestContext<T> allow(Class<E> eventType,
                         Predicate<E> predicate,
                         Port<P> port,
                         Direction direction) {
        checkNotNull(eventType, port, direction);

        // Verify that the expected event is possible.
        checkValidPort(eventType, port, direction);

        ctrl.whitelist(ctrl.createPredicateLabel(eventType, predicate, port, direction));
        return this;
    }

    /**
     * Similar to {@link #allow(KompicsEvent, Port, Direction)}.
     * Matches events by class instead.
     */
    public <E extends KompicsEvent, P extends  PortType>
    TestContext<T> allow(Class<E> eventType,
                         Port<P> port,
                         Direction direction) {
        checkNotNull(eventType, port, direction);

        // Verify that the expected event is possible.
        checkValidPort(eventType, port, direction);

        ctrl.whitelist(ctrl.createPredicateLabel(eventType, port, direction));
        return this;
    }

    // Drop.
    /**
     * Drops the specified event within the current block.
     * This means that if an event matching the specified one occurs at any position within the block then
     * that message of that event is not forwarded to the recipient(s).
     * <p>Modes - {@link MODE#HEADER HEADER}.</p>
     * @param event      event to be matched against.
     * @param port       port on which the matched event should occur.
     * @param direction  Direction IN or OUT of the event.
     * @param <P>        port type.
     * @return           current {@link TestContext}
     */
    public <P extends  PortType>
    TestContext<T> drop(KompicsEvent event,
                        Port<P> port,
                        Direction direction) {
        checkNotNull(event, port, direction);

        // Verify that the expected event is possible.
        checkValidPort(event.getClass(), port, direction);

        ctrl.drop(ctrl.createEventLabel(event, port, direction));
        return this;
    }

    /**
     * Similar to {@link #drop(KompicsEvent, Port, Direction)}.
     * Matches events only if predicate returns true instead.
     */
    public <E extends KompicsEvent, P extends  PortType>
    TestContext<T> drop(Class<E> eventType,
                        Predicate<E> predicate,
                        Port<P> port,
                        Direction direction) {
        checkNotNull(eventType, port, direction);

        // Verify that the expected event is possible.
        checkValidPort(eventType, port, direction);

        ctrl.drop(ctrl.createPredicateLabel(eventType, predicate, port, direction));
        return this;
    }

    /**
     * Similar to {@link #drop(KompicsEvent, Port, Direction)}.
     * Matches events by class instead.
     */
    public <E extends KompicsEvent, P extends  PortType>
    TestContext<T> drop(Class<E> eventType,
                        Port<P> port,
                        Direction direction) {
        checkNotNull(eventType, port, direction);

        // Verify that the expected event is possible.
        checkValidPort(eventType, port, direction);

        ctrl.drop(ctrl.createPredicateLabel(eventType, port, direction));
        return this;
    }

    /**
     * Set a comparator for comparing events of the specified class.
     * The comparator is used for determining the equivalence of two messages of the same class.
     * If no comparator is provided for that class, then the {@link Object#equals(Object)} of that class is used.
     * <p>Modes - setup {@link MODE#HEADER HEADER}.</p>
     * @param eventType   class of event.
     * @param comparator  comparator for eventType.
     * @param <E>         eventType.
     * @return            current {@link TestContext}
     */
    public <E extends KompicsEvent>
    TestContext<T> setComparator(Class<E> eventType,
                                 Comparator<E> comparator) {
        checkNotNull(eventType, comparator);
        ctrl.setComparator(eventType, comparator);
        return this;
    }

    /**
     * Set policy for handling unmatched/unexpected/unspecified events.
     * If such an event of class eventType is observed, then function is called with it as an argument.
     * If the return value of function is <code>null</code> then the test case fails.
     * Otherwise, an {@link Action} is returned determining whether to drop, whitelist or blacklist M.
     * <p>Modes - setup {@link MODE#HEADER HEADER}.</p>
     * @param eventType  classes (and subclasses) of events to forwardEvent with function.
     * @param function   function to specify the taken action for an event.
     * @param <E>        eventType.
     * @return           current {@link TestContext}.
     */
    public <E extends KompicsEvent>
    TestContext<T> setDefaultAction(Class<E> eventType,
                                    Function<E, Action> function) {
        checkNotNull(eventType, function);
        ctrl.setDefaultAction(eventType, function);
        return this;
    }

    /**
     * Returns the created component under test.
     * <p>Modes - All.</p>
     * @return  component under test.
     */
    public Component getComponentUnderTest() {
        return cut.getComponentCore();
    }

    // Inspect.

    /**
     * Inspect the internal state of a component.
     * predicate is called by the framework with the {@link ComponentDefinition} as an argument.
     * If the predicate returns false, the test case fails immediately.
     * <p>Modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
     * @param predicate inspect predicate
     * @return current {@link TestContext}.
     */
//    public TestContext<T> inspect(Predicate<T> predicate) {
//        checkNotNull(predicate);
//        ctrl.inspect(predicate);
//        return this;
//    }

    // ExpectFault.

    /**
     * Verifies that an exception is thrown at the current position in execution.
     * If no exception is thrown the test case fails.
     * <p>Modes - {@link MODE#BODY BODY}, {@link MODE#CONDITIONAL CONDITIONAL}.</p>
     * @param exceptionType  class (or superclass) of expected exception
     * @return current {@link TestContext}.
     */
    public TestContext<T> expectFault(Class<? extends Throwable> exceptionType) {
        checkNotNull(exceptionType);
        FaultLabel label = new FaultLabel(cut.getControlPort(), exceptionType);
        ctrl.expectFault(label);
        return this;
    }

    /**
     * Similar to {@link #expectFault(Class)}.
     * Matches thrown exception by specified predicate.
     */
    public TestContext<T> expectFault(Predicate<Throwable> exceptionPredicate) {
        checkNotNull(exceptionPredicate);
        FaultLabel label = new FaultLabel(cut.getControlPort(), exceptionPredicate);
        ctrl.expectFault(label);
        return this;
    }

    /**
     * Specify timeout value to wait for an event to occur.
     * <p>Modes - first (initial) {@link MODE#HEADER HEADER}.</p>
     * @param timeoutMS  timeout in milliseconds
     * @return current {@link TestContext}.
     */
    public TestContext<T> setTimeout(long timeoutMS) {
        ctrl.setTimeout(timeoutMS);
        return this;
    }

    /**
     * Run the specified testcase
     * @return true if observed event sequence conforms to testcase otherwise false.
     *  <p>Allowed modes - {@link MODE#BODY BODY}.</p>
     */
    public boolean check() {
        try {
            return ctrl.runFSM().get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            scheduler.shutdown();
        }
    }

    // PRIVATE
    // Verify that expected event is on the CUT's port and
    // that the port type allows the event in the specified
    // direction and if it is an incoming event, then the
    // port has been previously connected to some other port
    // (otherwise no message could be sent in the first place).
    private <P extends  PortType>
    void checkValidPort(Class<? extends KompicsEvent> eventType,
                        Port<P> port,
                        Direction direction) {
        // An expected event must be on the outside port of the CUT.
        if (!isCutPort(port)) {
            throw new UnsupportedOperationException("Expecting messages are allowed only on the testing component's ports");
        }

        // The port type must allow events of the expected type
        // in the expected direction.
        if (!portDeclaresEvent(eventType, port, direction)) {
            throw new IllegalArgumentException(String.format(
                "The specified port does not declare events of type %s in any direction%n",
                eventType.getName()
            ));
        }
    }

    private <P extends  PortType>
    boolean portDeclaresEvent(Class<? extends KompicsEvent> eventType,
                              Port<P> port,
                              Direction direction) {
        P portType = port.getPortType();
        boolean isProvidedPort =
            Unsafe.getPositivePorts(cut.getComponentCore()).keySet().contains(portType.getClass());

        // Contains the event types allowed by P's portType in the
        // specified direction.
        Collection<Class<? extends KompicsEvent>> allowedTypes;

        // Is this a provided port of the CUT?
        if (isProvidedPort) {
            // If yes, Negative events are incoming to P while
            // Positive events are outgoing from P.
            if (direction == IN) {
                allowedTypes = Unsafe.getNegativeEvents(portType);
            } else {
                allowedTypes = Unsafe.getPositiveEvents(portType);
            }
        } else {
            // Otherwise, Positive events are incoming to P while
            // Negative events are outgoing from P.
            if (direction == IN) {
                allowedTypes = Unsafe.getPositiveEvents(portType);
            } else {
                allowedTypes = Unsafe.getNegativeEvents(portType);
            }
        }

        // Return true if the specified eventType is allowed.
        for (Class<? extends KompicsEvent> type : allowedTypes) {
            if (type.isAssignableFrom(eventType)) {
                return true;
            }
        }

        return false;
    }

    private boolean isCutPort(Port<?> p) {
        return p.getPair().getOwner() == cut.getComponentCore();
    }

    // Throw an error if any provided object is null.
    private static void checkNotNull(Object... objects) {
        for (Object o : objects) {
            Preconditions.checkNotNull(o);
        }
    }
}
