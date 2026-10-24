package com.mahghuuuls.critworks.combat;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Owns what happens to one target of an attack: the multiplier applied before defenses, and the
 * "successful hit" rule (the target lost health or absorption hearts). A shield block, a cancel, or a
 * hit that armor reduced to nothing is not a successful hit.
 */
final class HitResolver {

    private final CritFeedback feedback;

    HitResolver(CritFeedback feedback) {
        this.feedback = feedback;
    }

    /** Called before armor; returns the damage the target should take from this hit. */
    float onHurt(TargetHit hit, EntityLivingBase target, float amount, boolean critical, double multiplier) {
        hit.damageBefore = amount;
        hit.absorptionBefore = target.getAbsorptionAmount();
        float result = critical ? (float) (amount * multiplier) : amount;
        hit.damageAfter = result;
        return result;
    }

    /** Called after armor and absorption, with the health damage that remains. */
    void onDamaged(TargetHit hit, EntityLivingBase target, float healthDamage, boolean critical,
                   EntityPlayer attacker, SoundClaim soundClaim) {
        float absorbed = hit.absorptionBefore - target.getAbsorptionAmount();
        hit.successful = healthDamage > 0.0F || absorbed > 0.0F;
        if (critical && hit.successful) {
            feedback.particles(attacker, target);
            if (soundClaim.claim()) {
                feedback.sound(attacker);
            }
        }
    }

    /** Lets the caller decide which hit of an attack plays the attack's one sound. */
    interface SoundClaim {
        boolean claim();
    }
}
