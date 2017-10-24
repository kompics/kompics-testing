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

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

class EventQueue {

  private long timeoutMS = TestContext.timeout;
  private final LinkedBlockingDeque<EventSymbol> q = new LinkedBlockingDeque<EventSymbol>();

  void setTimeout(long timeout) {
    this.timeoutMS = timeout;
  }

  void offer(EventSymbol event) {
    q.offer(event);
  }

  void addFirst(EventSymbol event) {
    q.addFirst(event);
  }

  EventSymbol poll() {
    try {
      return q.poll(timeoutMS, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace();
      return null;
    }
  }
}
