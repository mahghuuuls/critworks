package com.mahghuuuls.critworks;

import com.mahghuuuls.critworks.api.CritAttackKind;
import com.mahghuuuls.critworks.api.CritStat;
import com.mahghuuuls.critworks.api.CritStats;
import com.mahghuuuls.critworks.api.CritworksApi;
import com.mahghuuuls.critworks.api.GuaranteeSpec;
import com.mahghuuuls.critworks.api.IAttackIntegration;
import com.mahghuuuls.critworks.api.IWeaponBonusProvider;
import com.mahghuuuls.critworks.api.WeaponBonus;
import com.mahghuuuls.critworks.bonus.Bonus;
import com.mahghuuuls.critworks.bonus.CritAttributes;
import com.mahghuuuls.critworks.bonus.WeaponBonusProviders;
import com.mahghuuuls.critworks.combat.CombatHandlers;
import com.mahghuuuls.critworks.core.AddonDefaults;
import com.mahghuuuls.critworks.core.AttackKind;
import com.mahghuuuls.critworks.core.AttackerPolicies;
import com.mahghuuuls.critworks.core.CritProfile;
import com.mahghuuuls.critworks.core.StatResolver;
import com.mahghuuuls.critworks.state.PlayerCritCapability;
import com.mahghuuuls.critworks.state.PlayerCritState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/** Connects the public API to the internal components, so the api package depends on nothing inside. */
final class ApiBackend implements CritworksApi.Backend {

    private final Logger logger;
    private final AddonDefaults defaults;
    private final WeaponBonusProviders providers;
    private final AttackerPolicies attackers;
    private final Map<String, IAttackIntegration> integrations = new ConcurrentHashMap<String, IAttackIntegration>();
    private final AtomicBoolean warnedClientCall = new AtomicBoolean();
    private volatile StatResolver resolver;
    private volatile CombatHandlers combat;
    private volatile double damageCap = Double.POSITIVE_INFINITY;
    private volatile boolean registrationClosed;

    ApiBackend(Logger logger, AddonDefaults defaults, WeaponBonusProviders providers, AttackerPolicies attackers) {
        this.logger = logger;
        this.defaults = defaults;
        this.providers = providers;
        this.attackers = attackers;
    }

    /** Called in postInit, once the resolver and the combat handlers exist. */
    void ready(StatResolver resolver, CombatHandlers combat, double damageCap) {
        this.resolver = resolver;
        this.combat = combat;
        this.damageCap = damageCap;
    }

    /** Called when loading completes, after every mod's post-initialization. */
    void closeRegistration() {
        registrationClosed = true;
    }

    private void requireLoading(String what) {
        if (registrationClosed) {
            throw new IllegalStateException("Critworks API: " + what + " must be registered while mods load");
        }
    }

    @Override
    public void setGlobalBonus(EntityPlayer player, String sourceId, CritStat stat, double amount) {
        if (onServer(player, sourceId)) {
            CritAttributes.setGlobalBonus(player, sourceId, stat, amount);
        }
    }

    @Override
    public void removeGlobalBonus(EntityPlayer player, String sourceId, CritStat stat) {
        if (onServer(player, sourceId)) {
            CritAttributes.removeGlobalBonus(player, sourceId, stat);
        }
    }

    @Override
    public void grantGuarantee(EntityPlayer player, GuaranteeSpec spec) {
        PlayerCritState state = PlayerCritCapability.of(player);
        if (onServer(player, spec.getId()) && state != null) {
            state.grant(spec);
        }
    }

    @Override
    public boolean removeGuarantee(EntityPlayer player, String id) {
        PlayerCritState state = PlayerCritCapability.of(player);
        return onServer(player, id) && state != null && state.remove(id);
    }

    @Override
    public List<GuaranteeSpec> getGuarantees(EntityPlayer player) {
        PlayerCritState state = PlayerCritCapability.of(player);
        return state == null ? Collections.<GuaranteeSpec>emptyList() : Collections.unmodifiableList(state.list());
    }

    @Override
    public void registerItemDefaults(Item item, Predicate<ItemStack> stacks, Double chance, Double damage) {
        requireLoading("item defaults");
        defaults.register(item, stacks, chance, damage, callerModId());
    }

    @Override
    public void registerWeaponBonusProvider(IWeaponBonusProvider provider) {
        requireLoading("a weapon bonus provider");
        providers.register(provider, callerModId());
    }

    @Override
    public void registerAttackerPolicy(Predicate<EntityLivingBase> policy) {
        requireLoading("an attacker policy");
        attackers.register(policy, callerModId());
    }

    @Override
    public void optOutNextAttack(EntityPlayer player) {
        PlayerCritState state = PlayerCritCapability.of(player);
        if (onServer(player, "opt-out") && state != null) {
            state.optOutNextAttack();
        }
    }

    @Override
    public void registerAttackIntegration(IAttackIntegration integration) {
        requireLoading("an attack integration");
        if (integrations.putIfAbsent(integration.getId(), integration) != null) {
            throw new IllegalArgumentException("an attack integration with id " + integration.getId()
                    + " is already registered");
        }
        logger.info("Critworks: attack integration {} registered by {}", integration.getId(), callerModId());
    }

    @Override
    public void beginAttack(EntityPlayer player, ItemStack stack, EnumHand hand, CritAttackKind kind, float charge) {
        if (onServer(player, "an attack integration")) {
            loaded().beginIntegration(player, stack, hand,
                    kind == CritAttackKind.RANGED ? AttackKind.ARROW : AttackKind.MELEE, charge);
        }
    }

    @Override
    public void endAttack(EntityPlayer player) {
        if (!player.world.isRemote) {
            loaded().endIntegration(player);
        }
    }

    @Override
    public CritStats resolve(EntityPlayer player, ItemStack stack, EnumHand hand) {
        StatResolver current = resolver;
        if (current == null) {
            throw new IllegalStateException("Critworks API resolve used before Critworks finished loading");
        }
        CritProfile profile = current.resolve(player, stack, hand);
        List<WeaponBonus> bonuses = new ArrayList<WeaponBonus>();
        for (Bonus bonus : profile.getBonuses()) {
            bonuses.add(new WeaponBonus(bonus.getStat(), bonus.getAmount(), bonus.getSource()));
        }
        String eligibility = profile.isEligible() ? profile.getEligibilitySource() : profile.getIneligibleReason();
        return new CritStats(profile.isEligible(), eligibility, profile.getBaseChance(), profile.getBaseChanceSource(),
                profile.getBaseMultiplier(), profile.getBaseMultiplierSource(), profile.getFinalChance(),
                profile.getFinalMultiplier(), profile.isEligible() ? profile.getCap() : damageCap, bonuses);
    }

    private CombatHandlers loaded() {
        CombatHandlers current = combat;
        if (current == null) {
            throw new IllegalStateException("Critworks API attack used before Critworks finished loading");
        }
        return current;
    }

    /** The mod that is calling during loading, for the source shown with its defaults and providers. */
    private static String callerModId() {
        ModContainer active = Loader.instance().activeModContainer();
        return active == null ? "an unknown mod" : active.getModId();
    }

    /** Bonuses, guarantees, and attacks are server state; a client-side change is ignored and reported once. */
    private boolean onServer(EntityPlayer player, String sourceId) {
        if (!player.world.isRemote) {
            return true;
        }
        if (warnedClientCall.compareAndSet(false, true)) {
            logger.warn("Critworks API: a call for {} on the client side was ignored; call it on the server",
                    sourceId);
        }
        return false;
    }
}
