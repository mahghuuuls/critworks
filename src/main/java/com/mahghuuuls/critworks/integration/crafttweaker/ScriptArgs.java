package com.mahghuuuls.critworks.integration.crafttweaker;

import com.mahghuuuls.critworks.api.CritStat;
import com.mahghuuuls.critworks.api.GuaranteeSpec;
import net.minecraft.util.EnumHand;

/**
 * Checks the values a script passes to the player calls and says what is wrong in the script author's terms. No
 * CraftTweaker type here, so every rule and message is testable alone. Each check throws
 * {@link IllegalArgumentException} with the message the script log shows.
 */
final class ScriptArgs {

    private ScriptArgs() {
    }

    /** A guarantee id or a bonus source: any text that is not empty or blank. */
    static String id(String value, String what) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(what + " must not be empty");
        }
        return value;
    }

    /** A guarantee duration: 0 for no time limit, or a number of ticks. */
    static int ticks(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must be 0 (no time limit) or more, got " + ticks);
        }
        return ticks;
    }

    /** A guarantee from a script's arguments: 0 ticks means no time limit. */
    static GuaranteeSpec guarantee(String id, int ticks, boolean keptOnDeath) {
        GuaranteeSpec spec = GuaranteeSpec.of(id(id, "the guarantee id"));
        if (ticks(ticks) > 0) {
            spec = spec.lasting(ticks);
        }
        return keptOnDeath ? spec.keptOnDeath() : spec;
    }

    /** One of the four Critworks attribute names, such as {@code critworks.additionalCritChance}. */
    static CritStat stat(String name) {
        for (CritStat stat : CritStat.values()) {
            if (stat.getAttributeName().equals(name)) {
                return stat;
            }
        }
        StringBuilder names = new StringBuilder();
        for (CritStat stat : CritStat.values()) {
            names.append(names.length() == 0 ? "" : ", ").append(stat.getAttributeName());
        }
        throw new IllegalArgumentException("unknown stat \"" + name + "\"; use one of " + names);
    }

    /** {@code "main"} (also when left out) or {@code "off"}. */
    static EnumHand hand(String hand) {
        if (hand == null || ScriptNames.HAND_MAIN.equals(hand)) {
            return EnumHand.MAIN_HAND;
        }
        if (ScriptNames.HAND_OFF.equals(hand)) {
            return EnumHand.OFF_HAND;
        }
        throw new IllegalArgumentException("hand must be \"main\" or \"off\", got \"" + hand + "\"");
    }

    /** A bonus amount in the stat's own unit: any finite number. */
    static double amount(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            throw new IllegalArgumentException("amount must be a finite number, got " + amount);
        }
        return amount;
    }
}
