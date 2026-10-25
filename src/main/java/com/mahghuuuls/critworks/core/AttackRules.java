package com.mahghuuuls.critworks.core;

/**
 * Owns the per-attack eligibility rules that do not depend on the weapon: charge, target kind, and the
 * PvP crit setting. The weapon's own eligibility comes from {@link CritProfile}.
 */
public final class AttackRules {

    public static final String REASON_CHARGE = "charge";
    public static final String REASON_TARGET = "target";
    public static final String REASON_PVP = "pvp crits off";
    public static final String REASON_OPTED_OUT = "opted out";

    private AttackRules() {
    }

    /**
     * @return the reason the attack cannot crit, or an empty string when it can
     */
    public static String problem(CritProfile profile, double chargeFraction, double thresholdPercent,
                                 boolean targetIsLiving, boolean targetIsPlayer, boolean pvpCrits) {
        String release = releaseProblem(profile, chargeFraction, thresholdPercent);
        if (!release.isEmpty()) {
            return release;
        }
        if (!targetIsLiving) {
            return REASON_TARGET;
        }
        return targetProblem(targetIsPlayer, pvpCrits);
    }

    /** The rules known when an attack starts: the weapon and the charge. An arrow is decided by these at release. */
    public static String releaseProblem(CritProfile profile, double chargeFraction, double thresholdPercent) {
        if (!profile.isEligible()) {
            return profile.getIneligibleReason();
        }
        if (!ChargeRule.meetsThreshold(chargeFraction, thresholdPercent)) {
            return REASON_CHARGE;
        }
        return "";
    }

    /** The rule checked at each hit with the current settings, also for an arrow fired earlier. */
    public static String targetProblem(boolean targetIsPlayer, boolean pvpCrits) {
        return targetIsPlayer && !pvpCrits ? REASON_PVP : "";
    }
}
