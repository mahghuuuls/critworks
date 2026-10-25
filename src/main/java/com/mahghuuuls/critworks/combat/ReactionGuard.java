package com.mahghuuuls.critworks.combat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.IntSupplier;

/**
 * Owns reaction damage: damage that starts while critical-hit event handlers run. Such damage cannot
 * crit and fires no event, unless the running event allowed a chain and the next level stays within
 * the chain limit. The depth is always restored, also when a handler throws.
 */
final class ReactionGuard {

    /** One entry per event being handled; each gives the chain limit its handlers set. */
    private final Deque<IntSupplier> running = new ArrayDeque<IntSupplier>();
    private final int maxDepth;

    ReactionGuard(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    /** The level of the next event: 1 outside any handler. */
    int nextDepth() {
        return running.size() + 1;
    }

    /** Whether damage that starts now may crit and fire an event. */
    boolean allowsCritNow() {
        IntSupplier current = running.peek();
        if (current == null) {
            return true;
        }
        int next = nextDepth();
        return next <= maxDepth && next <= current.getAsInt();
    }

    /** Runs {@code handlers} as the event whose chain limit {@code chainLimit} reports. */
    void dispatch(IntSupplier chainLimit, Runnable handlers) {
        running.push(chainLimit);
        try {
            handlers.run();
        } finally {
            running.pop();
        }
    }
}
