package com.mahghuuuls.critworks.api;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import java.util.List;
import java.util.function.Predicate;

/**
 * The Critworks API for addons. Call it on the logical server unless a method says otherwise. Critworks
 * binds it during its pre-initialization; declare {@code after:critworks} (or a required dependency) and
 * use it from your own pre-initialization or later. The registration methods (item defaults, bonus
 * providers, attacker policies, attack integrations) work only while mods load, up to and including every
 * mod's post-initialization; later calls throw {@link IllegalStateException}.
 */
public final class CritworksApi {

    /** API version, raised when methods are added. */
    public static final String API_VERSION = "1.0";

    private static Backend backend;

    private CritworksApi() {
    }

    /**
     * Gives a player a global bonus: it counts for every eligible attack of that player, both hands,
     * melee and ranged. Setting the same source and stat again replaces the old amount; an amount of 0
     * removes it. The bonus is saved with the player. Minecraft does not keep it on the new player after
     * death or after leaving the End, so your mod decides when the bonus starts and ends, including then.
     * A call on the client side is ignored.
     *
     * @param sourceId a stable id for your bonus, such as "mymod:rage"; shown as its source
     * @throws IllegalArgumentException when the amount is not a finite number
     */
    public static void setGlobalBonus(EntityPlayer player, String sourceId, CritStat stat, double amount) {
        requireSource(sourceId);
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            throw new IllegalArgumentException("amount must be a finite number, got " + amount);
        }
        backend().setGlobalBonus(player, sourceId, stat, amount);
    }

    /** Removes a global bonus set with {@link #setGlobalBonus}. */
    public static void removeGlobalBonus(EntityPlayer player, String sourceId, CritStat stat) {
        requireSource(sourceId);
        backend().removeGlobalBonus(player, sourceId, stat);
    }

    /**
     * Grants a guarantee: the player's next eligible attack is critical. A melee attack uses it at its
     * first successful hit, an arrow when it is fired. Granting the same id again replaces it. The next
     * guarantee used is the one that expires first, then the oldest. Saved with the player; counts down
     * only while the player is online; survives dimension travel; death removes it unless the spec keeps
     * it. A call on the client side is ignored.
     */
    public static void grantGuarantee(EntityPlayer player, GuaranteeSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        backend().grantGuarantee(player, spec);
    }

    /** Removes a pending guarantee by id; returns true when one was pending. */
    public static boolean removeGuarantee(EntityPlayer player, String id) {
        return backend().removeGuarantee(player, id);
    }

    /** The player's pending guarantees in the order they will be used, each with the ticks it has left. */
    public static List<GuaranteeSpec> getGuarantees(EntityPlayer player) {
        return backend().getGuarantees(player);
    }

    /**
     * Sets default base stats for an item. Each stat is resolved on its own: a pack rule wins, then an
     * addon default, then the server's fallback. A default does not make an item eligible; swords, axes,
     * and bows are eligible by type, and other items through a pack rule. When several defaults set the
     * same stat for a stack, the item defaults win over the predicate defaults, and among each the first
     * registered wins. Call it during loading, on both sides; the source shown is your mod id.
     *
     * @param chance base chance in percent, 0 to 100, or null to leave it to the fallback
     * @param damage base damage multiplier, at least 1, or null to leave it to the fallback
     */
    public static void registerItemDefaults(Item item, Double chance, Double damage) {
        if (item == null) {
            throw new IllegalArgumentException("item must not be null");
        }
        checkDefaults(chance, damage);
        backend().registerItemDefaults(item, null, chance, damage);
    }

    /** As {@link #registerItemDefaults(Item, Double, Double)}, for every stack the predicate accepts. */
    public static void registerItemDefaults(Predicate<ItemStack> stacks, Double chance, Double damage) {
        if (stacks == null) {
            throw new IllegalArgumentException("predicate must not be null");
        }
        checkDefaults(chance, damage);
        backend().registerItemDefaults(null, stacks, chance, damage);
    }

    /** Adds a provider of weapon-specific bonuses. Call it during loading, on both sides. */
    public static void registerWeaponBonusProvider(IWeaponBonusProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        backend().registerWeaponBonusProvider(provider);
    }

    /**
     * Lets attackers other than players crit. By default only players crit. An allowed attacker's melee hit
     * (damage of type "mob" dealt by the attacker itself) resolves the stack in its main hand like a
     * player's attack, at full charge, with no bonuses except weapon-specific ones; it is decided per hit.
     * Arrows of other attackers are not supported attacks. The policy is asked on the server for each hit,
     * so it must be cheap.
     */
    public static void registerAttackerPolicy(Predicate<EntityLivingBase> policy) {
        if (policy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }
        backend().registerAttackerPolicy(policy);
    }

    /**
     * Marks the player's next attack as unable to crit, whatever its chance or guarantees; the next melee
     * swing, arrow, or integration attack uses the mark up. It is not saved. A call on the client side is
     * ignored.
     */
    public static void optOutNextAttack(EntityPlayer player) {
        requirePlayer(player);
        backend().optOutNextAttack(player);
    }

    /** Registers another combat system's attack integration. Call it during loading; ids must be unique. */
    public static void registerAttackIntegration(IAttackIntegration integration) {
        if (integration == null || integration.getId() == null || integration.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("integration must have an id");
        }
        backend().registerAttackIntegration(integration);
    }

    /**
     * Starts an attack of an integration (see {@link IAttackIntegration}) and decides it at once, with the
     * same rules as a player's own attack. Call it on the server just before your attack deals damage, and
     * call {@link #endAttack} after it; an attack still open at the end of the server tick ends then. A
     * begin from inside a {@link CritworksHitEvent} handler is a reaction: it opens no attack.
     *
     * @param charge the attack's charge from 0 to 1, checked against the melee or bow threshold
     */
    public static void beginAttack(EntityPlayer player, ItemStack stack, EnumHand hand, CritAttackKind kind, float charge) {
        if (player == null || stack == null || hand == null || kind == null) {
            throw new IllegalArgumentException("player, stack, hand, and kind must not be null");
        }
        if (Float.isNaN(charge) || charge < 0.0F || charge > 1.0F) {
            throw new IllegalArgumentException("charge must be from 0 to 1, got " + charge);
        }
        backend().beginAttack(player, stack, hand, kind, charge);
    }

    /** Ends the player's open integration attack. Does nothing when none is open. */
    public static void endAttack(EntityPlayer player) {
        requirePlayer(player);
        backend().endAttack(player);
    }

    /**
     * Resolves the stats an attack of the player with the stack in that hand would use now. Works on both
     * sides once Critworks has finished loading; changes nothing and uses no guarantee. On the client it uses
     * what the client's tooltips use: the server's settings and rules while connected to a server (the
     * client's own before they arrive or when they cannot be read). On the client, call it from the client
     * thread, where those values change.
     */
    public static CritStats resolve(EntityPlayer player, ItemStack stack, EnumHand hand) {
        requirePlayer(player);
        if (stack == null || hand == null) {
            throw new IllegalArgumentException("stack and hand must not be null");
        }
        return backend().resolve(player, stack, hand);
    }

    /** True once Critworks has bound the API; useful for an optional dependency. */
    public static boolean isBound() {
        return backend != null;
    }

    private static void checkDefaults(Double chance, Double damage) {
        if (chance == null && damage == null) {
            throw new IllegalArgumentException("set a chance, a damage, or both");
        }
        if (chance != null && (Double.isNaN(chance) || chance < 0.0D || chance > 100.0D)) {
            throw new IllegalArgumentException("chance must be from 0 to 100, got " + chance);
        }
        if (damage != null && (Double.isNaN(damage) || Double.isInfinite(damage) || damage < 1.0D)) {
            throw new IllegalArgumentException("damage must be a finite multiplier of at least 1, got " + damage);
        }
    }

    private static Backend backend() {
        if (backend == null) {
            throw new IllegalStateException("Critworks API used before Critworks pre-initialization");
        }
        return backend;
    }

    private static void requirePlayer(EntityPlayer player) {
        if (player == null) {
            throw new IllegalArgumentException("player must not be null");
        }
    }

    private static void requireSource(String sourceId) {
        if (sourceId == null || sourceId.trim().isEmpty()) {
            throw new IllegalArgumentException("sourceId must not be empty");
        }
    }

    /** Critworks' implementation of the API. Not for addons. */
    public interface Backend {
        void setGlobalBonus(EntityPlayer player, String sourceId, CritStat stat, double amount);

        void removeGlobalBonus(EntityPlayer player, String sourceId, CritStat stat);

        void grantGuarantee(EntityPlayer player, GuaranteeSpec spec);

        boolean removeGuarantee(EntityPlayer player, String id);

        List<GuaranteeSpec> getGuarantees(EntityPlayer player);

        void registerItemDefaults(Item item, Predicate<ItemStack> stacks, Double chance, Double damage);

        void registerWeaponBonusProvider(IWeaponBonusProvider provider);

        void registerAttackerPolicy(Predicate<EntityLivingBase> policy);

        void optOutNextAttack(EntityPlayer player);

        void registerAttackIntegration(IAttackIntegration integration);

        void beginAttack(EntityPlayer player, ItemStack stack, EnumHand hand, CritAttackKind kind, float charge);

        void endAttack(EntityPlayer player);

        CritStats resolve(EntityPlayer player, ItemStack stack, EnumHand hand);
    }

    /** Called once by Critworks. Not for addons. */
    public static void bind(Backend implementation) {
        if (backend != null) {
            throw new IllegalStateException("Critworks API is already bound; only Critworks may call bind");
        }
        backend = implementation;
    }
}
