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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * A Block can either be a Repeat block or a Kleene block.
 */
class Block {
    // Block header.
    private static class BLOCK_HEADER {
        final EventLabel event;
        final Action action;
        BLOCK_HEADER (EventLabel event, Action action) {
            this.event = event;
            this.action = action;
        }
    }

    // If this is a Repeat block, set the number of iterations at a time.
    final int count;

    // Updated with the number of times this block has been iterated so far?
    private int currentCount;

    // Id for a Kleene block.
    static final int STAR = -1;

    // Set to true if this is the MainBlock.
    // The MainBlock contains the statements and nests the blocks of the entire test case.
    private boolean isMainBlock;

    // Set to true if this is a Kleene block.
    private boolean isKleeneBlock;

    // Set the EntryFunction for this block.
    private EntryFunction entryFunction;

    // Set to true if we can run the EntryFunction for this block.
    // This is always true before executing the first statement of
    // this block as well as after an iteration of the block.
    private boolean canRunEntryFunction = true;

    // Set the lowest parent block within which this block is nested.
    final Block previousBlock;

    // Set to true if we are currently executing statements belonging to this block.
    private boolean currentlyExecuting;

    // Store all block header declarations in added order.
    private final ArrayList<BLOCK_HEADER> headerDeclarations;

    // Expected event within an iteration of this block.
    private List<SingleLabel> expected = new LinkedList<SingleLabel>();

    // Not-yet observed but expected events within an iteration of this block.
    private LinkedList<SingleLabel> pending = new LinkedList<SingleLabel>();

    // Create a Block with an EntryFunction.
    Block(Block previousBlock, int count, EntryFunction entryFunction) {
        this(previousBlock, count);
        this.entryFunction = entryFunction;
    }

    Block(Block previousBlock, int count) {
        this.count = count;

        // Is this a Kleene Block?
        if (count == STAR) {
            isKleeneBlock = true;
        }

        this.previousBlock = previousBlock;

        headerDeclarations = new ArrayList<BLOCK_HEADER>();

        // Do we have a parent block?
        if (previousBlock != null) {
            // If yes, we must be a nested block - initialize block headers
            // based on parent.
            headerDeclarations.addAll(previousBlock.headerDeclarations);
        } else {
            // Otherwise, we must be the MainBlock
            isMainBlock = true;
        }
    }

    // Return the number of times this block has been iterated.
    int getCurrentCount() {
        return currentCount;
    }

    /**
     * Prepare this block for iteration (execution of statements).
     * This is done by
     * 1) Resetting {@link #currentCount} to {@link #count} if this is a repeat block.
     * 2) Running the EntryFunction of this block if available.
     */
    void initialize() {
        if (!isKleeneBlock && !isCurrentlyExecuting()) {
            currentCount = count;
        }

        // Do we have any EntryFunction to run?
        if (entryFunction != null) {
            entryFunction.run();
        }

        // Signal that we have started executing statements in this block
        currentlyExecuting = true;

        // We can not run EntryFunction until this iteration is complete.
        canRunEntryFunction = false;
    }

    /**
     * This method is called upon completion of an iteration of the states in this Block.
     * We perform cleanup and re-initialization of this Block in case it needs to be
     * re-entered later on.
     *
     * First, we repopulate the expected events of the block. Second, we set the flag
     * to let the block's EntryFunction to be re-executed.
     * If this is a Repeat block, we also decrement its count.
     */
    void iterationComplete() {
        assert pending.isEmpty();

        // Reset expected observed events.
        resetBlockEvents();

        // Is this a repeat block?
        if (!isKleeneBlock) {
            // If yes, decrement its current count.
            currentCount--;
            // MainBlock may be decremented multiple count
            assert isMainBlock || currentCount >= 0;
        }

        // We can now run the EntryFunction later on if needed.
        canRunEntryFunction = true;
    }

    /**
     * For Repeat blocks, a distinction is made between a complete iteration of
     * the block and a complete execution of the block.
     * @see Block#iterationComplete() for the latter.
     * In this, the former, case, we exit the block entirely so we must signal that we
     * are done with its execution.
     */
    void close() {
        if (isMainBlock) {
            // Don't close main block since it cannot be reopened.
            return;
        }
        // Note: A Block that doesn't do anything at runtime will still be closed
        assert currentlyExecuting;
        // We have now completed the execution of this block.
        currentlyExecuting = false;
    }

    // Return true if we have not run our EntryFunction since the
    // last complete iteration of this block.
    boolean canRunEntryFunction() {
        return canRunEntryFunction;
    }

    /**
     * Reset this Block.
     * This method is called when the NFA is about to transition from
     * its set of current states C to a set of next states N and there exists some
     * state s in C such that s belongs to this Block B and there exists no such state
     * s in N.
     *
     * This means that block B went out of scope prematurely.
     * This will happen when the NFA kills one of its current threads.
     * The last state to fail to transition was s.
     *
     * We must reset block B, since it must have been initialized at
     * some point previously, in order to correctly re-enter it later on if needed.
     *
     * Note: This is caused by some block lacking a clear entry condition.
     *
     * @see NFA#updateCurrentState(Set)
     */
    void reset() {
        // Are we the MainBlock?
        if (isMainBlock) {
            // If yes, do not reset.
            return;
        }

        // If this is a Repeat block, it counts as a current iteration, so
        // don't update currentCount.
        // Is this a Kleene block?
        if (isKleeneBlock) {
            // If yes, we are no longer executing.
            currentlyExecuting = false;
        }
        resetBlockEvents();
    }

    /**
     * Return true if this Block has been initialized for iteration and
     * its current iteration is not yet complete.
     *
     * @return currentlyExecuting.
     */
    private boolean isCurrentlyExecuting() {
        return currentlyExecuting;
    }

    // Re-initialize data structures for tracking events expected
    // to be observed within this block.
    private void resetBlockEvents() {
        pending.clear();
        pending.addAll(expected);
    }

    // Return true if we still have any iterations of this Block left. (Repeat Block only)
    boolean hasMoreIterations() {
        return currentCount > 0;
    }

    // Return true if we expect to observe this event
    // within this Block or parent blocks (blockExpect).
    boolean match(EventSymbol eventSymbol) {
        // Set to true if we find a match for this event symbol.
        boolean found = false;

        // Find a match for this event in its list of blockExpect statements.
        Iterator<SingleLabel> it = pending.iterator();
        while (it.hasNext()) {
            SingleLabel label = it.next();
            if (label.match(eventSymbol)) {
                // Mark event as observed by removing matching label.
                it.remove();
                found = true;
                break;
            }
        }

        // If we did not find a match, check recursively with parent block.
        if (!found)
            return previousBlock != null && previousBlock.match(eventSymbol);

        TestContext.logger.trace("Event {} matched by {}", eventSymbol, status());
        return true;
    }

    // Return true if this Block still expects to observe some events.
    boolean hasPendingEvents() {
        return !pending.isEmpty();
    }

    // Expect an event to be observed within this Block. (blockExpect).
    void expect(EventLabel label) {
        expected.add(label);
        // Add to pending for initial iteration.
        pending.add(label);
    }

    // Black-list events matched by this label.
    void blacklist(EventLabel label) {
        headerDeclarations.add(new BLOCK_HEADER(label, Action.FAIL));
    }

    // White-list events matched by this label.
    void whitelist(EventLabel label) {
        headerDeclarations.add(new BLOCK_HEADER(label, Action.HANDLE));
    }

    // Do not forward events matched by this label.
    void drop(EventLabel label) {
        headerDeclarations.add(new BLOCK_HEADER(label, Action.DROP));
    }

    Action getHeaderFor(EventSymbol eventSymbol) {
        // Check block headers in LIFO order.
        for (int i = headerDeclarations.size() - 1; i >= 0; i--) {
            BLOCK_HEADER header = headerDeclarations.get(i);
            if (header.event.match(eventSymbol)) {
                return header.action;
            }
        }
        return null;
    }

    String status() {
        return String.format("Block[%s Pending%s]",
                             count == STAR ? "*" : count,
                             pending.toString());
    }

    @Override
    public String toString() {
        return "Repeat(" + (count == STAR ? "*" : count) + ")";
    }
}
