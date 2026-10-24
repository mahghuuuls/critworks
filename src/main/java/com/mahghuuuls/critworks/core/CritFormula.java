package com.mahghuuuls.critworks.core;

/**
 * The only owner of the critical chance and critical damage formulas, their limits, and the
 * finite-number rule. Inputs and outputs use the Critworks units: chance and increased bonuses in
 * percent, additional chance in percentage points, damage as a multiplier, additional damage in
 * multiplier units.
 */
public final class CritFormula {

    /** Cap value that means "no maximum". */
    public static final double NO_CAP = Double.POSITIVE_INFINITY;

    public static final double MIN_CHANCE = 0.0D;
    public static final double MAX_CHANCE = 100.0D;
    public static final double MIN_MULTIPLIER = 1.0D;

    private CritFormula() {
    }

    /**
     * Final chance = base x (1 + increased / 100) + additional, clamped to 0 to 100.
     * A base of 0 stays 0 under any increased bonus; an additional bonus can raise it.
     */
    public static double finalChance(double baseChance, double totalIncreased, double totalAdditional) {
        double chance = baseChance * (1.0D + totalIncreased / 100.0D) + totalAdditional;
        return clamp(chance, MIN_CHANCE, MAX_CHANCE);
    }

    /**
     * Final multiplier = 1 + (base - 1) x (1 + increased / 100) + additional, at least x1.00 and at
     * most the cap. Increased bonuses scale only the bonus part above x1.00.
     *
     * @param cap the server maximum, or {@link #NO_CAP}
     */
    public static double finalMultiplier(double baseMultiplier, double totalIncreased, double totalAdditional, double cap) {
        double bonusPart = (baseMultiplier - 1.0D) * (1.0D + totalIncreased / 100.0D);
        double multiplier = Math.max(MIN_MULTIPLIER, 1.0D + bonusPart + totalAdditional);
        return Math.min(multiplier, cap);
    }

    /** True when a value may take part in a calculation; NaN and infinities never do. */
    public static boolean isUsable(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
