package com.mahghuuuls.critworks.combat;

import com.mahghuuuls.critworks.core.ChargeRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bow draw at release as used by the bow threshold (REQ-017). */
class ArrowDrawTest {

    @Test
    void fullDrawIsTheVanillaFullDrawTime() {
        assertEquals(1.0F, ArrowShotTracker.drawFraction(20));
        assertEquals(1.0F, ArrowShotTracker.drawFraction(72000), "holding longer stays a full draw");
        assertTrue(ChargeRule.meetsThreshold(ArrowShotTracker.drawFraction(20), 100.0D));
        assertFalse(ChargeRule.meetsThreshold(ArrowShotTracker.drawFraction(19), 100.0D));
    }

    @Test
    void halfDrawnIsEligibleAtThreshold50Only() {
        float half = ArrowShotTracker.drawFraction(10);
        assertEquals(0.5F, half);
        assertTrue(ChargeRule.meetsThreshold(half, 50.0D));
        assertFalse(ChargeRule.meetsThreshold(half, 100.0D));
        assertFalse(ChargeRule.meetsThreshold(ArrowShotTracker.drawFraction(9), 50.0D));
    }

    @Test
    void noDrawIsZero() {
        assertEquals(0.0F, ArrowShotTracker.drawFraction(0));
        assertEquals(0.0F, ArrowShotTracker.drawFraction(-5));
    }
}
