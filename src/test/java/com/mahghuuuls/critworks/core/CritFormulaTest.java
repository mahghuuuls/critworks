package com.mahghuuuls.critworks.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the production chance and damage formulas with the documented worked examples, including
 * the clamps and the cap.
 */
class CritFormulaTest {

    private static final double EPSILON = 1.0E-9D;

    @Test
    void chanceScalesBaseThenAddsPoints() {
        assertEquals(20.0D, CritFormula.finalChance(10.0D, 50.0D, 5.0D), EPSILON);
    }

    @Test
    void increasedChanceAloneNeverEnablesAZeroBase() {
        assertEquals(0.0D, CritFormula.finalChance(0.0D, 500.0D, 0.0D), EPSILON);
    }

    @Test
    void additionalChanceEnablesAZeroBase() {
        assertEquals(3.0D, CritFormula.finalChance(0.0D, 0.0D, 3.0D), EPSILON);
    }

    @Test
    void chanceIsClampedToZeroAndOneHundred() {
        assertEquals(100.0D, CritFormula.finalChance(90.0D, 0.0D, 30.0D), EPSILON);
        assertEquals(0.0D, CritFormula.finalChance(5.0D, 0.0D, -10.0D), EPSILON);
    }

    @Test
    void negativeIncreasedChanceIsADebuff() {
        assertEquals(5.0D, CritFormula.finalChance(10.0D, -50.0D, 0.0D), EPSILON);
    }

    @Test
    void increasedDamageScalesOnlyTheBonusPart() {
        assertEquals(2.0D, CritFormula.finalMultiplier(1.5D, 100.0D, 0.0D, CritFormula.NO_CAP), EPSILON);
    }

    @Test
    void additionalDamageAddsMultiplierUnits() {
        assertEquals(2.0D, CritFormula.finalMultiplier(1.5D, 0.0D, 0.5D, CritFormula.NO_CAP), EPSILON);
        assertEquals(1.75D, CritFormula.finalMultiplier(1.5D, 0.0D, 0.25D, CritFormula.NO_CAP), EPSILON);
    }

    @Test
    void multiplierNeverDropsBelowOne() {
        assertEquals(1.0D, CritFormula.finalMultiplier(1.2D, -200.0D, 0.0D, CritFormula.NO_CAP), EPSILON);
    }

    @Test
    void capLimitsTheMultiplier() {
        assertEquals(3.0D, CritFormula.finalMultiplier(2.0D, 0.0D, 5.0D, 3.0D), EPSILON);
    }

    @Test
    void noCapLeavesLargeMultipliers() {
        assertEquals(12.0D, CritFormula.finalMultiplier(12.0D, 0.0D, 0.0D, CritFormula.NO_CAP), EPSILON);
    }

    @Test
    void shippedFallbackResolvesToItself() {
        assertEquals(5.0D, CritFormula.finalChance(5.0D, 0.0D, 0.0D), EPSILON);
        assertEquals(1.5D, CritFormula.finalMultiplier(1.5D, 0.0D, 0.0D, CritFormula.NO_CAP), EPSILON);
    }

    @Test
    void onlyFiniteValuesAreUsable() {
        assertTrue(CritFormula.isUsable(-3.0D));
        assertFalse(CritFormula.isUsable(Double.NaN));
        assertFalse(CritFormula.isUsable(Double.POSITIVE_INFINITY));
    }
}
