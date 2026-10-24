package com.mahghuuuls.critworks.combat;

import com.mahghuuuls.critworks.core.CritProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One melee swing: the weapon and charge read when it started, its single critical result, and what
 * happened to each target it hit. Lives for at most one server tick.
 */
final class MeleeAttack {

    final EntityPlayer attacker;
    final ItemStack weapon;
    final EnumHand hand;
    final float charge;
    final Entity primaryTarget;

    private boolean resolved;
    private CritProfile profile;
    private String ineligibleReason = "";
    private double roll = Double.NaN;
    private boolean critical;
    private boolean vanillaCritDenied;
    private boolean soundPlayed;
    private final Map<Integer, TargetHit> hits = new LinkedHashMap<Integer, TargetHit>();

    MeleeAttack(EntityPlayer attacker, ItemStack weapon, EnumHand hand, float charge, Entity primaryTarget) {
        this.attacker = attacker;
        this.weapon = weapon;
        this.hand = hand;
        this.charge = charge;
        this.primaryTarget = primaryTarget;
    }

    void resolve(CritProfile profile, String ineligibleReason, double roll, boolean critical, boolean vanillaCritDenied) {
        this.resolved = true;
        this.profile = profile;
        this.ineligibleReason = ineligibleReason;
        this.roll = roll;
        this.critical = critical;
        this.vanillaCritDenied = vanillaCritDenied;
    }

    boolean isResolved() {
        return resolved;
    }

    boolean isCritical() {
        return critical;
    }

    boolean isEligible() {
        return ineligibleReason.isEmpty();
    }

    String getIneligibleReason() {
        return ineligibleReason;
    }

    CritProfile getProfile() {
        return profile;
    }

    double getRoll() {
        return roll;
    }

    boolean isVanillaCritDenied() {
        return vanillaCritDenied;
    }

    /** Returns true the first time only, so an attack plays its sound once. */
    boolean claimSound() {
        if (soundPlayed) {
            return false;
        }
        soundPlayed = true;
        return true;
    }

    TargetHit hitFor(EntityLivingBase target) {
        TargetHit hit = hits.get(target.getEntityId());
        if (hit == null) {
            hit = new TargetHit(target, target == primaryTarget);
            hits.put(target.getEntityId(), hit);
        }
        return hit;
    }

    TargetHit existingHit(EntityLivingBase target) {
        return hits.get(target.getEntityId());
    }

    Collection<TargetHit> hits() {
        return hits.values();
    }
}
