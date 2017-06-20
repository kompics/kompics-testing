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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

class UnorderedSpec implements MultiEventSpec{

  private final List<SingleEventSpec> eventSpecs;
  private List<SingleEventSpec> pending;
  private List<SingleEventSpec> received;
  private List<AnswerRequestSpec> answerRequestSpecs;

  private final boolean immediateResponse;

  UnorderedSpec(List<SingleEventSpec> eventSpecs, boolean immediateResponse) {
    this.eventSpecs = eventSpecs;
    this.immediateResponse = immediateResponse;
    pending = new LinkedList<SingleEventSpec>(eventSpecs);
    received = new LinkedList<SingleEventSpec>();
  }

  @Override
  public boolean match(EventSpec receivedSpec) {
    Iterator<SingleEventSpec> it = pending.iterator();
    boolean foundMatch = false;

    // if any symbol matches, add to received list or handle immediately
    // remove matched symbol
    while (it.hasNext()) {
      SingleEventSpec spec = it.next();
      if (spec.match(receivedSpec)) {
        foundMatch = true;
        if (immediateResponse) {
          receivedSpec.handle();
        } else {
          received.add(spec instanceof AnswerRequestSpec? spec :receivedSpec);
        }

        it.remove();
        break;
      }
    }

    // if all received, handle as requested
    if (pending.isEmpty() && !immediateResponse) {
      for (SingleEventSpec spec : received) {
        if (spec instanceof AnswerRequestSpec) {
          ((AnswerRequestSpec) spec).triggerResponse();
        } else {
          ((EventSpec)spec).handle();
        }
      }
    }
    return foundMatch;
  }

  @Override
  public boolean isComplete() {
    if (pending.isEmpty()) {
      reset();
      return true;
    }
    return false;
  }

  private void reset() {
    pending.addAll(eventSpecs);
    received.clear();
  }

  public String toString() {
    StringBuilder sb = new StringBuilder("Unordered<Pending");
    sb.append(pending.toString()).append(" Received").append(received.toString()).append(">");
    return sb.toString();
  }

}
