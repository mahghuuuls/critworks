package com.mahghuuuls.critworks.core;

import com.mahghuuuls.critworks.bonus.Bonus;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Exercises the production per-attack eligibility rule: weapon, charge, target kind, and PvP setting. */
class AttackRulesTest {

    private static final CritProfile WEAPON = CritProfile.eligible("recognized weapon", 5.0D, "fallback", 1.5D,
            "fallback", 5.0D, 1.5D, CritFormula.NO_CAP, Collections.<Bonus>emptyList());

    @Test
    void fullChargeOnAMobIsEligible() {
        assertEquals("", AttackRules.problem(WEAPON, 1.0D, 100.0D, true, false, true));
    }

    @Test
    void weaponReasonComesFirst() {
        CritProfile stick = CritProfile.notEligible(StatResolver.REASON_NOT_A_WEAPON);
        assertEquals(StatResolver.REASON_NOT_A_WEAPON, AttackRules.problem(stick, 0.0D, 100.0D, false, false, true));
    }

    @Test
    void lowChargeIsNotEligible() {
        assertEquals(AttackRules.REASON_CHARGE, AttackRules.problem(WEAPON, 0.95D, 100.0D, true, false, true));
    }

    @Test
    void nonLivingTargetIsNotEligible() {
        assertEquals(AttackRules.REASON_TARGET, AttackRules.problem(WEAPON, 1.0D, 100.0D, false, false, true));
    }

    @Test
    void playerTargetFollowsThePvpSetting() {
        assertEquals("", AttackRules.problem(WEAPON, 1.0D, 100.0D, true, true, true));
        assertEquals(AttackRules.REASON_PVP, AttackRules.problem(WEAPON, 1.0D, 100.0D, true, true, false));
        assertEquals("", AttackRules.problem(WEAPON, 1.0D, 100.0D, true, false, false));
    }

    @Test
    void anArrowIsDecidedByWeaponAndDrawAtRelease() {
        assertEquals("", AttackRules.releaseProblem(WEAPON, 1.0D, 100.0D));
        assertEquals(AttackRules.REASON_CHARGE, AttackRules.releaseProblem(WEAPON, 0.5D, 100.0D));
        assertEquals("", AttackRules.releaseProblem(WEAPON, 0.5D, 50.0D));
        assertEquals(StatResolver.REASON_NOT_A_WEAPON,
                AttackRules.releaseProblem(CritProfile.notEligible(StatResolver.REASON_NOT_A_WEAPON), 1.0D, 100.0D));
    }

    @Test
    void theHitChecksOnlyThePvpSetting() {
        assertEquals("", AttackRules.targetProblem(false, false));
        assertEquals("", AttackRules.targetProblem(true, true));
        assertEquals(AttackRules.REASON_PVP, AttackRules.targetProblem(true, false));
    }
}
