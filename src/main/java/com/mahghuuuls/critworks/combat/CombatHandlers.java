package com.mahghuuuls.critworks.combat;

import java.util.function.ToIntFunction;
import com.mahghuuuls.critworks.api.CritworksHitEvent;
import com.mahghuuuls.critworks.core.AttackKind;
import com.mahghuuuls.critworks.core.AttackerPolicies;
import com.mahghuuuls.critworks.core.CritSettings;
import com.mahghuuuls.critworks.core.StatResolver;
import com.mahghuuuls.critworks.diagnostics.DiagnosticLog;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.eventhandler.EventBus;

/**
 * The melee, arrow, and other-attacker handlers, built around one shared {@link HitResolver}, so that
 * every attack kind uses the same successful-hit rule and the same reaction guard.
 */
public final class CombatHandlers {

    private final MeleeAttackTracker melee;
    private final ArrowShotTracker arrows;
    private final MobAttackTracker mobs;

    private CombatHandlers(MeleeAttackTracker melee, ArrowShotTracker arrows, MobAttackTracker mobs) {
        this.melee = melee;
        this.arrows = arrows;
        this.mobs = mobs;
    }

    /** The ticks of a full vanilla bow draw. */
    public static final int VANILLA_FULL_DRAW_TICKS = ArrowShotTracker.FULL_DRAW_TICKS;

    /** @param fullDrawTicks the ticks of a full draw with a bow (the Spartan longbow has its own) */
    public static CombatHandlers create(StatResolver resolver, CritSettings settings, CritFeedback feedback,
                                        DiagnosticLog diagnostics, MeleeAttackSource meleeSource,
                                        AttackerPolicies attackers, ToIntFunction<ItemStack> fullDrawTicks) {
        HitResolver hits = new HitResolver(new ReactionGuard(CritworksHitEvent.MAX_CHAIN_DEPTH));
        return new CombatHandlers(new MeleeAttackTracker(resolver, settings, feedback, diagnostics, meleeSource, hits),
                new ArrowShotTracker(resolver, settings, feedback, diagnostics, hits, fullDrawTicks),
                new MobAttackTracker(resolver, settings, attackers, feedback, diagnostics, hits));
    }

    public void register(EventBus bus) {
        bus.register(melee);
        bus.register(arrows);
        bus.register(mobs);
    }

    /** Opens and decides an attack integration's attack for the player. */
    public void beginIntegration(EntityPlayer player, ItemStack stack, EnumHand hand, AttackKind kind, float charge) {
        melee.beginIntegration(player, stack, hand, kind, charge);
    }

    public void endIntegration(EntityPlayer player) {
        melee.endIntegration(player);
    }

    /** Drops open swings, pending shots, and mob hits when the server stops. */
    public void onServerStopping() {
        melee.onServerStopping();
        arrows.onServerStopping();
        mobs.onServerStopping();
    }
}
