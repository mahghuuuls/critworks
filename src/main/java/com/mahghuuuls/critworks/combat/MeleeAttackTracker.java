package com.mahghuuuls.critworks.combat;

import com.mahghuuuls.critworks.api.CritworksHitEvent;
import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.core.AttackKind;
import com.mahghuuuls.critworks.core.AttackRules;
import com.mahghuuuls.critworks.core.CritProfile;
import com.mahghuuuls.critworks.core.StatResolver;
import com.mahghuuuls.critworks.diagnostics.DiagnosticLog;
import com.mahghuuuls.critworks.state.PlayerCritCapability;
import com.mahghuuuls.critworks.state.PlayerCritState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the identity of a melee attack. Forge has no "attack ended" event, so one swing is followed by
 * the order of its events inside one call: the attack event opens it (and reads the charge before the
 * cooldown resets), the crit event decides its one result and removes the vanilla crit, the hurt
 * events of the primary target and every sweep target are multiplied, and the damage events decide
 * which targets were hit. A swing closes at the player's next swing or at the end of the server tick.
 * A combat mod's attacks (RLCombat) come through the same events; its {@link MeleeAttackSource} gives
 * their hand and charge.
 */
public final class MeleeAttackTracker {

    private static final String PLAYER_DAMAGE = "player";
    private static final String THORNS_DAMAGE = "thorns";

    private final StatResolver resolver;
    private final CritSettings settings;
    private final HitResolver hitResolver;
    private final CritFeedback feedback;
    private final DiagnosticLog diagnostics;
    private final MeleeAttackSource source;
    private final Map<EntityPlayer, MeleeAttack> open = new HashMap<EntityPlayer, MeleeAttack>();
    /** Attacks of other combat systems, kept apart from the player's own swings so neither closes the other. */
    private final Map<EntityPlayer, MeleeAttack> integrations = new HashMap<EntityPlayer, MeleeAttack>();

    MeleeAttackTracker(StatResolver resolver, CritSettings settings, CritFeedback feedback, DiagnosticLog diagnostics,
                       MeleeAttackSource source, HitResolver hitResolver) {
        this.resolver = resolver;
        this.settings = settings;
        this.hitResolver = hitResolver;
        this.feedback = feedback;
        this.diagnostics = diagnostics;
        this.source = source;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(AttackEntityEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player.world.isRemote) {
            return;
        }
        if (hitResolver.isReacting()) {
            // A swing made by a critical-hit event handler is a reaction: it opens no attack and keeps the
            // attack whose event is running.
            return;
        }
        if (integrations.containsKey(player)) {
            // A vanilla swing made inside an integration attack is part of it: no swing of its own.
            return;
        }
        close(player);
        EnumHand hand = EnumHand.MAIN_HAND;
        float charge;
        if (source.owns(event)) {
            hand = source.handOf(event);
            if (hand == null) {
                // The combat mod's integration is off: its attack keeps its own behavior.
                return;
            }
            // The combat mod gives the attacking hand's charge with its crit event.
            charge = Float.NaN;
        } else {
            charge = player.getCooledAttackStrength(0.5F);
        }
        ItemStack weapon = player.getHeldItem(hand);
        open.put(player, new MeleeAttack(player, weapon, hand, charge, event.getTarget()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCriticalHit(CriticalHitEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player.world.isRemote) {
            return;
        }
        if (hitResolver.isReacting()) {
            // A reaction swing cannot crit: neither a Critworks crit nor the vanilla one.
            event.setResult(Event.Result.DENY);
            return;
        }
        if (integrations.containsKey(player)) {
            // A vanilla swing inside an integration attack has no crit of its own; the integration decided.
            event.setResult(Event.Result.DENY);
            return;
        }
        MeleeAttack attack = open.get(player);
        if (attack == null || attack.isResolved() || attack.primaryTarget != event.getTarget()) {
            return;
        }
        float charge = source.owns(event) ? source.chargeOf(event) : attack.getCharge();
        if (Float.isNaN(charge)) {
            // The combat mod's integration turned off during this attack: the attack keeps its own behavior.
            open.remove(player);
            return;
        }
        CritProfile profile = resolver.resolve(player, attack.weapon, attack.hand, AttackKind.MELEE);
        Entity target = event.getTarget();
        String reason = optOut(player, AttackRules.problem(profile, charge, settings.getMeleeChargeThreshold(),
                target instanceof EntityLivingBase, target instanceof EntityPlayer, settings.isPvpCrits()));
        CritRoller.Decision decision = reason.isEmpty()
                ? CritRoller.decide(PlayerCritCapability.of(player), profile.getFinalChance(), player.world.rand)
                : null;
        boolean vanillaCritDenied = event.isVanillaCritical() || event.getResult() == Event.Result.ALLOW;
        event.setResult(Event.Result.DENY);
        attack.resolve(profile, charge, reason, decision, vanillaCritDenied);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onHurt(LivingHurtEvent event) {
        EntityLivingBase target = event.getEntityLiving();
        if (target.world.isRemote) {
            return;
        }
        MeleeAttack attack = attackOf(event.getSource());
        if (attack == null) {
            return;
        }
        if (hitResolver.isReacting()) {
            if (hitResolver.allowsCritNow()) {
                // An opted-in reaction takes part in the attack: its own hit, apart from the target's first one.
                TargetHit reaction = attack.reactionHitFor(target);
                reaction.critical = isCriticalFor(attack, target);
                double multiplier = reaction.critical ? attack.getProfile().getFinalMultiplier() : 1.0D;
                event.setAmount(hitResolver.onHurt(reaction, target, event.getAmount(), reaction.critical, multiplier));
            }
            return;
        }
        TargetHit hit = attack.hitFor(target);
        attack.setInHit(true);
        hit.critical = isCriticalFor(attack, target);
        double multiplier = hit.critical ? attack.getProfile().getFinalMultiplier() : 1.0D;
        event.setAmount(hitResolver.onHurt(hit, target, event.getAmount(), hit.critical, multiplier));
    }

    /**
     * A hurt event that ends canceled or at 0 (a shield block, another mod) gets no damage event, so the
     * swing's hit ends here instead.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onHurtEnd(LivingHurtEvent event) {
        if (event.getEntityLiving().world.isRemote || (!event.isCanceled() && event.getAmount() > 0.0F)) {
            return;
        }
        Entity attacker = event.getSource().getTrueSource();
        MeleeAttack swing = attacker instanceof EntityPlayer ? open.get(attacker) : null;
        if (swing != null) {
            swing.setInHit(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDamaged(LivingDamageEvent event) {
        EntityLivingBase target = event.getEntityLiving();
        if (target.world.isRemote) {
            return;
        }
        final MeleeAttack attack = attackOf(event.getSource());
        if (attack == null) {
            return;
        }
        if (hitResolver.isReacting()) {
            TargetHit reaction = attack.takeReactionHit(target);
            if (reaction != null && hitResolver.allowsCritNow()
                    && hitResolver.onDamaged(reaction, target, event.getAmount()) && reaction.critical) {
                fireEvent(attack, reaction, target);
            }
            return;
        }
        TargetHit hit = attack.existingHit(target);
        attack.setInHit(false);
        if (hit == null || hit.eventFired) {
            // Other player damage to a target this swing already hit successfully is not a second hit (REQ-050).
            return;
        }
        if (hitResolver.onDamaged(hit, target, event.getAmount()) && hit.critical) {
            feedback.particles(attack.attacker, target);
            if (attack.claimSound()) {
                feedback.sound(attack.attacker);
            }
            useGuarantee(attack);
            fireEvent(attack, hit, target);
        }
    }

    /**
     * Opens and decides an attack of another combat system for the player; damage the player deals until
     * {@link #endIntegration} belongs to it.
     */
    void beginIntegration(EntityPlayer player, ItemStack stack, EnumHand hand, AttackKind kind, float charge) {
        if (player.world.isRemote || hitResolver.isReacting()) {
            // A begin from a critical-hit event handler is a reaction: its damage counts as reaction damage.
            return;
        }
        MeleeAttack swing = open.get(player);
        if (swing != null && swing.isInHit()) {
            // A begin while the player's own swing is dealing a hit opens nothing: the swing keeps its damage.
            return;
        }
        // Attacks do not overlap: the player's earlier swing of this tick, and an earlier integration, end here.
        close(player);
        closeIntegration(player);
        MeleeAttack attack = new MeleeAttack(player, stack, hand, charge, null);
        attack.setIntegration("integration");
        CritProfile profile = resolver.resolve(player, stack, hand, kind);
        double threshold = kind == AttackKind.ARROW ? settings.getBowChargeThreshold() : settings.getMeleeChargeThreshold();
        String reason = optOut(player, AttackRules.releaseProblem(profile, charge, threshold));
        CritRoller.Decision decision = reason.isEmpty()
                ? CritRoller.decide(PlayerCritCapability.of(player), profile.getFinalChance(), player.world.rand)
                : null;
        attack.resolve(profile, charge, reason, decision, false);
        integrations.put(player, attack);
    }

    void endIntegration(EntityPlayer player) {
        if (!hitResolver.isReacting()) {
            // An end from a critical-hit event handler belongs to a refused begin; the running attack goes on.
            closeIntegration(player);
        }
    }

    private void closeIntegration(EntityPlayer player) {
        MeleeAttack attack = integrations.remove(player);
        if (attack != null && attack.isResolved() && diagnostics.isEnabled()) {
            diagnostics.write(describe(attack));
        }
    }

    /** Every attack uses up a pending opt-out as it is decided; an opted-out attack cannot crit (REQ-021). */
    private static String optOut(EntityPlayer player, String reason) {
        PlayerCritState state = PlayerCritCapability.of(player);
        boolean optedOut = state != null && state.consumeOptOut();
        return optedOut && reason.isEmpty() ? AttackRules.REASON_OPTED_OUT : reason;
    }

    /** A critical swing is critical for each target the PvP rule allows: the primary target and every sweep target. */
    private boolean isCriticalFor(MeleeAttack attack, EntityLivingBase target) {
        return attack.isCritical()
                && AttackRules.targetProblem(target instanceof EntityPlayer, settings.isPvpCrits()).isEmpty();
    }

    private void fireEvent(MeleeAttack attack, TargetHit hit, EntityLivingBase target) {
        hitResolver.fireEvent(hit, attack.attacker, target, attack.weapon, attack.hand,
                attack.isIntegration() ? CritworksHitEvent.Kind.INTEGRATION
                        : hit.primary ? CritworksHitEvent.Kind.MELEE_PRIMARY : CritworksHitEvent.Kind.MELEE_SWEEP,
                attack.getProfile().getFinalMultiplier(), attack.isGuaranteed());
    }

    /** A melee guarantee is used at the swing's first successful hit, so a miss keeps it (REQ-061). */
    private static void useGuarantee(MeleeAttack attack) {
        if (attack.isGuaranteed() && attack.claimGuarantee()) {
            PlayerCritState state = PlayerCritCapability.of(attack.attacker);
            if (state != null) {
                state.remove(attack.getGuaranteeId());
            }
        }
    }

    /** Drops open swings when the server stops, so no player outlives an integrated-server session here. */
    public void onServerStopping() {
        open.clear();
        integrations.clear();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || (open.isEmpty() && integrations.isEmpty())) {
            return;
        }
        for (EntityPlayer player : new ArrayList<EntityPlayer>(open.keySet())) {
            close(player);
        }
        for (EntityPlayer player : new ArrayList<EntityPlayer>(integrations.keySet())) {
            closeIntegration(player);
        }
    }

    /**
     * The resolved attack a damage source belongs to: direct melee damage by a player with an open swing goes
     * to that swing; other damage of a player with an open integration attack goes to the integration,
     * except arrows, thorns, explosions, fire, and magic, which are never part of it.
     */
    private MeleeAttack attackOf(DamageSource source) {
        Entity attacker = source.getTrueSource();
        if (!(attacker instanceof EntityPlayer)) {
            return null;
        }
        if (PLAYER_DAMAGE.equals(source.getDamageType()) && source.getImmediateSource() == attacker) {
            MeleeAttack swing = open.get(attacker);
            if (swing != null && swing.isResolved()) {
                return swing;
            }
        }
        MeleeAttack integration = integrations.get(attacker);
        if (integration == null || !integration.isResolved() || !integrationMayOwn(source)) {
            return null;
        }
        return integration;
    }

    private static boolean integrationMayOwn(DamageSource source) {
        return !(source.getImmediateSource() instanceof EntityArrow) && !THORNS_DAMAGE.equals(source.getDamageType())
                && !source.isExplosion() && !source.isFireDamage() && !source.isMagicDamage();
    }

    private void close(EntityPlayer player) {
        MeleeAttack attack = open.remove(player);
        if (attack != null && attack.isResolved() && diagnostics.isEnabled()) {
            diagnostics.write(describe(attack));
        }
    }

    private DiagnosticLog.Line describe(MeleeAttack attack) {
        CritProfile profile = attack.getProfile();
        DiagnosticLog.Line line = diagnostics.line(attack.isIntegration() ? "integration" : "melee")
                .add("attacker", attack.attacker.getName())
                .add("weapon", attack.weapon.isEmpty() ? "empty" : String.valueOf(attack.weapon.getItem().getRegistryName()))
                .add("hand", attack.hand == EnumHand.MAIN_HAND ? "main" : "off")
                .add("charge", attack.getCharge() * 100.0D);
        String result;
        if (!attack.isEligible()) {
            result = "\"not eligible: " + attack.getIneligibleReason() + "\"";
        } else {
            result = attack.isCritical() ? "critical" : "normal";
        }
        // A weapon that is not eligible has no stats; its line shows "-" instead of placeholder numbers.
        double chance = profile.isEligible() ? profile.getFinalChance() : Double.NaN;
        double multiplier = profile.isEligible() ? profile.getFinalMultiplier() : Double.NaN;
        line.add("chance", chance)
                .add("roll", attack.getRoll())
                .add("result", result)
                .add("by", attack.isGuaranteed() ? "guarantee" : "roll")
                .add("multiplier", multiplier)
                .add("vanillaCrit", attack.isVanillaCritDenied() ? "denied" : "no");
        line.add("guarantee", !attack.isGuaranteed() ? "-"
                : attack.getGuaranteeId() + (attack.isGuaranteeUsed() ? ":used" : ":kept"));
        StringBuilder targets = new StringBuilder("[");
        for (TargetHit hit : attack.hits()) {
            if (targets.length() > 1) {
                targets.append(',');
            }
            targets.append(hit.targetName).append(attack.isIntegration() ? ":target:" : hit.primary ? ":primary:" : ":sweep:")
                    .append(DiagnosticLog.Line.format(hit.damageBefore)).append("->")
                    .append(DiagnosticLog.Line.format(hit.damageAfter))
                    .append(hit.successful ? ":hit" : ":nohit")
                    .append(hit.eventFired ? ":event" : "");
        }
        return line.add("targets", targets.append(']').toString());
    }
}
