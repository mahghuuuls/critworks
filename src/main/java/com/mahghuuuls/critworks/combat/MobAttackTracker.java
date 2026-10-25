package com.mahghuuuls.critworks.combat;

import com.mahghuuuls.critworks.api.CritworksHitEvent;
import com.mahghuuuls.critworks.core.AttackKind;
import com.mahghuuuls.critworks.core.AttackRules;
import com.mahghuuuls.critworks.core.AttackerPolicies;
import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.core.CritProfile;
import com.mahghuuuls.critworks.core.StatResolver;
import com.mahghuuuls.critworks.diagnostics.DiagnosticLog;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Melee hits of attackers other than players, when an addon's attacker policy allows them (REQ-015). A
 * mob has no swing to follow, so each hit is its own attack: decided at its hurt event with the stack in
 * the attacker's main hand, at full charge, with no bonuses except the stack's weapon-specific ones and no
 * guarantees. Only direct damage of type "mob" counts; arrows of other attackers are not supported. The
 * PvP crit setting applies to their hits on players too: with it off, crits never happen against players.
 */
public final class MobAttackTracker {

    private static final String MOB_DAMAGE = "mob";

    private final StatResolver resolver;
    private final CritSettings settings;
    private final AttackerPolicies policies;
    private final CritFeedback feedback;
    private final DiagnosticLog diagnostics;
    private final HitResolver hitResolver;
    private final Map<EntityLivingBase, MobHit> hitsInProgress = new IdentityHashMap<EntityLivingBase, MobHit>();

    MobAttackTracker(StatResolver resolver, CritSettings settings, AttackerPolicies policies, CritFeedback feedback,
                     DiagnosticLog diagnostics, HitResolver hitResolver) {
        this.resolver = resolver;
        this.settings = settings;
        this.policies = policies;
        this.feedback = feedback;
        this.diagnostics = diagnostics;
        this.hitResolver = hitResolver;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onHurt(LivingHurtEvent event) {
        EntityLivingBase target = event.getEntityLiving();
        if (target.world.isRemote || policies.isEmpty()) {
            return;
        }
        EntityLivingBase attacker = mobAttacker(event.getSource());
        if (attacker == null || !policies.allows(attacker)) {
            return;
        }
        if (hitResolver.isReacting() && !hitResolver.allowsCritNow()) {
            return;
        }
        ItemStack weapon = attacker.getHeldItemMainhand();
        CritProfile profile = resolver.resolve(null, weapon, EnumHand.MAIN_HAND, AttackKind.MELEE);
        boolean allowed = profile.isEligible()
                && AttackRules.targetProblem(target instanceof EntityPlayer, settings.isPvpCrits()).isEmpty();
        CritRoller.Decision decision = allowed
                ? CritRoller.decide(null, profile.getFinalChance(), attacker.world.rand) : null;
        boolean critical = decision != null && decision.critical;
        TargetHit hit = new TargetHit(target, true);
        hit.critical = critical;
        event.setAmount(hitResolver.onHurt(hit, target, event.getAmount(), critical, profile.getFinalMultiplier()));
        hitsInProgress.put(target, new MobHit(attacker, weapon.copy(), profile, decision, hit));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDamaged(LivingDamageEvent event) {
        EntityLivingBase target = event.getEntityLiving();
        if (target.world.isRemote || hitsInProgress.isEmpty()) {
            return;
        }
        MobHit mobHit = hitsInProgress.remove(target);
        if (mobHit == null || mobHit.attacker != mobAttacker(event.getSource())) {
            return;
        }
        TargetHit hit = mobHit.hit;
        if (hitResolver.onDamaged(hit, target, event.getAmount()) && hit.critical) {
            feedback.particlesOn(target);
            feedback.soundAt(mobHit.attacker);
            hitResolver.fireEvent(hit, mobHit.attacker, target, mobHit.weapon, EnumHand.MAIN_HAND,
                    CritworksHitEvent.Kind.MELEE_PRIMARY, mobHit.profile.getFinalMultiplier(), false);
        }
        if (diagnostics.isEnabled()) {
            diagnostics.write(describe(mobHit));
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !hitsInProgress.isEmpty()) {
            hitsInProgress.clear();
        }
    }

    void onServerStopping() {
        hitsInProgress.clear();
    }

    private static EntityLivingBase mobAttacker(DamageSource source) {
        if (!MOB_DAMAGE.equals(source.getDamageType())) {
            return null;
        }
        Entity attacker = source.getTrueSource();
        if (!(attacker instanceof EntityLivingBase) || attacker instanceof EntityPlayer
                || attacker != source.getImmediateSource()) {
            return null;
        }
        return (EntityLivingBase) attacker;
    }

    private DiagnosticLog.Line describe(MobHit mobHit) {
        CritProfile profile = mobHit.profile;
        TargetHit hit = mobHit.hit;
        String result = !profile.isEligible() ? "\"not eligible: " + profile.getIneligibleReason() + "\""
                : mobHit.decision != null && mobHit.decision.critical ? "critical" : "normal";
        return diagnostics.line("mob")
                .add("attacker", mobHit.attacker.getName() + "#" + mobHit.attacker.getEntityId())
                .add("weapon", mobHit.weapon.isEmpty() ? "empty" : String.valueOf(mobHit.weapon.getItem().getRegistryName()))
                .add("chance", profile.isEligible() ? profile.getFinalChance() : Double.NaN)
                .add("roll", mobHit.decision != null ? mobHit.decision.roll : Double.NaN)
                .add("result", result)
                .add("by", "roll")
                .add("multiplier", profile.isEligible() ? profile.getFinalMultiplier() : Double.NaN)
                .add("targets", "[" + hit.targetName + ":primary:" + DiagnosticLog.Line.format(hit.damageBefore) + "->"
                        + DiagnosticLog.Line.format(hit.damageAfter) + (hit.successful ? ":hit" : ":nohit")
                        + (hit.eventFired ? ":event" : "") + "]");
    }

    /** One mob hit between its hurt and its damage event. */
    private static final class MobHit {
        final EntityLivingBase attacker;
        final ItemStack weapon;
        final CritProfile profile;
        final CritRoller.Decision decision;
        final TargetHit hit;

        MobHit(EntityLivingBase attacker, ItemStack weapon, CritProfile profile, CritRoller.Decision decision,
               TargetHit hit) {
            this.attacker = attacker;
            this.weapon = weapon;
            this.profile = profile;
            this.decision = decision;
            this.hit = hit;
        }
    }
}
