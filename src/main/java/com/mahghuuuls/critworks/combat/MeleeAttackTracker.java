package com.mahghuuuls.critworks.combat;

import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.core.AttackRules;
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
 */
public final class MeleeAttackTracker {

    private static final String PLAYER_DAMAGE = "player";

    private final StatResolver resolver;
    private final CritSettings settings;
    private final HitResolver hitResolver;
    private final DiagnosticLog diagnostics;
    private final Map<EntityPlayer, MeleeAttack> open = new HashMap<EntityPlayer, MeleeAttack>();

    public MeleeAttackTracker(StatResolver resolver, CritSettings settings, CritFeedback feedback, DiagnosticLog diagnostics) {
        this.resolver = resolver;
        this.settings = settings;
        this.hitResolver = new HitResolver(feedback);
        this.diagnostics = diagnostics;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(AttackEntityEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player.world.isRemote) {
            return;
        }
        close(player);
        ItemStack weapon = player.getHeldItemMainhand();
        float charge = player.getCooledAttackStrength(0.5F);
        open.put(player, new MeleeAttack(player, weapon, EnumHand.MAIN_HAND, charge, event.getTarget()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCriticalHit(CriticalHitEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player.world.isRemote) {
            return;
        }
        MeleeAttack attack = open.get(player);
        if (attack == null || attack.isResolved() || attack.primaryTarget != event.getTarget()) {
            return;
        }
        CritProfile profile = resolver.resolve(player, attack.weapon, attack.hand);
        Entity target = event.getTarget();
        String reason = AttackRules.problem(profile, attack.charge, settings.getMeleeChargeThreshold(),
                target instanceof EntityLivingBase, target instanceof EntityPlayer, settings.isPvpCrits());
        double roll = Double.NaN;
        boolean critical = false;
        if (reason.isEmpty()) {
            roll = CritRoller.roll(player.world.rand);
            critical = CritRoller.isCritical(roll, profile.getFinalChance());
        }
        boolean vanillaCritDenied = event.isVanillaCritical() || event.getResult() == Event.Result.ALLOW;
        event.setResult(Event.Result.DENY);
        attack.resolve(profile, reason, roll, critical, vanillaCritDenied);
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
        TargetHit hit = attack.hitFor(target);
        double multiplier = attack.isCritical() ? attack.getProfile().getFinalMultiplier() : 1.0D;
        event.setAmount(hitResolver.onHurt(hit, target, event.getAmount(), attack.isCritical(), multiplier));
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
        TargetHit hit = attack.existingHit(target);
        if (hit == null) {
            return;
        }
        hitResolver.onDamaged(hit, target, event.getAmount(), attack.isCritical(), attack.attacker, attack::claimSound);
    }

    /** Drops open swings when the server stops, so no player outlives an integrated-server session here. */
    public void onServerStopping() {
        open.clear();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || open.isEmpty()) {
            return;
        }
        List<EntityPlayer> players = new ArrayList<EntityPlayer>(open.keySet());
        for (EntityPlayer player : players) {
            close(player);
        }
    }

    /** The resolved attack a damage source belongs to: direct melee damage by a player with an open swing. */
    private MeleeAttack attackOf(DamageSource source) {
        if (!PLAYER_DAMAGE.equals(source.getDamageType())) {
            return null;
        }
        Entity immediate = source.getImmediateSource();
        if (!(immediate instanceof EntityPlayer) || immediate != source.getTrueSource()) {
            return null;
        }
        MeleeAttack attack = open.get(immediate);
        return attack != null && attack.isResolved() ? attack : null;
    }

    private void close(EntityPlayer player) {
        MeleeAttack attack = open.remove(player);
        if (attack != null && attack.isResolved() && diagnostics.isEnabled()) {
            diagnostics.write(describe(attack));
        }
    }

    private DiagnosticLog.Line describe(MeleeAttack attack) {
        CritProfile profile = attack.getProfile();
        DiagnosticLog.Line line = diagnostics.line("melee")
                .add("attacker", attack.attacker.getName())
                .add("weapon", attack.weapon.isEmpty() ? "empty" : String.valueOf(attack.weapon.getItem().getRegistryName()))
                .add("hand", attack.hand == EnumHand.MAIN_HAND ? "main" : "off")
                .add("charge", attack.charge * 100.0D);
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
                .add("by", "roll")
                .add("multiplier", multiplier)
                .add("vanillaCrit", attack.isVanillaCritDenied() ? "denied" : "no");
        StringBuilder targets = new StringBuilder("[");
        for (TargetHit hit : attack.hits()) {
            if (targets.length() > 1) {
                targets.append(',');
            }
            targets.append(hit.targetName).append(hit.primary ? ":primary:" : ":sweep:")
                    .append(DiagnosticLog.Line.format(hit.damageBefore)).append("->")
                    .append(DiagnosticLog.Line.format(hit.damageAfter))
                    .append(hit.successful ? ":hit" : ":nohit");
        }
        return line.add("targets", targets.append(']').toString());
    }
}
