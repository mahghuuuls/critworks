package com.mahghuuuls.critworks.combat;

import net.minecraft.entity.EntityLivingBase;

/** What one attack did to one target: damage before and after the multiplier, and the outcome. */
final class TargetHit {

    final String targetName;
    final boolean primary;
    float damageBefore;
    float damageAfter;
    float absorptionBefore;
    float damageDealt;
    boolean critical;
    boolean successful;
    boolean eventFired;

    TargetHit(EntityLivingBase target, boolean primary) {
        this.targetName = target.getName() + "#" + target.getEntityId();
        this.primary = primary;
    }
}
