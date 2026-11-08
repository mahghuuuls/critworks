package com.mahghuuuls.critworks.integration.crafttweaker;

import com.mahghuuuls.critworks.api.CritworksHitEvent;
import net.minecraft.util.EnumHand;

/**
 * The words scripts read and write for attack kinds and hands. They are part of the script contract, so a
 * renamed Java constant must not change them. No CraftTweaker type here, so the mapping is testable alone.
 */
public final class ScriptNames {

    public static final String HAND_MAIN = "main";
    public static final String HAND_OFF = "off";

    private ScriptNames() {
    }

    /** {@code "melee"}, {@code "sweep"}, {@code "arrow"}, or {@code "integration"}. */
    public static String kind(CritworksHitEvent.Kind kind) {
        switch (kind) {
            case MELEE_PRIMARY:
                return "melee";
            case MELEE_SWEEP:
                return "sweep";
            case PROJECTILE:
                return "arrow";
            case INTEGRATION:
                return "integration";
            default:
                throw new IllegalArgumentException(String.valueOf(kind));
        }
    }

    /** {@code "main"} or {@code "off"}. */
    public static String hand(EnumHand hand) {
        return hand == EnumHand.OFF_HAND ? HAND_OFF : HAND_MAIN;
    }
}
