package com.mahghuuuls.critworks.combat;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the production roll rule: a chance of 100 always crits, a chance of 0 never does. */
class CritRollerTest {

    @Test
    void rollIsBelowOneHundredSoFullChanceAlwaysCrits() {
        Random random = new Random(42L);
        for (int i = 0; i < 10000; i++) {
            double roll = CritRoller.roll(random);
            assertTrue(roll >= 0.0D && roll < 100.0D);
            assertTrue(CritRoller.isCritical(roll, 100.0D));
            assertFalse(CritRoller.isCritical(roll, 0.0D));
        }
    }

    @Test
    void criticalWhenRollIsBelowChance() {
        assertTrue(CritRoller.isCritical(4.99D, 5.0D));
        assertFalse(CritRoller.isCritical(5.0D, 5.0D));
    }
}
