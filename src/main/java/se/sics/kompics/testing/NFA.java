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
import org.slf4j.Logger;
import se.sics.kompics.KompicsEvent;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

class NFA {
    // Log.
    private Logger logger = TestContext.logger;

    // Next available ID for a new state.
    private int stateId = 1;

    // Registered default actions.
    private final List<Map.Entry<Class<? extends KompicsEvent>, Function<? extends KompicsEvent, Action>>> defaultActions =
        new ArrayList<Map.Entry<Class<? extends KompicsEvent>, Function<? extends KompicsEvent, Action>>>();

    // The Main Repeat Block within which all
    // statements in the test case are run (and blocks are nested).
    private Block mainBlock = new Block(null, 1);

    // The error state of this NFA.
    private final State errorState = new State(0, mainBlock);

    // The Transition leading to the error state.
    private final Transition ERROR_TRANSITION = new Transition(null, errorState);

    // The current states of this NFA.
    private Set<State> currentStates;

    // The FA to which we are currently adding state transitions.
    private FA currentFA;

    // The Main RepeatFA within which the entire test case is executed.
    private RepeatFA repeatMain = new RepeatFA(1, mainBlock);

    // Track nested automata while adding state transitions.
    private Stack<FA> previousFA = new Stack<FA>();

    // The blocks currently in scope.
    // That is, those blocks that have at least one state in
    // the set of currentStates.
    private HashSet<Block> activeBlocks = new HashSet<Block>();

    public NFA() {
        currentFA = repeatMain;
        previousFA.push(repeatMain);
    }

    public Block getMainBlock() {
        return mainBlock;
    }

    // Add this transition label as a child of the current FA.
    public void addLabel(Label label) {
        currentFA.createAndAddTransition(label);
    }

    // Create Repeat/Kleene block and set as the current FA.
    public void addRepeat(int count, Block block) {
        if (count == Block.STAR) {
            currentFA = new KleeneFA(block);
        } else {
            currentFA = new RepeatFA(count, block);
        }
        // Push on stack.
        previousFA.push(currentFA);
    }

    // Close the current block/conditional statement's automaton.
    public void end() {
        // Pop currentFA from the FA stack.
        FA child = previousFA.pop();
        assert child == currentFA;

        // The Main Repeat block is at the bottom of the stack
        // and is not closed until all transitions have been added.
        // So, if it was popped, then there must be mis-matched end statement.
        // is the test specification.
        if (previousFA.isEmpty()) {
            throw new IllegalStateException("no matching statement for end.");
        }

        // Close currentFA.
        currentFA.end();

        // Restore the previous, enclosing FA within which the
        // popped FA was nested.
        currentFA = previousFA.peek(); // previous
        // Add the popped FA as a child.
        currentFA.addFA(child);
    }

    // Begin a new conditional statement.
    public void either(Block block) {
        currentFA = new ConditionalFA(block);
        // Remember enclosing FA.
        previousFA.push(currentFA);
    }

    // Switch from 'either' to 'or' mode in a conditional statement.
    public void or() {
        if (!(currentFA instanceof ConditionalFA)) {
            throw new IllegalStateException("Not in conditional mode");
        }
        ((ConditionalFA) currentFA).or();
    }

    // Construct the NFA.
    public void construct() {
        if (currentStates != null) {
            throw new IllegalStateException("NFA has already been constructed");
        }

        assert repeatMain == currentFA;
        assert repeatMain == previousFA.pop();
        assert previousFA.isEmpty();

        // Close main Repeat Block
        repeatMain.end();

        // Create the special final state of the entire NFA.
        BaseFA finalFA = new BaseFA(repeatMain.block);
        finalFA.startState.isFinalState = true;

        // Add it as the last state when building the Main Repeat Block.
        repeatMain.construct(finalFA);

        // Set the initial current states.
        currentStates = new HashSet<State>(repeatMain.startState.eclosure());

        // LOGGING.
        //printNFA(repeatMain.startState, finalFA.startState);
    }

    // Register a default action for unmatched events of subtype eventType.
    // Given an unmatched event e of class C, the predicate registered for
    // the type T that is a subtype of C and closest to C in
    // the subtype hierarchy is used.
    <E extends KompicsEvent>
    void setDefaultAction(Class<E> eventType,
                          Function<E, Action> predicate) {
        defaultActions.add(new AbstractMap.SimpleImmutableEntry<Class<? extends KompicsEvent>, Function<? extends KompicsEvent, Action>>(eventType, predicate));
    }

    // Return and increment next available ID for a new state.
    private int nextId() {
        return stateId++;
    }

    // Return true if we are in the final state of the NFA.
    private boolean isInFinalState() {
        for (State state : currentStates) {
            if (state.isFinalState) {
                return true;
            }
        }
        return false;
    }

    // Return true if we are currently in the error state.
    private boolean inErrorState() {
        return currentStates.size() == 1 && currentStates.contains(errorState);
    }

    // Set the current state to the error state.
    private void gotoErrorState() {
        currentStates.clear();
        currentStates.add(errorState);
    }

    TransitionResult doTransition(EventSymbol eventSymbol) {
        logger.debug("{}: observed event [{}]",  currentStates, eventSymbol);

        while (true) {
            // Check if we are supposed to perform an internal transition before
            // trying to match event symbol.
            // If no state in our current states set C expects to match an event symbol, i.e-
            // 'all' states in C are active states, then perform the internal transitions
            // associated with these active states and move to next states.
            TransitionResult tResult = tryInternalEventTransitions();

            // Return if in error state
            if (tResult != null && tResult.inErrorState) {
                return tResult;
            }


            // Check if this symbol is matched by any state in the set of
            // current states C and set C to the set of next states for matching
            // transitions.
            tResult = tryStateTransitions(eventSymbol);

            if (tResult != null) {
                return tResult;
            }

            // We are still unable to match eventSymbol.
            // Force internal transitions by performing the internal transition
            // associated with each active state S in the set of current states C.
            // Kill the state thread for all non-active states in C.
            tResult = performInternalEventTransitions();

            // If we found any active states and successfully landed in some next state(s),
            // then try matching event symbol again.
            if (tResult != null) {
                continue;
            }

            logger.trace("No internal transition(s) found");
            // We have an event to match but are unable to
            // follow any transitions from the current states.
            // So we default to checking for any registered actions that
            // may match the event.
            logger.trace("trying default actions");
            Action action = getDefaultAction(eventSymbol);

            // No default action for event.
            if (action == null) {
                // We still are unable to match event symbol even with default actions.
                logger.error("Event {} was not matched", eventSymbol);
                gotoErrorState();
                return transitionResult(false);
            }

            // We successfully matched this event using a default action.
            switch (action) {
                case FAIL:
                    // We were requested to fail the test case,
                    // Set current state to error state.
                    gotoErrorState();
                    logger.error("Default action for observed event {} was [{}]", eventSymbol, Action.FAIL);
                    return transitionResult(false);
                case HANDLE:
                    // We were requested to forward this event.
                    return transitionResult(true);
                case DROP:
                    // We were requested to drop this event.
                    return transitionResult(false);
            }
        }
    }

    // While 'all' states S in the current states C are active-states, i.e no
    // external events are expected, perform the internal transition associated with
    // each such S and update C to the next reachable states.
    TransitionResult tryInternalEventTransitions() {
        TransitionResult tResult = null;
        while (true) {
            // if some state thread in the NFA expects an event,
            // return the last transition we took if any.
            for (State state : currentStates) {
                if (!state.canPerformInternalTransition()) {
                    return tResult;
                }
            }

            // all S in current states C are active-states.
            // Note: If C is not a singleton set, then test specification is ambiguous.

            tResult = performInternalEventTransitions();
            assert tResult != null;
        }
    }

    private TransitionResult transitionResult(boolean send) {
        return new TransitionResult(send, isInFinalState(), inErrorState());
    }

    // For each state S in the current states such that S currently can perform
    // an internal transition t, perform t and add the destination state of t
    // to the next states. Update the current states to the next states.
    // Return null if no such S exists.
    TransitionResult performInternalEventTransitions() {
        Set<State> nextStates = new HashSet<State>();
        for (State state : currentStates) {
            if (state.canPerformInternalTransition()) {
                // perform internal transition associated with this active state.
                Transition t = state.performInternalTransition();
                assert t != null;
                // add next state to reachable
                nextStates.add(t.nextState);
            }
        }
        if (nextStates.isEmpty()) {
            return null;
        } else {
            updateCurrentState(nextStates);
            return transitionResult(false);
        }
    }

    // Given an event symbol e, find all states s in the set of currentStates C such
    // that s has a set of transitions T that match e. Add each matching
    // transition's destination state to the set of nextStates N.
    // Finally, update the C to N if any matching transition was found.
    // Returns null if this event symbol was not matched.
    private TransitionResult tryStateTransitions(EventSymbol eventSymbol) {
        Set<State> nextStates = new HashSet<>();
        // Matching transitions.
        Set<Transition> transitions = new HashSet<Transition>();

        // Find all matching transitions and add their destination state to nextStates
        for (State state : currentStates) {
            Collection<Transition> t = state.getTransition(eventSymbol);
            for (Transition transition : t) {
                nextStates.add(transition.nextState);
                transitions.add(transition);
            }
        }

        // Did we find any matching transitions?
        if (nextStates.isEmpty()) {
            return null;
        }

        // If we did, kill NFA threads for unmatched transitions and
        // set our new current state to next states
        updateCurrentState(nextStates);

        // Does any of our matching transitions require the event symbol to be forwarded?
        for (Transition t : transitions) {
            if (t.forwardEvent) {
                // Mark event to be forwarded.
                return transitionResult(true);
            }
        }
        // Mark event to not be forwarded.
        return transitionResult(false);
    }

    private void updateCurrentState(Set<State> nextStates) {
        logger.trace("{}: new state is {}", currentStates, nextStates);

        // Compute active blocks (blocks that have at least
        // one state in the nextStates).
        activeBlocks.clear();

        Block block;
        for (State state : nextStates) {
            block = state.block;
            // Has this block been added previously?
            while (block != null && !activeBlocks.contains(block)) {
                // If no, add it.
                activeBlocks.add(block);

                // If a block b is active, then its
                // parent block is also active.
                // So we recursively add its parent block.
                block = block.previousBlock;
            }
        }

        // Blocks which have a state in the currentStates but not
        // in the nextStates have been discontinued (their thread
        // has been killed by the NFA).
        // Reset discontinued blocks.
        Set<Block> discontinued = new HashSet<Block>();
        for (State state : currentStates) {
            if (!nextStates.contains(state)
                && !activeBlocks.contains(state.block)) {

                // Don't call reset more than once on the same block.
                if (discontinued.add(state.block))
                    state.block.reset();
            }
        }

        // Set current states to next states.
        currentStates.clear();
        for (State state : nextStates) {
            assert !state.eclosure().isEmpty();
            currentStates.addAll(state.eclosure());
        }
    }

    // Return a registered default Action, if any, for this event symbol.
    private Action getDefaultAction(EventSymbol eventSymbol) {
        // Return immediately if event is null.
        if (eventSymbol == null) {
            return null;
        }

        KompicsEvent event = eventSymbol.getEvent();
        Class<? extends KompicsEvent> eventType = event.getClass();

        // Find the registered Function with the tightest class match to the event.
        Map.Entry<Class<? extends KompicsEvent>, Function<? extends KompicsEvent, Action>> match = null;
        for (Map.Entry<Class<? extends KompicsEvent>, Function<? extends KompicsEvent, Action>> entry : defaultActions) {
            if (entry.getKey().isAssignableFrom(eventType)) {
                if (match == null || match.getKey().isAssignableFrom(entry.getKey()))
                    match = entry;
            }
        }

        if (match != null)
            return getDefaultActionHelper(event, match);
        else
            return null;
    }

    // Return the action received by calling the registered function
    // with the event.
    private <E extends KompicsEvent>
    Action getDefaultActionHelper(KompicsEvent event,
                                  Map.Entry<Class<? extends KompicsEvent>,
                                  Function<? extends KompicsEvent, Action>> match) {
        Function<E, Action> function = (Function<E, Action>) match.getValue();
        Action action = function.apply((E) event);

        if (action == null) {
            throw new NullPointerException(String.format("(default handler for %s returned null for event '%s')",
                match.getKey(), event));
        }
        return action;
    }

    // Print the state transitions of the NFA.
    // This does not include
    private void printNFA(State start, State end) {
        logger.trace("==========NFA===========");
        logger.trace("Start State = {}. End State = {}", start, end);

        Set<Integer> seen = new HashSet<Integer>();
        LinkedList<State> q = new LinkedList<State>();
        seen.add(start.id);
        q.offer(start);

        while (!q.isEmpty()) {
            State s = q.poll();

            // Print the state's id and mark if it is a
            // start or end state of a block.
            String qualifier = "";
            if (s.isRepeatStart)
                qualifier += "x" + s.block.count;

            if (s.isKleeneStart)
                qualifier += "*";

            if (s.isEndOfLoop())
                qualifier += "!";
            logger.trace("{}{}", s, qualifier);


            // Print state's transitions
            for (Transition t : s.transitions) {
                logger.trace("\t\t\t{} {}", t.label, t.nextState);

                if (seen.add(t.nextState.id)) {
                    q.offer(t.nextState);
                }
            }

            // Print exit transition if any.
            if (s.exitTransition != null) {
                State exit = s.exitTransition.nextState;
                logger.trace("\t\t\tEXIT {}", exit);

                if (seen.add(exit.id))
                    q.offer(exit);
            }

            // Print loop transition if any.
            if (s.loopTransition != null) {
                State loop = s.loopTransition.nextState;
                logger.trace("\t\t\tLOOP {}", loop);

                if (seen.add(loop.id))
                    q.offer(loop);
            }
        }

        logger.trace("========================");
    }

  /*
   *
   *    PRIVATE CLASSES
   *
   */

    /**
     * A finite state automaton (FA) for statements in a test specification.
     * Subclasses of this class are combined to construct the final NFA for the entire test specification.
     *
     * A FA can be made up of smaller child FAs.
     * For example if FA $B is defined as $B=b->c where b and c are the start
     * and end states of $B respectively, and $A is defined as $A=a->$B, then
     * $B is a child of A and will be merged accordingly during the construction of $A.
     * e.g to form $A=a->b->c.
     * @see #construct(FA)
     */
    private abstract class FA {
        // The start state for this FA.
        State startState;

        // The set of final states for this FA.
        Collection<State> endStates;

        // The list of sub-automata that comprise this FA if any.
        List<FA> children = new ArrayList<FA>();

        // The block that the states in this FA belong to.
        // This does not include those states that belong to children of this FA.
        final Block block;

        FA(Block block) {
            this.block = block;
        }

        /** Construct this FA using the provided FA as the final automaton.
         *  This works by recursively building each sub-FA in the {@link #children}
         *  or in the case of {@link BaseFA} simply setting the final automaton.
         *  @param finalFA final automaton of this FA
         */
        abstract void construct(FA finalFA);

        /**
         * Create and add a Transition t to this FA
         * with t's label set to label.
         *
         * This is done by creating a sub {@link BaseFA}
         * automaton with label and adding it to a list of
         * sub-automata {@link #children}.
         * Since a {@link BaseFA} has exactly one transition t,
         * it does not implement this method.
         */
        void createAndAddTransition(Label label) {
            // Not valid for a BaseFA.
            if (this instanceof BaseFA)
                throw new UnsupportedOperationException();

            // Create sub automaton with requested transition.
            BaseFA child = new BaseFA(block);

            // Set transition's label.
            child.setLabel(label);

            // Add sub automaton to sequence of children.
            children.add(child);
        }

        // Add the provided sub-FA as a child of this FA.
        void addFA(FA childFA) {
            children.add(childFA);
        }

        // Add any final state if needed for this FA.
        void end() {}

        /** Set {@link #block} to be initialized before nested blocks. **/
        void registerParentBlock() {
            FA firstChild = children.get(0);

            // Is the first child F of this block a BaseFA?
            if (!(firstChild instanceof BaseFA)) {
                // If no, then the the start state s of F belongs to another block N.
                // Since s is also the start state of this FA, we must
                // register this FA's block to be initialized on entry to s before
                // initializing block N.
                startState.parentBlocks.add(0, block);
            }
        }
    }

    /**
     * A Finite Automaton with a single transition pointing
     * from its start state to the end state.
     */
    private class BaseFA extends FA {
        // The label on this FA's transition.
        private Label label;

        BaseFA(Block block) {
            super(block);
            // Create start state of this FA.
            startState = new State(nextId(), block);
        }

        @Override
        void construct(FA finalFA) {
            assert label != null;

            // Create sole transition and set its source as the start state.
            // The end state of the FA is the start state of the provided finalFA.
            Transition t = new Transition(label, finalFA.startState, true);
            startState.addTransition(t);

            // Is this an internal label?
            if (label instanceof InternalLabel) {
                // If yes, mark start state as active-state.
                startState.internalTransition = t;
            }
        }

        void setLabel(Label label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return "BaseFA " + startState;
        }
    }

    // Finite Automaton for a repeat block
    private class RepeatFA extends FA {
        // The number of times this FA should be traversed (set to the number
        // of times the repeat block should be executed).
        final int count;

        RepeatFA(int count, Block block) {
            super(block);
            assert count >= 1;
            this.count = count;
        }

        /**
         * Construction of a RepeatFA works by connecting each child FA to the
         * next in sequence, with the last child L connected to the provided finalFA.
         * Next, a loop transition is added from L to the first state S of this RepeatFA -
         * this allows the FA to be executed the required number of times.
         * @param finalFA final automaton of this FA
         */
        @Override
        void construct(FA finalFA) {
            // Start by building child FAs recursively using the next
            // FA in sequence as the child's finalFA - Build from Last to first.

            // The last child L. (its first state is the end of the repeat block)
            FA endFA = children.get(children.size() - 1);

            // The next FA in sequence.
            FA next = endFA;
            for (int i = children.size() - 2; i >= 0; i--) { // ignore repeat end
                FA current = children.get(i);
                current.construct(next);
                next = current;
            }

            // Set start state of this repeat block.
            FA firstChild = children.get(0);
            startState = firstChild.startState;
            startState.isRepeatStart = true;

            // Set end state of this repeat block.
            State endState = endFA.startState;
            assert endState.isRepeatEnd;

            // Add a (NULL) transition to exit out of this block.
            endState.setExitTransition(new Transition(null, finalFA.startState));

            // Add a (NULL) transition to loop back to the start of this block.
            endState.setLoopTransition(new Transition(null, startState));

            // Make sure we initialize our block before any nested blocks.
            registerParentBlock();
        }

        @Override
        void end() {
            // Create special end state for this block and
            // add it as as the last child.
            FA blockEnd = new BaseFA(block);
            blockEnd.startState.setRepeatEnd();
            children.add(blockEnd);
        }
    }

    // Finite Automaton for Kleene blocks.
    private class KleeneFA extends FA {

        KleeneFA(Block block) {
            super(block);
        }

        /**
         * Construction of a KleeneFA K works by connecting each child FA to the
         * next in sequence.
         * Next, A (loop) transition is added from the last state l of the last child L
         * to the first state s of K - allowing any number of traversals of the FA.
         * Finally, a (exit) transition is added from s to the first state of
         * the provided finalFA.
         *
         * @param finalFA final automaton of this FA
         */
        @Override
        void construct(FA finalFA) {
            // Start by building child FAs recursively using the next
            // FA in sequence as the child's finalFA - Build from Last to first.

            // The last child L. (its first state is the end of the Kleene block)
            FA endFA = children.get(children.size() - 1);

            // The next FA in sequence.
            FA next = endFA;
            for (int i = children.size() - 2; i >= 0; i--) { // ignore kleene end
                FA current = children.get(i);
                current.construct(next);
                next = current;
            }

            // Set the start state of this Kleene block.
            FA firstChild = children.get(0);
            firstChild.startState.isKleeneStart = true;
            startState = firstChild.startState;

            // Set the end state of this Kleene block.
            State endState = endFA.startState;
            assert endState.isKleeneEnd;

            // Add (NULL) transition to loop back to start state from end state.
            endState.setLoopTransition(new Transition(null, startState));

            // Add e-transition to exit out of this block.
            // We use EPSILON transitions to compute the e-closure of a state.
            startState.addTransition(new Transition(EventLabel.EPSILON_LABEL,
                                                    finalFA.startState));

            // Make sure we initialize our block before any nested blocks.
            registerParentBlock();
        }

        @Override
        void end() {
            // Create special end state for this block and
            // add it as as the last child.
            FA blockEnd = new BaseFA(block);
            blockEnd.startState.setKleeneEnd();
            children.add(blockEnd);
        }
    }

    // Finite Automaton for a Conditional Statement.
    private class ConditionalFA extends FA {

        // Start adding transitions in the either branch.
        private boolean inEitherBranch = true;

        // Contains state transitions of the $either branch.
        private List<FA> eitherBranch = new ArrayList<FA>();

        // Contains state transitions of the $or branch.
        private List<FA> orBranch = new ArrayList<FA>();

        ConditionalFA(Block block) {
            super(block);
            // Set special start state for this FA.
            startState = new State(nextId(), block);
        }

        // Start adding transitions to the 'or' branch.
        void or() {
            if (!inEitherBranch) {
                throw new IllegalStateException("multiple 'or' statements in conditional");
            }
            inEitherBranch = false;
        }

        /**
         * Create and add a Transition t to this ConditionalFA
         * with t's label set to label.
         *
         * t is added to the $either or $or branch depending
         * on our current position within the conditional
         * statement.
         *
         * This is done by creating a sub {@link BaseFA}
         * automaton with label and adding it to the appropriate
         * list of sub-automata.
         */
        @Override
        void createAndAddTransition(Label label) {
            // Create sub automaton with requested transition.
            BaseFA child = new BaseFA(block);

            // Set transition's label.
            child.setLabel(label);

            // Add child to the $either or $or branch.
            addFA(child);
        }

        // Add a child FA.
        @Override
        void addFA(FA childFA) {
            if (inEitherBranch) {
                eitherBranch.add(childFA);
            } else {
                orBranch.add(childFA);
            }
        }

        /**
         * Construct this Conditional FA.
         *
         * This works by independently constructing the
         * 'either' and 'or' branches.
         * Next, e-transitions are added from the special start state s of
         * the FA to the start state of both branches - allowing the
         * final NFA to be in the start state of both branches upon
         * entering s (computed by the e-closure).
         * @param finalFA final automaton of this FA
         */
        @Override
        void construct(FA finalFA) {
            buildBranch(eitherBranch, finalFA);
            buildBranch(orBranch, finalFA);
        }

        private void buildBranch(List<FA> branch, FA finalFA) {
            // Start by building child FAs recursively using the next
            // FA in sequence as the child's finalFA - Build from Last to first.

            // The next FA in sequence.
            FA next = finalFA;
            for (int i = branch.size() - 1; i >= 0; i--) {
                FA current = branch.get(i);
                current.construct(next);
                next = current;
            }

            // Add e-transition from the special start state of this
            // Conditional FA to the start state of this branch's FA.
            State branchStartState = branch.get(0).startState;
            startState.addTransition(new Transition(EventLabel.EPSILON_LABEL,
                                                    branchStartState));
        }

        @Override
        void end() {
            checkNonEmptyBranch();
        }

        // Throw an exception if any branch is empty.
        private void checkNonEmptyBranch() {
            if (eitherBranch.isEmpty() || orBranch.isEmpty())
                throw new IllegalStateException(
                    String.format("Empty %s branch in conditional statement.",
                        eitherBranch.isEmpty() ? "either" : "or"));
        }
    }

    /**
     * A state in the NFA.
     *
     * A state has a set of transitions.
     * @see State#transitions
     *
     * The NFA transitions from a current state C to a next state N by following
     * at least one transition T in C's transition set.
     * This is only possible if T's label L successfully matches an event symbol
     * @see State#doTransition(EventSymbol)
     * or L performs an internal action (e.g by triggering a event).
     * @see State#performInternalTransition()
     */
    private class State {

        // The id of this state
        final int id;

        // The block to which this state belongs
        final Block block;

        /** Registers, in nesting order, the parent blocks for {@link State#block}. */
        List<Block> parentBlocks = new LinkedList<Block>();

        // The transitions for outgoing from state.
        List<Transition> transitions = new ArrayList<Transition>();

        /* Set to true if this state is the final state of the entire NFA (there is exactly one such).*/
        boolean isFinalState;

        /** Set to true if this state is the start state of a {@link RepeatFA}. */
        boolean isRepeatStart;

        /** Set to true if this state is the end state of a {@link RepeatFA}. */
        boolean isRepeatEnd;

        /** Set to true if this state is the start state of a {@link KleeneFA}. */
        boolean isKleeneStart;

        /** Set to true if this state is the end state of a {@link KleeneFA}. */
        boolean isKleeneEnd;

        /**
         * The internal transition associated with this State if any.
         * If set, then this state is an active state and the transition
         * can be followed by the NFA without consuming
         * any input event symbol.
         * @see InternalLabel
         */
        Transition internalTransition;

        // The e-closure of this state.
        Set<State> eclosure;

        // Transition from this state to itself and forward associated event.
        Transition selfTransitionAndForward = new Transition(null, this, true);

        // Transition from this state to itself without forwarding the associated event.
        Transition selfTransitionAndDrop = new Transition(null, this, false);

        // Special Internal transitions for start/end of blocks //

        // Loop back transition from the end state of a block to it's start state.
        Transition loopTransition;

        // Transition from the end state of a block to the next state outside its block.
        Transition exitTransition;

        State(int number, Block block) {
            this.block = block;
            id = number;
        }

        // Add the Transition t to this State.
        void addTransition(Transition t) {
            transitions.add(t);
        }

        // Return a matching transition for this event symbol.
        Collection<Transition> getTransition(EventSymbol eventSymbol) {
            // Matching transitions.
            Collection<Transition> match = new HashSet<Transition>();

            // Don't transition out of completed loop on event.
            // This would be done later on if necessary so do nothing this time.
            if (isEndOfLoop() && !block.hasPendingEvents()) {
                return match;
            }

            // Can we match this event symbol using any
            // enclosing blockExpect statements.
            if (block.match(eventSymbol)) {
                // If yes, we forward the event and stay in the same state.
                match.add(selfTransitionAndForward);
            }

            // Did we find any match for this event?
            if (match.isEmpty()) {
                // If no, we check each transition t of this state for a match.
                for (Transition t : transitions) {
                    Label label = t.label;
                    assert label != null;

                    // Is t's label L a match?
                    if (label.match(eventSymbol)) {
                        // Is L a multi label?
                        if (label instanceof MultiLabel) {
                            // If yes, have all of L's internal labels
                            // matched an event?
                            if (((MultiLabel) label).hasCompleted()) {
                                // If yes, then we can follow t to the
                                // next state.
                                match.add(t);
                            } else {
                                // Otherwise, we stay in the same state,
                                // waiting for L's remaining internal
                                // label(s) to match.
                                //
                                // Since the multi labels decide
                                // whether or not to forward their matched
                                // events, we always use a drop transition here.
                                match.add(selfTransitionAndDrop);
                            }
                        } else {
                            // Otherwise, we transition to some next state.
                            match.add(t);
                        }
                    }
                }
            }

            // Have we still not found any matching transitions for this event?
            if (match.isEmpty()) {
                // If yes, we try block header statements (allow, drop, disallow).
                Transition blockTransition = handleWithBlockHeaders(eventSymbol);
                if (blockTransition != null) {
                    match.add(blockTransition);
                }
            }

            // Finally, If this is the start state of a block and we are
            // about to follow some transition(s), run any EntryFunctions for the
            // state's block.
            if (isStartOfLoop() && !match.isEmpty()) {
                runEntryFunctions();
            }

            return match;
        }

        // Use the constraints place on this block to match eventSymbol.
        // If successful return the Transition that matched the symbol.
        private Transition handleWithBlockHeaders(EventSymbol eventSymbol) {
            logger.trace("{}: looking up {} with {}",
                         currentStates,
                         eventSymbol,
                         block.status());

            Action action = block.getHeaderFor(eventSymbol);
            if (action == null) {
                // We were not able to match the event.
                return null;
            }
            switch (action) {
                // Is this event white-listed?
                case HANDLE:
                    // If yes, forward it.
                    logger.trace("Forwarding event [{}]", eventSymbol);
                    return selfTransitionAndForward;
                // Should we drop this event?
                case DROP:
                    logger.trace("Dropping event [{}]", eventSymbol);
                    return selfTransitionAndDrop;
                default:
                    logger.error("Observed blacklisted event [{}]", eventSymbol);
                    return ERROR_TRANSITION;
            }
        }

        /**
         *  If this state S is an active-state, then perform the
         *  action of its internal transition T and return T.
         *
         *  Otherwise if S is the start or end of a block, then return
         *  and appropriate transition.
         *  For this method to be successful,
         *  {@link #canPerformInternalTransition()} must return true immediately
         *  before its invocation.
         */
        Transition performInternalTransition() {
            // Is this the start state of a Repeat/Kleene block?
            if (isStartOfLoop()) {
                // If yes, run any entry functions available.
                runEntryFunctions();
            }

            // Does this State have an internal transition?
            if (internalTransition != null) {
                assert internalTransition.label != null;
                // If yes, execute the transition's action and
                // add it to the result if successful.
                String error = ((InternalLabel)
                                internalTransition.label).executeAction();

                // Did we get an error while executing the internal action?
                if (error != null) {
                    // If yes, print to stdErr and return an
                    // error transition instead.
                    logger.error("{}", error);
                    return ERROR_TRANSITION;
                } else {
                    // Otherwise return the internal transition.
                    return internalTransition;
                }
            }

            // If this is the last state in a Repeat/Kleene block, select a suitable transition.
            if (isEndOfLoop()) {
                return getLoopEndTransition();
            }

            // We should never reach this point as long as the state can
            // perform an internal transition - since it must either be A) an active-state
            // or B) the end of a block (the start of a block is either an A or a B).
            assert false;
            return null;
        }

        // Is this state S able to transition to some next
        // state without consuming an event symbol?
        private boolean canPerformInternalTransition() {
            // if S is end of block and block is complete, loop back transition to
            // the block's start is possible otherwise it is unable until all
            // pending events have been observed.
            if (isEndOfLoop()) {
                return !block.hasPendingEvents();
            }

            // return true iff S is an active state or the start of a Repeat/Kleene block.
            return internalTransition != null;
        }

        /**
         * Run the EntryFunction {@link EntryFunction} associated with
         * the block B, to which this state belongs.
         * If B is a nested block within a sequence of parent blocks, we
         * must always make sure that the entry functions for its parent blocks are (have
         * been) executed in nesting order before executing that of B.
         */
        private void runEntryFunctions() {
            // first run for parent blocks if not previously run
            // A parent block that hasn't run it's entry function implies that
            // all of its nested blocks have also not run their entry function.
            boolean canRunEntryFunction = false;
            for (Block parent : parentBlocks) {
                if (canRunEntryFunction) {
                    assert parent.canRunEntryFunction();
                }

                if (parent.canRunEntryFunction()) {
                    canRunEntryFunction = true;
                    runEntryFunction(parent);
                }
            }
            // finally run for current block
            runEntryFunction(block);
        }

        private void runEntryFunction(Block block) {
            logger.trace("{}: running initialize() for block {}", this, block);
            block.initialize();
        }

        /**
         * Return a suitable transition from the this State S.
         * S must be the end of a Repeat/Kleene block. i.e {@link State#isEndOfLoop()}
         * must return true immediately before invoking this method.
         *
         * As an example of finding suitable transition T consider the FA.
         *          a->b->c->d
         * with a and c as start and end states of some block B.
         * Note that this method would only be called on state c.
         * There are 3 cases:
         *
         * 1) We shouldn't try to transition out of this state as long as
         *    block B is still waiting for some events to occur (as dictated by header statements).
         *    In this case we simply return a self transition.
         * 2) If B is a Kleene block: T is always a transition back to start state a.
         * 3) If B is a Repeat block: T is a transition back to start state a if the
         * block still has more iterations, else T is a transition to state d -
         * thereby exiting the block.
         *
         * @return the selected transition from this state.
         */
        Transition getLoopEndTransition() {
            // Are we waiting for some events to occur within our current block B?
            if (block.hasPendingEvents()) {
                // If yes, Case 1: do not transition out of this state.
                return selfTransitionAndDrop;
            }

            // Otherwise, we can safely exit this state.
            logger.trace("end{} count = {}", block, block.getCurrentCount());

            // We have completed an iteration of this block
            // so we should do some house-keeping (e.g decrement repeat count for block).
            block.iterationComplete();

            assert loopTransition != null;
            if (isRepeatEnd) {
                assert exitTransition != null;
            }

            // Should we loop back to the start state or exit this block?
            if (isKleeneEnd || (isRepeatEnd && block.hasMoreIterations())) {
                // Case 2 and first part of Case 3.
                return loopTransition;
            } else {
                // We have completed all iterations of this repeat block so
                // we re-initialize it in case we need to reenter it later on.
                block.close();

                // Second part of Case 3.
                return exitTransition;
            }
        }

        // Compute the e-closure of this state
        Set<State> eclosure() {
            // If we already computed this, return immediately.
            if (eclosure != null) {
                assert !eclosure.isEmpty();
                return eclosure;
            }

            // Compute e-closure of this state using basic BFS where
            // an edge exists from state S to state s if S has an epsilon transition to s.
            Set<State> eclose = new HashSet<State>();

            // BFS Queue
            LinkedList<State> pending = new LinkedList<State>();
            pending.add(this);

            while (!pending.isEmpty()) {
                // Currently visited state.
                State S = pending.removeFirst();

                // Add S to eclosure.
                eclose.add(S);

                // Is there any previously unseen state s reachable
                // from S via the epsilon transition?
                for (Transition t : S.transitions) {
                    State s = t.nextState;
                    if (t.label == EventLabel.EPSILON_LABEL && !(eclose.contains(s) || pending.contains(s))) {
                        // If yes, add s to our pending queue to visit later
                        pending.add(s);
                    }
                }
            }

            // Remember the result for next time.
            eclosure = eclose;
            return eclosure;
        }

        // Initialize this state as the end state of a repeat block
        void setRepeatEnd() {
            isRepeatEnd = true;
        }

        // Initialize this state as the end state of a repeat block
        void setKleeneEnd() {
            isKleeneEnd = true;
        }

        void setLoopTransition(Transition loopTransition) {
            this.loopTransition = loopTransition;
        }

        void setExitTransition(Transition exitTransition) {
            this.exitTransition = exitTransition;
        }

        private boolean isEndOfLoop() {
            return isRepeatEnd || isKleeneEnd;
        }

        private boolean isStartOfLoop() {
            return isRepeatStart || isKleeneStart;
        }

        @Override
        public String toString() {
            if (this == errorState) {
                return "Error State";
            }
            return Integer.toString(id);
        }

        // Return all transitions outgoing from this state.
        // This does not include transitions created from header
        // statements (e.g blockExpect, allow etc).
        Collection<Transition> getTransitions() {
            List<Transition> result = new ArrayList<Transition>(transitions);

            // Include exit transition if one exists.
            if (exitTransition != null)
                result.add(exitTransition);

            // Include loop transition if one exists.
            if (loopTransition != null)
                result.add(loopTransition);

            return result;
        }
    }

    /**
     * A directed edge in the NFA, leading to a next (destination) state.
     * A Transition T is a a pair (l,n) where l is a {@link Label} and
     * n is the destination {@link State} pointed to by T.
     * If l does not expect to match an event symbol, e.g if l is null or l is
     * an {@link InternalLabel} instance, then T is an Internal transition.
     */
    private class Transition {
        // The Transition's Label.
        private final Label label;

        // The destination state of this transition
        private final State nextState;

        // Set to true if the event associated with the event
        // symbol matched by the transition label should be forwarded.
        boolean forwardEvent;

        // Create a Transition and set its label.
        // and destination state.
        Transition(Label label, State nextState) {
            this.label = label;
            this.nextState = nextState;
        }

        // Create a Transition and set its label.
        // and destination state. Additionally, set
        // whether or not any event matched by label should
        // be forwarded by the NFA.
        Transition(Label label, State nextState, boolean forwardEvent) {
            this(label, nextState);
            this.forwardEvent = forwardEvent;
        }

        @Override
        public String toString() {
            return String.format("(%s, %s)", label, nextState);
        }
    }
}

