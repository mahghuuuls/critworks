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

    @Test
    void aPendingGuaranteeDecidesWithoutARollAndIsNotUsedByTheDecision() {
        com.mahghuuuls.critworks.state.PlayerCritState state = new com.mahghuuuls.critworks.state.PlayerCritState();
        state.grant(com.mahghuuuls.critworks.api.GuaranteeSpec.of("a"));
        CritRoller.Decision decision = CritRoller.decide(state, 0.0D, new Random(1L));
        assertTrue(decision.critical, "a guarantee crits even at chance 0");
        assertTrue(decision.isGuaranteed());
        assertTrue(Double.isNaN(decision.roll));
        assertFalse(state.isEmpty(), "the attack, not the decision, uses the guarantee");
    }

    @Test
    void withoutAGuaranteeTheRollDecides() {
        CritRoller.Decision none = CritRoller.decide(new com.mahghuuuls.critworks.state.PlayerCritState(), 0.0D,
                new Random(1L));
        assertFalse(none.critical);
        assertFalse(none.isGuaranteed());
        assertTrue(CritRoller.decide(null, 100.0D, new Random(1L)).critical);
    }
}
