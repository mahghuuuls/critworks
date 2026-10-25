package com.mahghuuuls.critworks.combat;

import com.mahghuuuls.critworks.api.CritworksHitEvent;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.MinecraftForge;

/**
 * Owns what happens to one target of an attack: the multiplier applied before defenses, the
 * "successful hit" rule (the target lost health or absorption hearts), and the critical-hit event that
 * a successful critical hit fires, through the {@link ReactionGuard}. A shield block, a cancel, or a
 * hit that armor reduced to nothing is not a successful hit. Melee and arrows use the same rules.
 */
final class HitResolver {

    private final ReactionGuard guard;

    HitResolver(ReactionGuard guard) {
        this.guard = guard;
    }

    /** Whether critical-hit event handlers are running now, so new damage is a reaction. */
    boolean isReacting() {
        return guard.nextDepth() > 1;
    }

    /** Whether damage that starts now may crit and fire an event: always, except for reactions not allowed to chain. */
    boolean allowsCritNow() {
        return guard.allowsCritNow();
    }

    /** Called before armor; returns the damage the target should take from this hit. */
    float onHurt(TargetHit hit, EntityLivingBase target, float amount, boolean critical, double multiplier) {
        hit.damageBefore = amount;
        hit.absorptionBefore = target.getAbsorptionAmount();
        float result = critical ? (float) (amount * multiplier) : amount;
        hit.damageAfter = result;
        return result;
    }

    /** Called after armor and absorption, with the health damage that remains; returns whether the hit succeeded. */
    boolean onDamaged(TargetHit hit, EntityLivingBase target, float healthDamage) {
        float absorbed = hit.absorptionBefore - target.getAbsorptionAmount();
        hit.successful = healthDamage > 0.0F || absorbed > 0.0F;
        // Health is reduced after this event, so overkill is capped at the health the target still has.
        hit.damageDealt = Math.min(Math.max(healthDamage, 0.0F), target.getHealth()) + Math.max(absorbed, 0.0F);
        return hit.successful;
    }

    /** Fires the critical-hit event for one successful critical hit. */
    void fireEvent(TargetHit hit, EntityLivingBase attacker, EntityLivingBase target, ItemStack weapon, EnumHand hand,
                   CritworksHitEvent.Kind kind, double multiplier, boolean guaranteed) {
        final CritworksHitEvent event = new CritworksHitEvent(attacker, target, weapon.copy(), hand, kind, multiplier,
                guaranteed, hit.damageDealt, guard.nextDepth());
        hit.eventFired = true;
        guard.dispatch(event::getChainLimit, () -> MinecraftForge.EVENT_BUS.post(event));
    }
}
