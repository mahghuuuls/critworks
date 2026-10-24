package com.mahghuuuls.critworks.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the production charge threshold rule at the shipped default and the range ends. */
class ChargeRuleTest {

    @Test
    void fullChargeMeetsTheDefaultThreshold() {
        assertTrue(ChargeRule.meetsThreshold(1.0F, 100.0D));
    }

    @Test
    void ninetyFivePercentMissesTheDefaultThreshold() {
        assertFalse(ChargeRule.meetsThreshold(0.95F, 100.0D));
    }

    @Test
    void floatRoundingDoesNotMissAnEqualThreshold() {
        // 0.95F times 100 is 94.9999988; the rule must still treat it as 95%.
        assertTrue(ChargeRule.meetsThreshold(0.95F, 95.0D));
    }

    @Test
    void zeroThresholdAcceptsAnyCharge() {
        assertTrue(ChargeRule.meetsThreshold(0.0F, 0.0D));
    }

    @Test
    void halfChargeMeetsAHalfThreshold() {
        assertTrue(ChargeRule.meetsThreshold(0.5F, 50.0D));
        assertFalse(ChargeRule.meetsThreshold(0.49F, 50.0D));
    }
}
