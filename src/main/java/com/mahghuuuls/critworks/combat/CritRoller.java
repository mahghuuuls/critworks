package com.mahghuuuls.critworks.combat;

import java.util.Random;

/** Owns the single critical roll of an attack. */
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
}
