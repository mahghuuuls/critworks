package com.mahghuuuls.critworks.core;

/**
 * Owns the per-attack eligibility rules that do not depend on the weapon: charge, target kind, and the
 * PvP crit setting. The weapon's own eligibility comes from {@link CritProfile}.
 */
public final class AttackRules {

    public static final String REASON_CHARGE = "charge";
    public static final String REASON_TARGET = "target";
    public static final String REASON_PVP = "pvp crits off";

    private AttackRules() {
    }

    /**
     * @return the reason the attack cannot crit, or an empty string when it can
     */
    public static String problem(CritProfile profile, double chargeFraction, double thresholdPercent,
                                 boolean targetIsLiving, boolean targetIsPlayer, boolean pvpCrits) {
        if (!profile.isEligible()) {
            return profile.getIneligibleReason();
        }
        if (!ChargeRule.meetsThreshold(chargeFraction, thresholdPercent)) {
            return REASON_CHARGE;
        }
        if (!targetIsLiving) {
            return REASON_TARGET;
        }
        if (targetIsPlayer && !pvpCrits) {
            return REASON_PVP;
        }
        return "";
    }
}
