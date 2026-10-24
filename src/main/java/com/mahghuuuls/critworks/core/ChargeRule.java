package com.mahghuuuls.critworks.core;

/**
 * Owns the charge rule: an attack can crit only when its charge (melee cooldown recovery or bow draw)
 * reaches the configured threshold. Charge only allows a crit; it never makes one.
 */
public final class ChargeRule {

    /** Float charge values times 100 carry about 6e-6 of rounding; this keeps a full charge at 100%. */
    private static final double TOLERANCE = 1.0E-4D;

    private ChargeRule() {
    }

    /**
     * @param chargeFraction charge from 0 to 1
     * @param thresholdPercent threshold from 0 to 100
     */
    public static boolean meetsThreshold(double chargeFraction, double thresholdPercent) {
        return chargeFraction * 100.0D + TOLERANCE >= thresholdPercent;
    }
}
