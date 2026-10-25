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
 * One melee swing: the hand and weapon read when it started, its charge, its single critical result,
 * and what happened to each target it hit. Lives for at most one server tick.
 */
final class MeleeAttack {

    final EntityPlayer attacker;
    final ItemStack weapon;
    final EnumHand hand;
    final Entity primaryTarget;

    private float charge;
    private boolean resolved;
    private CritProfile profile;
    private String ineligibleReason = "";
    private double roll = Double.NaN;
    private boolean critical;
    private boolean vanillaCritDenied;
    private boolean soundPlayed;
    private String guaranteeId;
    private boolean guaranteeUsed;
    /** The attack integration that opened this attack, or null for a player's own swing. */
    private String integrationId;
    /** True while one of this swing's hits is between its hurt and its damage event. */
    private boolean inHit;
    private final Map<Integer, TargetHit> hits = new LinkedHashMap<Integer, TargetHit>();
    private final Map<Integer, TargetHit> reactionHits = new LinkedHashMap<Integer, TargetHit>();

    MeleeAttack(EntityPlayer attacker, ItemStack weapon, EnumHand hand, float charge, Entity primaryTarget) {
        this.attacker = attacker;
        this.weapon = weapon;
        this.hand = hand;
        this.charge = charge;
        this.primaryTarget = primaryTarget;
    }

    /**
     * @param charge NaN when the attack's combat mod gives the charge with its crit event instead
     */
    void resolve(CritProfile profile, float charge, String ineligibleReason, CritRoller.Decision decision,
                 boolean vanillaCritDenied) {
        this.resolved = true;
        this.charge = charge;
        this.profile = profile;
        this.ineligibleReason = ineligibleReason;
        if (decision != null) {
            this.roll = decision.roll;
            this.critical = decision.critical;
            this.guaranteeId = decision.guaranteeId;
        }
        this.vanillaCritDenied = vanillaCritDenied;
    }

    void setInHit(boolean inHit) {
        this.inHit = inHit;
    }

    boolean isInHit() {
        return inHit;
    }

    void setIntegration(String id) {
        this.integrationId = id;
    }

    boolean isIntegration() {
        return integrationId != null;
    }

    String getIntegrationId() {
        return integrationId;
    }

    boolean isGuaranteed() {
        return guaranteeId != null;
    }

    String getGuaranteeId() {
        return guaranteeId;
    }

    boolean isGuaranteeUsed() {
        return guaranteeUsed;
    }

    /** Returns true the first time only, so a swing uses its guarantee once. */
    boolean claimGuarantee() {
        if (guaranteeUsed) {
            return false;
        }
        guaranteeUsed = true;
        return true;
    }

    /** The charge (0 to 1) the attack was decided with; NaN until a combat mod's crit event gives it. */
    float getCharge() {
        return charge;
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

    /** The hit of an opted-in reaction on a target, kept apart from the attack's own hit on it. */
    TargetHit reactionHitFor(EntityLivingBase target) {
        TargetHit hit = new TargetHit(target, target == primaryTarget);
        reactionHits.put(target.getEntityId(), hit);
        return hit;
    }

    TargetHit takeReactionHit(EntityLivingBase target) {
        return reactionHits.remove(target.getEntityId());
    }

    TargetHit existingHit(EntityLivingBase target) {
        return hits.get(target.getEntityId());
    }

    Collection<TargetHit> hits() {
        return hits.values();
    }
}
