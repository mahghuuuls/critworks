package com.mahghuuuls.critworks.core;

import com.mahghuuuls.critworks.bonus.Bonus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The resolved critical stats of one weapon for one player at one moment. Attacks, the inspection
 * command, tooltips, and the diagnostic log all read this one result, so they never disagree.
 * Charge, target, and per-attack rules are not part of it: they belong to the attack.
 */
public final class CritProfile {

    private final boolean eligible;
    private final String eligibilitySource;
    private final String ineligibleReason;
    private final double baseChance;
    private final String baseChanceSource;
    private final double baseMultiplier;
    private final String baseMultiplierSource;
    private final double finalChance;
    private final double finalMultiplier;
    private final double cap;
    private final List<Bonus> bonuses;

    private CritProfile(boolean eligible, String eligibilitySource, String ineligibleReason, double baseChance,
                        String baseChanceSource, double baseMultiplier, String baseMultiplierSource,
                        double finalChance, double finalMultiplier, double cap, List<Bonus> bonuses) {
        this.eligible = eligible;
        this.eligibilitySource = eligibilitySource;
        this.ineligibleReason = ineligibleReason;
        this.baseChance = baseChance;
        this.baseChanceSource = baseChanceSource;
        this.baseMultiplier = baseMultiplier;
        this.baseMultiplierSource = baseMultiplierSource;
        this.finalChance = finalChance;
        this.finalMultiplier = finalMultiplier;
        this.cap = cap;
        this.bonuses = Collections.unmodifiableList(new ArrayList<Bonus>(bonuses));
    }

    public static CritProfile notEligible(String reason) {
        return new CritProfile(false, "", reason, 0.0D, "", CritFormula.MIN_MULTIPLIER, "", 0.0D,
                CritFormula.MIN_MULTIPLIER, CritFormula.NO_CAP, Collections.<Bonus>emptyList());
    }

    /**
     * @param eligibilitySource why the weapon can crit, for example "recognized weapon" or the enabling rule
     */
    public static CritProfile eligible(String eligibilitySource, double baseChance, String baseChanceSource,
                                       double baseMultiplier, String baseMultiplierSource, double finalChance,
                                       double finalMultiplier, double cap, List<Bonus> bonuses) {
        return new CritProfile(true, eligibilitySource, "", baseChance, baseChanceSource, baseMultiplier,
                baseMultiplierSource, finalChance, finalMultiplier, cap, bonuses);
    }

    public boolean isEligible() {
        return eligible;
    }

    /** Why the weapon can crit; empty when not eligible. */
    public String getEligibilitySource() {
        return eligibilitySource;
    }

    /** Why the weapon cannot crit; empty when eligible. */
    public String getIneligibleReason() {
        return ineligibleReason;
    }

    public double getBaseChance() {
        return baseChance;
    }

    public String getBaseChanceSource() {
        return baseChanceSource;
    }

    public double getBaseMultiplier() {
        return baseMultiplier;
    }

    public String getBaseMultiplierSource() {
        return baseMultiplierSource;
    }

    public double getFinalChance() {
        return finalChance;
    }

    public double getFinalMultiplier() {
        return finalMultiplier;
    }

    /** Every bonus that counted, with its source; empty when not eligible. */
    public List<Bonus> getBonuses() {
        return bonuses;
    }

    /** The server maximum multiplier, or {@link CritFormula#NO_CAP}. */
    public double getCap() {
        return cap;
    }
}
