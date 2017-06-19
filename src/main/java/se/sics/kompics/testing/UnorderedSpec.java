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

import java.util.LinkedList;
import java.util.List;

class UnorderedSpec implements MultiEventSpec{

  private final List<SingleEventSpec> expectUnordered;
  private List<SingleEventSpec> pending;
  private List<EventSpec> seen;

  UnorderedSpec(List<SingleEventSpec> expectUnordered) {
    this.expectUnordered = expectUnordered;
    seen = new LinkedList<EventSpec>();
    pending = new LinkedList<SingleEventSpec>(expectUnordered);
  }

  @Override
  public boolean match(EventSpec receivedSpec) {
    if (pending.contains(receivedSpec)) {
      int index = pending.indexOf(receivedSpec);
      seen.add(receivedSpec);
      pending.remove(index);

      if (pending.isEmpty()) {
        for (EventSpec e : seen) {
          e.handle();
        }
      }
      return true;
    }
    return false;
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
    for (SingleEventSpec spec : expectUnordered) {
      pending.add(spec);
    }
    seen.clear();
  }

  public String toString() {
    StringBuilder sb = new StringBuilder("Unordered<Seen(");
    for (EventSpec e : seen) {
      sb.append(" ").append(e);
    }
    sb.append(")Pending(");
    for (Spec e : pending) {
      sb.append(" ").append(e);
    }
    sb.append(")>");
    return sb.toString();
  }

}
