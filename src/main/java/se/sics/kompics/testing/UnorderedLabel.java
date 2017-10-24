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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * A {@link MultiLabel} for a set S of unordered statements
 * where the statements in S can match event symbols in any order.
 *
 * An event e associated with a matched symbol can
 * either be forwarded immediately or batched and later forwarded together
 * with the events of other matched symbols when 'all' statements
 * in S have successfully matched a symbol.
 */
class UnorderedLabel implements MultiLabel {

    // Enclose each statement using a label internally.
    private final List<SingleLabel> labels;

    // EventSymbols that have been matched.
    private final List<EventSymbol> matchedSymbols;

    /** We forwarding matched events in batch mode, we
     * treat those event symbols that were matched by
     * {@link se.sics.kompics.testing.AnswerRequestLabel} labels
     *  separately since 1) they are not forwarded and 2) instead
     *  the label itself may need to trigger a response.
     *  @see AnswerRequestLabel#match(EventSymbol)
     *  @see AnswerRequestLabel#triggerResponse()
     *  for case 1) and 2) respectively.
     */
    private final List<AnswerRequestLabel> matchedRequests;

    // Labels that are yet to be matched.
    private final List<SingleLabel> pendingLabels;

    // If set to true, matched events are forwarded immediately.
    private final boolean forwardImmediately;

    UnorderedLabel(List<SingleLabel> labels, boolean forwardImmediately) {
        this.labels = labels;
        this.forwardImmediately = forwardImmediately;

        matchedSymbols = new LinkedList<EventSymbol>();
        matchedRequests = new LinkedList<AnswerRequestLabel>();

        // Initialize pending events prior to first traversal.
        pendingLabels = new LinkedList<SingleLabel>(labels);
    }

    // Return true if an internal label l matches this event symbol.
    // Remove label l from pending and add to matched symbols.
    // If in batch mode, queue matched event otherwise forward event immediately.
    @Override
    public boolean match(EventSymbol eventSymbol) {
        // Set to true if we find a match.
        boolean matched = false;

        Iterator<SingleLabel> it = pendingLabels.iterator();
        while (it.hasNext()) {
            SingleLabel label = it.next();

            // Is this label a match?
            if (!label.match(eventSymbol))
                continue;

            // If yes, remove it and flag match.
            it.remove();
            matched = true;

            // Should we forward the event immediately?
            if (forwardImmediately) {
                eventSymbol.forwardEvent();
            }
            else {
                // Otherwise, we queue the event until all pending
                // events have been matched.
                if (label instanceof AnswerRequestLabel)
                    // Queue response to be triggered later.
                    matchedRequests.add((AnswerRequestLabel) label);
                else
                    matchedSymbols.add(eventSymbol);
            }

            // Match exactly once.
            break;
        }

        // Have we matched all expected event symbols and in batch mode?
        if (pendingLabels.isEmpty() && !forwardImmediately)
            forwardMatchedEvents();

        return matched;
    }

    private void forwardMatchedEvents() {
        // Trigger responses if any for matched requests.
        for (AnswerRequestLabel label : matchedRequests)
            label.triggerResponse();

        // Forward normal events.
        for (EventSymbol eventSymbol : matchedSymbols) {
            eventSymbol.forwardEvent();
        }
    }

    // Re-initialize label and Return true: if all internal labels have been matched.
    @Override
    public boolean hasCompleted() {
        if (!pendingLabels.isEmpty())
            return false;

        // Reset pending labels for future traversal.
        pendingLabels.addAll(labels);
        matchedSymbols.clear();
        matchedRequests.clear();
        return true;
    }

    public String toString() {
        return String.format("Unordered<Pending%s Observed%s>",
                             pendingLabels,
                             matchedSymbols);
    }
}
