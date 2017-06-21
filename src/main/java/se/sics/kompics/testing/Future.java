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

import se.sics.kompics.KompicsEvent;

public abstract class Future<E extends KompicsEvent, R extends KompicsEvent> {

  /**
   * Sets the future instance if the specified request is matched.
   * @param request request event.
   * @return true iff request was matched.
   */
  public abstract boolean set(E request);

  /**
   * Returns a response event if future instance has previously been successfully set via call to {@link #set(KompicsEvent)}.
   * Otherwise null should be returned.
   * @return  response event
   */
  public abstract R get();

  @Override
  public final int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public final boolean equals(Object o) {
    return this == o;
  }
}
