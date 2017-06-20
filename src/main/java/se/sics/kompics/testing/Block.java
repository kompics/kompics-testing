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

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

class Block {

  private final int times;
  private int currentCount;
  static final int STAR = -1;
  private boolean isMainBlock;

  private boolean isKleeneBlock;

  private BlockInit blockInit;
  final Block previousBlock;

  private boolean currentlyExecuting;
  private boolean runInit = true;

  private Set<EventSpec> disallowed;
  private Set<EventSpec> allowed;
  private Set<EventSpec> dropped;

  private List<SingleEventSpec> expected = new LinkedList<SingleEventSpec>();
  private Multiset<SingleEventSpec> pending = HashMultiset.create();
  private Multiset<SingleEventSpec> received = HashMultiset.create();

  enum MODE { HEADER, BODY, UNORDERED, ANSWER_REQUEST, CONDITIONAL}

  Block(Block previousBlock, int count, BlockInit blockInit) {
    this(previousBlock, count);
    this.blockInit = blockInit;
  }

  Block() {
    this(null, 1);
    isMainBlock = true;
  }

  Block(Block previousBlock, int count) {
    this.times = count;

    if (count == STAR) {
      isKleeneBlock = true;
    }

    this.previousBlock = previousBlock;

    if (previousBlock == null) {
      initEmptyBlock();
    } else {
      this.disallowed = new HashSet<EventSpec>(previousBlock.disallowed);
      this.allowed = new HashSet<EventSpec>(previousBlock.allowed);
      this.dropped = new HashSet<EventSpec>(previousBlock.dropped);
    }
  }

  int getCurrentCount() {
    return currentCount;
  }

  void initialize() {
    if (!isKleeneBlock && !isOpen()) {
      currentCount = times;
    }

    if (blockInit != null) {
      blockInit.init();
    }

    currentlyExecuting = true;
    runInit = false;
  }

  void iterationComplete() {
    assert pending.isEmpty();
    resetBlockEvents();

    if (!(isKleeneBlock)) {
      currentCount--;
      // main block may be decremented multiple times
      assert isMainBlock || currentCount >= 0;
    }
    runInit = true;
  }

  boolean canRunInit() {
    return runInit;
  }

  void reset() {
    if (isMainBlock) {
      return;
    }

    if (isKleeneBlock) {
      // without explicit end conditions -
      // kleene blocks only go out of scope when the thread is discontinued by the NFA
      // close the block to re-enable block init to run later if needed
      currentlyExecuting = false;
    }
    resetBlockEvents();
  }

  void close() {
    if (isMainBlock) {
      // don't close main block since it cannot be reopened
      return;
    }
    assert isOpen(); // block that doesn't do anything at runtime will still be closed
    currentlyExecuting = false;
  }

  boolean isOpen() {
    return currentlyExecuting;
  }

  private void resetBlockEvents() {
    pending.clear();
    received.clear();

    for (SingleEventSpec spec : expected) {
      pending.add(spec);
    }
  }

  boolean hasMoreIterations() {
    return currentCount > 0;
  }

  void expect(SingleEventSpec spec) {
    expected.add(spec);
    pending.add(spec);
  }

  boolean handle(EventSpec receivedSpec) {
    int remaining = pending.count(receivedSpec);
    if (remaining == 0) {
      return previousBlock != null && previousBlock.handle(receivedSpec);
    }

    pending.remove(receivedSpec);
    TestContext.logger.trace("Event {} will be handled by {}", receivedSpec, status());
    return true;
  }

  boolean hasPendingEvents() {
    return !pending.isEmpty();
  }

  String status() {
    StringBuilder sb = new StringBuilder("Block[");
    sb.append(times == STAR? "*" : times).append(" Received").append(received);
    sb.append(" Pending").append(pending);
    sb.append("]");
    return sb.toString();
  }

  private void initEmptyBlock() {
    disallowed = new HashSet<EventSpec>();
    allowed = new HashSet<EventSpec>();
    dropped = new HashSet<EventSpec>();
  }

  void addDisallowedMessage(EventSpec eventSpec) {
    if (disallowed.add(eventSpec)) {
      allowed.remove(eventSpec);
      dropped.remove(eventSpec);
    }
  }

  void addAllowedMessage(EventSpec eventSpec) {
    if (allowed.add(eventSpec)) {
      disallowed.remove(eventSpec);
      dropped.remove(eventSpec);
    }
  }

  void addDroppedMessage(EventSpec eventSpec) {
    if (dropped.add(eventSpec)) {
      disallowed.remove(eventSpec);
      allowed.remove(eventSpec);
    }
  }

  Collection<EventSpec> getAllowedSpecs() {
    return allowed;
  }

  Collection<EventSpec> getDisallowedSpecs() {
    return disallowed;
  }
  Collection<EventSpec> getDroppedSpecs() {
    return dropped;
  }

  @Override
  public String toString() {
    return "Repeat(" + (times == STAR? "*" : times) + ")";
  }
}
