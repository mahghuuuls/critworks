package com.mahghuuuls.critworks.api;

/**
 * A guarantee: the player's next eligible attack is critical. Build one with {@link #of(String)} and grant
 * it with {@link CritworksApi#grantGuarantee}. A guarantee with a duration counts down only while the
 * player is online; one without a duration waits until it is used or removed. Death removes it unless
 * {@link #keptOnDeath()} is set. {@link CritworksApi#getGuarantees} returns specs whose duration is the
 * time left.
 */
public final class GuaranteeSpec {

    /** The duration of a guarantee with no time limit. */
    public static final int NO_DURATION = -1;

    private final String id;
    private final int durationTicks;
    private final boolean keepOnDeath;

    private GuaranteeSpec(String id, int durationTicks, boolean keepOnDeath) {
        this.id = id;
        this.durationTicks = durationTicks;
        this.keepOnDeath = keepOnDeath;
    }

    /**
     * A guarantee with no time limit that death removes.
     *
     * @param id a stable id such as "mymod:focus"; granting the same id again replaces the guarantee
     */
    public static GuaranteeSpec of(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("guarantee id must not be empty");
        }
        return new GuaranteeSpec(id, NO_DURATION, false);
    }

    /** The same guarantee, lasting {@code ticks} online ticks (20 per second). */
    public GuaranteeSpec lasting(int ticks) {
        if (ticks <= 0) {
            throw new IllegalArgumentException("guarantee duration must be at least 1 tick, got " + ticks);
        }
        return new GuaranteeSpec(id, ticks, keepOnDeath);
    }

    /** The same guarantee, kept when the player dies. */
    public GuaranteeSpec keptOnDeath() {
        return new GuaranteeSpec(id, durationTicks, true);
    }

    public String getId() {
        return id;
    }

    /** Ticks it lasts (or has left), or {@link #NO_DURATION}. */
    public int getDurationTicks() {
        return durationTicks;
    }

    public boolean hasDuration() {
        return durationTicks != NO_DURATION;
    }

    public boolean isKeptOnDeath() {
        return keepOnDeath;
    }
}
