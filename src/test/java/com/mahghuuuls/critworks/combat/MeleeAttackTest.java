package com.mahghuuuls.critworks.combat;

import com.mahghuuuls.critworks.bonus.Bonus;
import com.mahghuuuls.critworks.core.CritFormula;
import com.mahghuuuls.critworks.core.CritProfile;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A guaranteed swing uses its guarantee once, and only after a successful hit claims it. */
class MeleeAttackTest {

    private static final CritProfile PROFILE = CritProfile.eligible("recognized weapon", 0.0D, "fallback", 1.5D,
            "fallback", 0.0D, 1.5D, CritFormula.NO_CAP, Collections.<Bonus>emptyList());

    @Test
    void aGuaranteedSwingClaimsItsGuaranteeOnce() {
        MeleeAttack attack = new MeleeAttack(null, null, null, 1.0F, null);
        attack.resolve(PROFILE, 1.0F, "", new CritRoller.Decision(true, Double.NaN, "a"), false);
        assertTrue(attack.isCritical());
        assertTrue(attack.isGuaranteed());
        assertFalse(attack.isGuaranteeUsed(), "nothing is used before a successful hit");
        assertTrue(attack.claimGuarantee());
        assertFalse(attack.claimGuarantee(), "a sweep's later hits use nothing more");
        assertTrue(attack.isGuaranteeUsed());
    }

    @Test
    void aNotEligibleSwingHasNoGuarantee() {
        MeleeAttack attack = new MeleeAttack(null, null, null, 0.5F, null);
        attack.resolve(PROFILE, 0.5F, "charge", null, false);
        assertFalse(attack.isCritical());
        assertFalse(attack.isGuaranteed());
    }
}
