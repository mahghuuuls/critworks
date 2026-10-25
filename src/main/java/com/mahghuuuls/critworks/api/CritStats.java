package com.mahghuuuls.critworks.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A read-only view of the stats Critworks resolves for a player and a stack right now: the same numbers an
 * attack, the inspection command, and tooltips use. Resolving changes nothing and uses no guarantee.
 */
public final class CritStats {

    private final boolean eligible;
    private final String eligibility;
    private final double baseChance;
    private final String baseChanceSource;
    private final double baseDamage;
    private final String baseDamageSource;
    private final double finalChance;
    private final double finalMultiplier;
    private final double damageCap;
    private final List<WeaponBonus> bonuses;

    /** Built by Critworks. */
    public CritStats(boolean eligible, String eligibility, double baseChance, String baseChanceSource, double baseDamage,
                     String baseDamageSource, double finalChance, double finalMultiplier, double damageCap,
                     List<WeaponBonus> bonuses) {
        this.eligible = eligible;
        this.eligibility = eligibility;
        this.baseChance = baseChance;
        this.baseChanceSource = baseChanceSource;
        this.baseDamage = baseDamage;
        this.baseDamageSource = baseDamageSource;
        this.finalChance = finalChance;
        this.finalMultiplier = finalMultiplier;
        this.damageCap = damageCap;
        this.bonuses = Collections.unmodifiableList(new ArrayList<WeaponBonus>(bonuses));
    }

    public boolean isEligible() {
        return eligible;
    }

    /** Why the stack can crit (for example "recognized weapon" or a pack rule), or why it cannot. */
    public String getEligibility() {
        return eligibility;
    }

    /** Base chance in percent; 0 when not eligible. */
    public double getBaseChance() {
        return baseChance;
    }

    /** Where the base chance comes from: a pack rule, an addon default, or the fallback. */
    public String getBaseChanceSource() {
        return baseChanceSource;
    }

    /** Base damage multiplier; 1.00 when not eligible. */
    public double getBaseDamage() {
        return baseDamage;
    }

    public String getBaseDamageSource() {
        return baseDamageSource;
    }

    /** Final chance in percent, after bonuses, 0 to 100. */
    public double getFinalChance() {
        return finalChance;
    }

    /** Final damage multiplier, after bonuses and the cap, at least 1.00. */
    public double getFinalMultiplier() {
        return finalMultiplier;
    }

    /** The damage cap of the side that resolved the stats, or positive infinity when there is none. */
    public double getDamageCap() {
        return damageCap;
    }

    /** Every bonus counted: the player's global bonuses and the stack's weapon-specific bonuses. */
    public List<WeaponBonus> getBonuses() {
        return bonuses;
    }
}
