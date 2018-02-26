package se.sics.kompics.testing;

// Models the result of performing a sequence of transitions.
public class TransitionResult {
    // Should the the observed event (if any) that initiated the
    // transition sequence be forwarded?
    public final boolean forwardEvent;
    // Did the last transition end in a final state?
    public final boolean inFinalState;
    // Did the last transition end in an error state?
    public final boolean inErrorState;

    TransitionResult(boolean forwardEvent, boolean inFinalState, boolean inErrorState) {
        this.forwardEvent = forwardEvent;
        this.inFinalState = inFinalState;
        this.inErrorState = inErrorState;
    }
}
