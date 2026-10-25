package com.mahghuuuls.critworks.combat;

import com.mahghuuuls.critworks.state.PlayerCritState;

import java.util.Random;

/**
 * Owns the single decision of an eligible attack: critical through the player's next guarantee when one
 * is pending, otherwise through one roll against the final chance. Which guarantee comes next is the
 * rule of {@link PlayerCritState}; when it is used is up to the attack (melee on a successful hit,
 * arrows at release).
 */
final class CritRoller {

    private CritRoller() {
    }

    /** A roll in [0, 100); the attack is critical when the roll is below the final chance. */
    static double roll(Random random) {
        return random.nextDouble() * 100.0D;
    }

    static boolean isCritical(double roll, double finalChance) {
        return roll < finalChance;
    }

    /** Decides an eligible attack. Changes nothing: the attack uses the guarantee itself. */
    static Decision decide(PlayerCritState guarantees, double finalChance, Random random) {
        String guarantee = guarantees == null ? null : guarantees.nextId();
        if (guarantee != null) {
            return new Decision(true, Double.NaN, guarantee);
        }
        double roll = roll(random);
        return new Decision(isCritical(roll, finalChance), roll, null);
    }

    /** The result of an eligible attack: critical or not, the roll (NaN when guaranteed), and the guarantee id. */
    static final class Decision {
        final boolean critical;
        final double roll;
        final String guaranteeId;

        Decision(boolean critical, double roll, String guaranteeId) {
            this.critical = critical;
            this.roll = roll;
            this.guaranteeId = guaranteeId;
        }

        boolean isGuaranteed() {
            return guaranteeId != null;
        }
    }
}
