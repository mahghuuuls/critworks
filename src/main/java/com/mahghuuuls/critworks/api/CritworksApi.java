package com.mahghuuuls.critworks.api;

import net.minecraft.entity.player.EntityPlayer;

/**
 * The Critworks API for addons. Call it on the logical server unless a method says otherwise. Critworks
 * binds it during its pre-initialization; declare {@code after:critworks} (or a required dependency) and
 * use it from your own pre-initialization or later.
 */
public final class CritworksApi {

    /** API version, raised when methods are added. */
    public static final String API_VERSION = "1.0";

    private static Backend backend;

    private CritworksApi() {
    }

    /**
     * Gives a player a global bonus: it counts for every eligible attack of that player, both hands,
     * melee and ranged. Setting the same source and stat again replaces the old amount; an amount of 0
     * removes it. The bonus is saved with the player. Minecraft does not keep it on the new player after
     * death or after leaving the End, so your mod decides when the bonus starts and ends, including then.
     * A call on the client side is ignored.
     *
     * @param sourceId a stable id for your bonus, such as "mymod:rage"; shown as its source
     * @throws IllegalArgumentException when the amount is not a finite number
     */
    public static void setGlobalBonus(EntityPlayer player, String sourceId, CritStat stat, double amount) {
        requireSource(sourceId);
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            throw new IllegalArgumentException("amount must be a finite number, got " + amount);
        }
        backend().setGlobalBonus(player, sourceId, stat, amount);
    }

    /** Removes a global bonus set with {@link #setGlobalBonus}. */
    public static void removeGlobalBonus(EntityPlayer player, String sourceId, CritStat stat) {
        requireSource(sourceId);
        backend().removeGlobalBonus(player, sourceId, stat);
    }

    /** True once Critworks has bound the API; useful for an optional dependency. */
    public static boolean isBound() {
        return backend != null;
    }

    private static Backend backend() {
        if (backend == null) {
            throw new IllegalStateException("Critworks API used before Critworks pre-initialization");
        }
        return backend;
    }

    private static void requireSource(String sourceId) {
        if (sourceId == null || sourceId.trim().isEmpty()) {
            throw new IllegalArgumentException("sourceId must not be empty");
        }
    }

    /** Critworks' implementation of the API. Not for addons. */
    public interface Backend {
        void setGlobalBonus(EntityPlayer player, String sourceId, CritStat stat, double amount);

        void removeGlobalBonus(EntityPlayer player, String sourceId, CritStat stat);
    }

    /** Called once by Critworks. Not for addons. */
    public static void bind(Backend implementation) {
        if (backend != null) {
            throw new IllegalStateException("Critworks API is already bound; only Critworks may call bind");
        }
        backend = implementation;
    }
}
