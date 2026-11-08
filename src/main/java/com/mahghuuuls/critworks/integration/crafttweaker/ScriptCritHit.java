package com.mahghuuuls.critworks.integration.crafttweaker;

import com.mahghuuuls.critworks.api.CritworksHitEvent;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.entity.IEntityLivingBase;
import crafttweaker.api.item.IItemStack;
import crafttweaker.api.minecraft.CraftTweakerMC;
import crafttweaker.api.player.IPlayer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenGetter;

/**
 * One critical hit as a script sees it: {@code event.kind}, {@code event.multiplier}, and the rest. The numbers,
 * words, and the weapon copy are taken when the event is built; the attacker, player, and target are the live
 * entities, so a script can act on them. Read-only: a script cannot cancel or change the hit.
 */
@ZenRegister
@ZenClass("mods.critworks.CritHitEvent")
public final class ScriptCritHit {

    private final IEntityLivingBase attacker;
    private final IPlayer player;
    private final IEntityLivingBase target;
    private final IItemStack weapon;
    private final String hand;
    private final String kind;
    private final double multiplier;
    private final boolean guaranteed;
    private final float damage;
    private final int depth;

    private ScriptCritHit(IEntityLivingBase attacker, IPlayer player, IEntityLivingBase target, IItemStack weapon,
                          String hand, String kind, double multiplier, boolean guaranteed, float damage, int depth) {
        this.attacker = attacker;
        this.player = player;
        this.target = target;
        this.weapon = weapon;
        this.hand = hand;
        this.kind = kind;
        this.multiplier = multiplier;
        this.guaranteed = guaranteed;
        this.damage = damage;
        this.depth = depth;
    }

    static ScriptCritHit of(CritworksHitEvent event) {
        EntityLivingBase attacker = event.getAttacker();
        IPlayer player = attacker instanceof EntityPlayer ? CraftTweakerMC.getIPlayer((EntityPlayer) attacker) : null;
        return new ScriptCritHit(
                attacker == null ? null : CraftTweakerMC.getIEntityLivingBase(attacker),
                player,
                CraftTweakerMC.getIEntityLivingBase(event.getTarget()),
                CraftTweakerMC.getIItemStack(event.getWeapon().copy()),
                ScriptNames.hand(event.getHand()),
                ScriptNames.kind(event.getKind()),
                event.getMultiplier(),
                event.isGuaranteed(),
                event.getDamage(),
                event.getDepth());
    }

    /** The attacker, or null when it is unknown (an arrow whose shooter is gone). */
    @ZenGetter("attacker")
    public IEntityLivingBase getAttacker() {
        return attacker;
    }

    /** The attacker as a player, or null when the attacker is not a player. */
    @ZenGetter("player")
    public IPlayer getPlayer() {
        return player;
    }

    @ZenGetter("target")
    public IEntityLivingBase getTarget() {
        return target;
    }

    /** The melee weapon or the bow, or null when there was none (an arrow loaded from a save). */
    @ZenGetter("weapon")
    public IItemStack getWeapon() {
        return weapon;
    }

    /** {@code "main"} or {@code "off"}. */
    @ZenGetter("hand")
    public String getHand() {
        return hand;
    }

    /** {@code "melee"}, {@code "sweep"}, {@code "arrow"}, or {@code "integration"}. */
    @ZenGetter("kind")
    public String getKind() {
        return kind;
    }

    /** The final damage multiplier the hit used, 1.00 or more. */
    @ZenGetter("multiplier")
    public double getMultiplier() {
        return multiplier;
    }

    /** True when a guarantee made the attack critical, false when the roll did. */
    @ZenGetter("guaranteed")
    public boolean isGuaranteed() {
        return guaranteed;
    }

    /** Health lost (at most the remaining health) plus absorption lost, in health points. */
    @ZenGetter("damage")
    public float getDamage() {
        return damage;
    }

    /** 1 for the attack's own hit; higher for hits that Java addons allowed to chain. */
    @ZenGetter("depth")
    public int getDepth() {
        return depth;
    }
}
