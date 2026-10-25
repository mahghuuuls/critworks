package com.mahghuuuls.critworks.combat;

import com.mahghuuuls.critworks.api.CritworksHitEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reaction damage and opted-in chains (REQ-054), with the real event's chain limit. */
class ReactionGuardTest {

    private final ReactionGuard guard = new ReactionGuard(CritworksHitEvent.MAX_CHAIN_DEPTH);

    @Test
    void damageOutsideAnyHandlerIsAnAttack() {
        assertTrue(guard.allowsCritNow());
        assertEquals(1, guard.nextDepth());
    }

    @Test
    void aPlainReactionCannotCrit() {
        CritworksHitEvent event = event(1);
        final boolean[] allowed = {true};
        guard.dispatch(event::getChainLimit, () -> allowed[0] = guard.allowsCritNow());
        assertFalse(allowed[0]);
        assertEquals(1, guard.nextDepth(), "the depth is back to normal after the handlers");
    }

    @Test
    void anOptedInChainStopsAtFourLevels() {
        List<Integer> levels = new ArrayList<Integer>();
        chain(1, levels, CritworksHitEvent.MAX_CHAIN_DEPTH);
        assertEquals(java.util.Arrays.asList(1, 2, 3, 4), levels);
    }

    @Test
    void aShorterChainStopsAtItsOwnLimit() {
        List<Integer> levels = new ArrayList<Integer>();
        chain(1, levels, 2);
        assertEquals(java.util.Arrays.asList(1, 2), levels);
    }

    @Test
    void allowChainNeverPassesTheMaximum() {
        CritworksHitEvent event = event(1);
        event.allowChain(10);
        assertEquals(CritworksHitEvent.MAX_CHAIN_DEPTH, event.getChainLimit());
        List<Integer> levels = new ArrayList<Integer>();
        chain(1, levels, 99);
        assertEquals(4, levels.size());
    }

    @Test
    void theDepthIsRestoredWhenAHandlerThrows() {
        CritworksHitEvent event = event(1);
        assertThrows(IllegalStateException.class, () -> guard.dispatch(event::getChainLimit, () -> {
            throw new IllegalStateException("addon bug");
        }));
        assertEquals(1, guard.nextDepth());
        assertTrue(guard.allowsCritNow());
    }

    @Test
    void aChainNotRenewedAtTheNextLevelStopsThere() {
        final List<Integer> levels = new ArrayList<Integer>();
        final CritworksHitEvent first = event(1);
        levels.add(1);
        guard.dispatch(first::getChainLimit, () -> {
            first.allowChain(CritworksHitEvent.MAX_CHAIN_DEPTH);
            if (guard.allowsCritNow()) {
                final CritworksHitEvent second = event(guard.nextDepth());
                levels.add(second.getDepth());
                // The deeper event does not opt in again: its reactions are plain.
                guard.dispatch(second::getChainLimit, () -> {
                    if (guard.allowsCritNow()) {
                        levels.add(guard.nextDepth());
                    }
                });
            }
        });
        assertEquals(java.util.Arrays.asList(1, 2), levels);
    }

    @Test
    void theGuardAloneCapsAChainAtItsMaximum() {
        ReactionGuard shortGuard = new ReactionGuard(2);
        List<Integer> levels = new ArrayList<Integer>();
        chainOn(shortGuard, 1, levels);
        assertEquals(java.util.Arrays.asList(1, 2), levels);
    }

    private static void chainOn(final ReactionGuard g, int depth, final List<Integer> levels) {
        final CritworksHitEvent event = event(depth);
        levels.add(depth);
        g.dispatch(event::getChainLimit, () -> {
            event.allowChain(CritworksHitEvent.MAX_CHAIN_DEPTH);
            if (g.allowsCritNow()) {
                chainOn(g, g.nextDepth(), levels);
            }
        });
    }

    /** Simulates an addon that reacts to every event, opting in each time with {@code levels}. */
    private void chain(int depth, List<Integer> levels, int requested) {
        final CritworksHitEvent event = event(depth);
        levels.add(depth);
        guard.dispatch(event::getChainLimit, () -> {
            event.allowChain(requested);
            // The reaction's damage: it fires a deeper event only when the guard allows a crit now.
            if (guard.allowsCritNow()) {
                chain(guard.nextDepth(), levels, requested);
            }
        });
    }

    private static CritworksHitEvent event(int depth) {
        return new CritworksHitEvent(null, null, null, null, CritworksHitEvent.Kind.MELEE_PRIMARY, 2.0D, false, 4.0F, depth);
    }
}
