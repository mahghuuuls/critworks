package com.mahghuuuls.critworks.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rule that links an arrow to its bow release: same shooter, same tick, one arrow per release. */
class PendingShotsTest {

    @Test
    void twoShootersInOneTickEachGetTheirOwnRelease() {
        PendingShots<String, String> shots = new PendingShots<String, String>();
        shots.record("alice", "alice's bow", 100L);
        shots.record("bob", "bob's bow", 100L);
        assertEquals("bob's bow", shots.take("bob", 100L));
        assertEquals("alice's bow", shots.take("alice", 100L));
    }

    @Test
    void aReleaseLinksToOneArrowOnly() {
        PendingShots<String, String> shots = new PendingShots<String, String>();
        shots.record("alice", "bow", 100L);
        assertEquals("bow", shots.take("alice", 100L));
        assertNull(shots.take("alice", 100L));
    }

    @Test
    void anArrowOfALaterTickDoesNotTakeAnOldRelease() {
        PendingShots<String, String> shots = new PendingShots<String, String>();
        shots.record("alice", "bow", 100L);
        assertNull(shots.take("alice", 101L));
        assertTrue(shots.isEmpty());
    }

    @Test
    void anotherShootersArrowFindsNothing() {
        PendingShots<String, String> shots = new PendingShots<String, String>();
        shots.record("alice", "bow", 100L);
        assertNull(shots.take("bob", 100L));
        assertEquals("bow", shots.take("alice", 100L));
    }
}
