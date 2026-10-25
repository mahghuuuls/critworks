package com.mahghuuuls.critworks.devtest;

import com.mahghuuuls.critworks.api.CritworksHitEvent;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Listens to the critical-hit event as an addon would: logs every field of every event, and on request
 * reacts to each event: 2 damage to the target as a plain reaction or as an opted-in chain, or a swing
 * of the attacker at the target. Public, because Forge calls listeners from its own generated classes.
 */
public final class DevTestEvents {

    static final float REACTION_DAMAGE = 2.0F;

    enum Reaction { OFF, DAMAGE, CHAIN, SWING }

    private static final Logger LOGGER = LogManager.getLogger("Critworks Dev Test");

    static Reaction reaction = Reaction.OFF;

    @SubscribeEvent
    public void onCrit(CritworksHitEvent event) {
        EntityLivingBase attacker = event.getAttacker();
        EntityLivingBase target = event.getTarget();
        LOGGER.info("[devtest] event attacker={} target={}#{} weapon={} hand={} kind={} multiplier={} guaranteed={} damage={} depth={} remote={}",
                attacker == null ? "none" : attacker.getName(), target.getName(), target.getEntityId(),
                event.getWeapon().isEmpty() ? "empty" : event.getWeapon().getItem().getRegistryName(),
                event.getHand() == null ? "none" : event.getHand().name().toLowerCase(java.util.Locale.ROOT),
                event.getKind(), String.format(java.util.Locale.ROOT, "%.2f", event.getMultiplier()),
                event.isGuaranteed(), String.format(java.util.Locale.ROOT, "%.2f", event.getDamage()), event.getDepth(),
                target.world.isRemote);
        if (reaction == Reaction.OFF || !(attacker instanceof EntityPlayer) || target.isDead) {
            return;
        }
        if (reaction == Reaction.CHAIN) {
            event.allowChain(CritworksHitEvent.MAX_CHAIN_DEPTH);
        }
        // Clear immunity time so the reaction reaches the target at all; the target just took the attack's hit.
        target.hurtResistantTime = 0;
        float before = target.getHealth() + target.getAbsorptionAmount();
        if (reaction == Reaction.SWING) {
            ((EntityPlayer) attacker).attackTargetEntityWithCurrentItem(target);
        } else {
            target.attackEntityFrom(DamageSource.causePlayerDamage((EntityPlayer) attacker), REACTION_DAMAGE);
        }
        float after = target.getHealth() + target.getAbsorptionAmount();
        LOGGER.info("[devtest] reaction {} depth={} dealt {} to {}#{}", reaction.name().toLowerCase(java.util.Locale.ROOT),
                event.getDepth(), String.format(java.util.Locale.ROOT, "%.2f", before - after), target.getName(),
                target.getEntityId());
    }
}
