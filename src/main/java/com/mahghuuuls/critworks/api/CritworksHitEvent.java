package com.mahghuuuls.critworks.api;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * Fired on {@code MinecraftForge.EVENT_BUS}, on the logical server only, once for each target that a
 * critical attack hits successfully: the target lost health or absorption hearts. A miss, a cancel, a
 * shield block, a hit stopped by immunity time, or a hit armor reduces to nothing fires no event. It
 * cannot be canceled. It fires while the hit is applied: absorption hearts are already taken, and the
 * target's health is reduced right after the handlers return, so {@code getTarget().getHealth()} still
 * shows the health before the hit.
 *
 * <p>Damage a handler deals while the event runs is a reaction: it cannot crit and fires no event,
 * unless {@link #allowChain(int)} was called first. A swing a handler makes the attacker do opens no
 * attack of its own; its damage counts as such a reaction. A chain stops after {@link #MAX_CHAIN_DEPTH} levels.
 */
public class CritworksHitEvent extends Event {

    /** The deepest level an opted-in chain of reactions reaches; level 1 is the attack's own hit. */
    public static final int MAX_CHAIN_DEPTH = 4;

    /** What kind of attack made the hit. */
    public enum Kind {
        /** The target a melee swing aimed at. */
        MELEE_PRIMARY,
        /** A target hit by the sweep of a melee swing. */
        MELEE_SWEEP,
        /** A target hit by an arrow. */
        PROJECTILE,
        /** A target hit by an attack of another combat system through an attack integration. */
        INTEGRATION
    }

    private final EntityLivingBase attacker;
    private final EntityLivingBase target;
    private final ItemStack weapon;
    private final EnumHand hand;
    private final Kind kind;
    private final double multiplier;
    private final boolean guaranteed;
    private final float damage;
    private final int depth;
    private int chainLimit;

    public CritworksHitEvent(EntityLivingBase attacker, EntityLivingBase target, ItemStack weapon, EnumHand hand,
                             Kind kind, double multiplier, boolean guaranteed, float damage, int depth) {
        this.attacker = attacker;
        this.target = target;
        this.weapon = weapon;
        this.hand = hand;
        this.kind = kind;
        this.multiplier = multiplier;
        this.guaranteed = guaranteed;
        this.damage = damage;
        this.depth = depth;
    }

    /** The attacker, or null for an arrow whose shooter is unknown (for example after the world was reloaded). */
    public EntityLivingBase getAttacker() {
        return attacker;
    }

    public EntityLivingBase getTarget() {
        return target;
    }

    /**
     * A copy of the attacking stack: the melee weapon or the bow. Empty for an arrow loaded from a save,
     * whose bow is not saved with it.
     */
    public ItemStack getWeapon() {
        return weapon;
    }

    public EnumHand getHand() {
        return hand;
    }

    public Kind getKind() {
        return kind;
    }

    /** The final damage multiplier the hit used, 1.00 or more. */
    public double getMultiplier() {
        return multiplier;
    }

    /** True when a guarantee made the attack critical, false when the random roll did. */
    public boolean isGuaranteed() {
        return guaranteed;
    }

    /** The damage the target takes from this hit: health lost (at most its remaining health) plus absorption hearts lost. */
    public float getDamage() {
        return damage;
    }

    /** 1 for the attack's own hit; 2 to {@link #MAX_CHAIN_DEPTH} for hits by opted-in reactions. */
    public int getDepth() {
        return depth;
    }

    /**
     * Lets damage that handlers deal from now on, while this event runs, take part in the attack: it can
     * crit and fire this event again, one level deeper. It applies to this event, so damage from every
     * handler that runs after the call counts. A reaction counts for the attack whose damage source it
     * uses: player damage for that player's melee swing. {@code levels} is how deep the chain may reach
     * counting from the attack's own hit, at most {@link #MAX_CHAIN_DEPTH}. Call it again from the deeper
     * event to continue the chain.
     */
    public void allowChain(int levels) {
        chainLimit = Math.max(chainLimit, Math.min(levels, MAX_CHAIN_DEPTH));
    }

    /** The deepest level reactions to this event may reach: 0 until {@link #allowChain(int)} is called. */
    public int getChainLimit() {
        return chainLimit;
    }
}
