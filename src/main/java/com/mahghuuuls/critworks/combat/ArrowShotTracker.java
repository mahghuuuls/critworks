package com.mahghuuuls.critworks.combat;

import com.mahghuuuls.critworks.api.CritworksHitEvent;
import com.mahghuuuls.critworks.core.AttackKind;
import com.mahghuuuls.critworks.core.AttackRules;
import com.mahghuuuls.critworks.core.CritProfile;
import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.core.StatResolver;
import com.mahghuuuls.critworks.diagnostics.DiagnosticLog;
import com.mahghuuuls.critworks.state.ArrowCritCapability;
import com.mahghuuuls.critworks.state.ArrowCritState;
import com.mahghuuuls.critworks.state.PlayerCritCapability;
import com.mahghuuuls.critworks.state.PlayerCritState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.function.ToIntFunction;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Owns arrow capture. A player's bow release resolves the bow's stats and the draw; the arrow that the
 * release spawns in the same call takes them, rolls once, loses the vanilla critical flag (no native
 * crit damage, no trail), and keeps the result in its {@link ArrowCritState}. On each hit the captured
 * result is used, with the current PvP setting; skeleton, dispenser, and other arrows are left alone.
 */
public final class ArrowShotTracker {

    /** Ticks of a full vanilla bow draw: the draw at which vanilla's own arrow becomes critical. */
    static final int FULL_DRAW_TICKS = 20;

    private final StatResolver resolver;
    private final CritSettings settings;
    private final CritFeedback feedback;
    private final DiagnosticLog diagnostics;
    private final HitResolver hitResolver;
    private final ToIntFunction<ItemStack> fullDrawTicks;
    private final PendingShots<EntityPlayer, Release> releases = new PendingShots<EntityPlayer, Release>();
    private final Map<EntityArrow, ArrowHit> hitsInProgress = new IdentityHashMap<EntityArrow, ArrowHit>();

    /** @param fullDrawTicks the ticks of a full draw with a bow; {@link #FULL_DRAW_TICKS} unless a mod changes it */
    ArrowShotTracker(StatResolver resolver, CritSettings settings, CritFeedback feedback, DiagnosticLog diagnostics,
                     HitResolver hitResolver, ToIntFunction<ItemStack> fullDrawTicks) {
        this.hitResolver = hitResolver;
        this.fullDrawTicks = fullDrawTicks;
        this.resolver = resolver;
        this.settings = settings;
        this.feedback = feedback;
        this.diagnostics = diagnostics;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLoose(ArrowLooseEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player.world.isRemote) {
            return;
        }
        ItemStack bow = event.getBow();
        EnumHand hand = handHolding(player, bow);
        // Resolved now: the bow may break from this shot before its arrow spawns.
        CritProfile profile = resolver.resolve(player, bow, hand, AttackKind.ARROW);
        if (!(bow.getItem() instanceof ItemBow) && !profile.isEligible()) {
            // Another mod's launcher that fires this event keeps its own arrow behavior (REQ-030).
            return;
        }
        float draw = drawFraction(event.getCharge(), fullDrawTicks.applyAsInt(bow));
        String weapon = bow.isEmpty() ? "empty" : String.valueOf(bow.getItem().getRegistryName());
        releases.record(player, new Release(profile, draw, hand, weapon, bow.copy()), player.world.getTotalWorldTime());
    }

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote || !(event.getEntity() instanceof EntityArrow)) {
            return;
        }
        EntityArrow arrow = (EntityArrow) event.getEntity();
        if (!(arrow.shootingEntity instanceof EntityPlayer)) {
            return;
        }
        ArrowCritState state = ArrowCritCapability.of(arrow);
        if (state == null || state.isCaptured()) {
            // An arrow loaded from a save keeps the result it already has.
            return;
        }
        EntityPlayer player = (EntityPlayer) arrow.shootingEntity;
        Release release = releases.take(player, arrow.world.getTotalWorldTime());
        if (release == null) {
            return;
        }
        CritProfile profile = release.profile;
        String reason = AttackRules.releaseProblem(profile, release.draw, settings.getBowChargeThreshold());
        PlayerCritState optOutState = PlayerCritCapability.of(player);
        if (optOutState != null && optOutState.consumeOptOut() && reason.isEmpty()) {
            // An opted-out shot cannot crit and uses no guarantee (REQ-021).
            reason = AttackRules.REASON_OPTED_OUT;
        }
        double roll = Double.NaN;
        boolean critical = false;
        String guarantee = null;
        if (reason.isEmpty()) {
            PlayerCritState guarantees = PlayerCritCapability.of(player);
            CritRoller.Decision decision = CritRoller.decide(guarantees, profile.getFinalChance(), arrow.world.rand);
            roll = decision.roll;
            critical = decision.critical;
            guarantee = decision.guaranteeId;
            if (guarantee != null) {
                // An arrow uses its guarantee when it is fired, hit or miss (REQ-061).
                guarantees.remove(guarantee);
            }
        }
        boolean vanillaCritical = arrow.getIsCritical();
        arrow.setIsCritical(false);
        double chance = profile.isEligible() ? profile.getFinalChance() : Double.NaN;
        double multiplier = profile.isEligible() ? profile.getFinalMultiplier() : Double.NaN;
        state.capture(reason.isEmpty(), chance, multiplier, critical, guarantee != null, release.hand);
        state.setWeapon(release.bow);
        if (diagnostics.isEnabled()) {
            String result = !reason.isEmpty() ? "\"not eligible: " + reason + "\"" : critical ? "critical" : "normal";
            diagnostics.write(diagnostics.line("arrow")
                    .add("attacker", player.getName())
                    .add("weapon", release.weapon)
                    .add("hand", release.hand == EnumHand.MAIN_HAND ? "main" : "off")
                    .add("charge", release.draw * 100.0D)
                    .add("chance", chance)
                    .add("roll", roll)
                    .add("result", result)
                    .add("by", guarantee != null ? "guarantee" : "roll")
                    .add("multiplier", multiplier)
                    .add("vanillaCrit", vanillaCritical ? "cleared" : "no")
                    .add("arrow", shortId(arrow))
                    .add("guarantee", guarantee != null ? guarantee + ":used" : "-"));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onHurt(LivingHurtEvent event) {
        EntityLivingBase target = event.getEntityLiving();
        if (target.world.isRemote) {
            return;
        }
        EntityArrow arrow = arrowOf(event.getSource());
        ArrowCritState state = arrow == null ? null : ArrowCritCapability.of(arrow);
        if (state == null || !state.isCaptured()) {
            return;
        }
        if (hitResolver.isReacting() && !hitResolver.allowsCritNow()) {
            // Arrow damage dealt by a critical-hit event handler is a reaction: never critical, no event.
            return;
        }
        // The captured result stands; the PvP setting is the current one (REQ-040).
        boolean critical = state.isCritical()
                && AttackRules.targetProblem(target instanceof EntityPlayer, settings.isPvpCrits()).isEmpty();
        TargetHit hit = new TargetHit(target, true);
        event.setAmount(hitResolver.onHurt(hit, target, event.getAmount(), critical, state.getMultiplier()));
        hitsInProgress.put(arrow, new ArrowHit(target, hit, critical));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDamaged(LivingDamageEvent event) {
        EntityLivingBase target = event.getEntityLiving();
        if (target.world.isRemote) {
            return;
        }
        EntityArrow arrow = arrowOf(event.getSource());
        if (arrow == null) {
            return;
        }
        ArrowHit inProgress = hitsInProgress.remove(arrow);
        ArrowCritState state = ArrowCritCapability.of(arrow);
        if (inProgress == null || inProgress.target != target || state == null) {
            return;
        }
        boolean successful = hitResolver.onDamaged(inProgress.hit, target, event.getAmount());
        if (!successful || !inProgress.critical) {
            return;
        }
        boolean first = !state.hasHitAnyTarget();
        if (!state.recordHit(target.getUniqueID())) {
            // One success per target and arrow, so a target hit twice by one arrow gets no second event.
            return;
        }
        feedback.particlesOn(target);
        if (first) {
            feedback.soundAt(target);
        }
        EntityLivingBase shooter = arrow.shootingEntity instanceof EntityLivingBase ? (EntityLivingBase) arrow.shootingEntity : null;
        hitResolver.fireEvent(inProgress.hit, shooter, target, state.getWeapon(), state.getHand(),
                CritworksHitEvent.Kind.PROJECTILE, state.getMultiplier(), state.isGuaranteed());
        if (diagnostics.isEnabled()) {
            TargetHit hit = inProgress.hit;
            diagnostics.write(diagnostics.hitLine("arrow")
                    .add("arrow", shortId(arrow))
                    .add("target", hit.targetName)
                    .add("damage", DiagnosticLog.Line.format(hit.damageBefore) + "->"
                            + DiagnosticLog.Line.format(hit.damageAfter))
                    .add("event", hit.eventFired ? "yes" : "no"));
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // A release whose arrow never spawned, or a hurt without its damage event, ends with its tick.
        if (!releases.isEmpty()) {
            releases.clear();
        }
        if (!hitsInProgress.isEmpty()) {
            hitsInProgress.clear();
        }
    }

    public void onServerStopping() {
        releases.clear();
        hitsInProgress.clear();
    }

    /**
     * The draw at release as a fraction of a full draw: the time the bow was held back over the full-draw
     * time, so a bow held for half the time is half drawn (REQ-017).
     */
    static float drawFraction(int chargeTicks) {
        return drawFraction(chargeTicks, FULL_DRAW_TICKS);
    }

    /** As {@link #drawFraction(int)} for a bow whose full draw takes {@code fullDrawTicks}, such as a Spartan longbow. */
    static float drawFraction(int chargeTicks, int fullDrawTicks) {
        return Math.min(Math.max(chargeTicks, 0) / (float) Math.max(fullDrawTicks, 1), 1.0F);
    }

    private static EntityArrow arrowOf(DamageSource source) {
        Entity immediate = source.getImmediateSource();
        return immediate instanceof EntityArrow ? (EntityArrow) immediate : null;
    }

    private static EnumHand handHolding(EntityPlayer player, ItemStack bow) {
        if (player.getHeldItemOffhand() == bow) {
            return EnumHand.OFF_HAND;
        }
        return EnumHand.MAIN_HAND;
    }

    /** A short, stable name for the arrow in the release and hit lines; the arrow's id survives a reload. */
    private static String shortId(EntityArrow arrow) {
        return arrow.getUniqueID().toString().substring(0, 8);
    }

    /** What a release decided before its arrow existed. */
    private static final class Release {
        final CritProfile profile;
        final float draw;
        final EnumHand hand;
        final String weapon;
        final ItemStack bow;

        Release(CritProfile profile, float draw, EnumHand hand, String weapon, ItemStack bow) {
            this.profile = profile;
            this.draw = draw;
            this.hand = hand;
            this.weapon = weapon;
            this.bow = bow;
        }
    }

    /** One arrow hit between its hurt and its damage event. */
    private static final class ArrowHit {
        final EntityLivingBase target;
        final TargetHit hit;
        final boolean critical;

        ArrowHit(EntityLivingBase target, TargetHit hit, boolean critical) {
            this.target = target;
            this.hit = hit;
            this.critical = critical;
        }
    }
}
