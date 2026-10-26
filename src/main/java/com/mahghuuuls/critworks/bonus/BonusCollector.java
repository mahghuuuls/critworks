package com.mahghuuuls.critworks.bonus;

import com.mahghuuuls.critworks.api.CritStat;
import com.mahghuuuls.critworks.api.WeaponBonus;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Owns which bonuses count for one attack: the player's global bonuses (attribute modifiers with operation
 * 0), without the modifiers that held items put on the whole player (Quality Tools, ARC-006), then the
 * attacking stack's own weapon-specific bonuses from the registered providers and the held-item sources.
 * A bonus that is not a finite number is left out and reported once per source; a held-item source that
 * throws is reported once and then skipped.
 */
public final class BonusCollector {

    private static final int ADD = 0;

    private final Logger logger;
    private final WeaponBonusProviders providers;
    private final List<HeldItemBonuses> heldSources;
    private final Set<HeldItemBonuses> failedSources = Collections.newSetFromMap(new ConcurrentHashMap<HeldItemBonuses, Boolean>());
    private final Function<AttributeModifier, String> labels;
    private final Set<String> reported = Collections.synchronizedSet(new HashSet<String>());

    public BonusCollector(Logger logger) {
        this(logger, new WeaponBonusProviders(logger::warn));
    }

    public BonusCollector(Logger logger, WeaponBonusProviders providers) {
        this(logger, providers, new CopyOnWriteArrayList<HeldItemBonuses>(), AttributeModifier::getName);
    }

    private BonusCollector(Logger logger, WeaponBonusProviders providers, List<HeldItemBonuses> heldSources,
                           Function<AttributeModifier, String> labels) {
        this.logger = logger;
        this.providers = providers;
        this.heldSources = heldSources;
        this.labels = labels;
    }

    /**
     * The collector for the client's tooltips: global bonus sources named by {@code labels}, because vanilla
     * sends attribute modifiers to the client without their names, and its own record of failed providers
     * and held-item sources.
     */
    public BonusCollector forClient(Function<AttributeModifier, String> labels) {
        return new BonusCollector(logger, providers.withOwnFailures(), heldSources, labels);
    }

    /** Adds a held-item source; done once at start for each loaded integration. */
    public void addHeldItemBonuses(HeldItemBonuses source) {
        heldSources.add(source);
    }

    /** As {@link #collect(EntityPlayer, ItemStack, EnumHand, Predicate)} with every integration on. */
    public List<Bonus> collect(EntityPlayer player, ItemStack stack, EnumHand hand) {
        return collect(player, stack, hand, key -> true);
    }

    /**
     * Every bonus that counts for an attack by {@code player} (null for another attacker) with {@code stack}
     * from {@code hand}: the player's global bonuses without those of the held items, then the stack's own
     * bonuses.
     *
     * @param integrationOn answers whether an integration setting key is on (the server's settings on a client)
     */
    public List<Bonus> collect(EntityPlayer player, ItemStack stack, EnumHand hand, Predicate<String> integrationOn) {
        if (player == null) {
            return collect(null, null, Collections.<ItemStack>emptyList(), ItemStack.EMPTY, stack, hand, integrationOn);
        }
        return collect(player, player::getEntityAttribute, player.inventory.mainInventory, player.getHeldItemOffhand(),
                stack, hand, integrationOn);
    }

    /**
     * {@link #collect(EntityPlayer, ItemStack, EnumHand, Predicate)} with the player's attributes, main
     * inventory, and offhand stack given.
     */
    List<Bonus> collect(EntityPlayer player, Function<IAttribute, IAttributeInstance> attributes,
                        List<ItemStack> mainInventory, ItemStack offHand, ItemStack stack, EnumHand hand,
                        Predicate<String> integrationOn) {
        List<HeldItemBonuses> sources = new ArrayList<HeldItemBonuses>();
        for (HeldItemBonuses source : heldSources) {
            if (!failedSources.contains(source) && integrationOn.test(source.integrationKey())) {
                sources.add(source);
            }
        }
        List<Bonus> bonuses = attributes == null ? new ArrayList<Bonus>()
                : globalBonuses(attributes, heldModifierIds(mainInventory, offHand, sources));
        for (WeaponBonus bonus : providers.bonusesFor(player, stack)) {
            add(bonuses, bonus.getStat(), bonus.getAmount(), bonus.getSource());
        }
        if (!stack.isEmpty()) {
            for (HeldItemBonuses source : sources) {
                try {
                    for (Bonus bonus : source.ownBonuses(stack, hand)) {
                        add(bonuses, bonus.getStat(), bonus.getAmount(), bonus.getSource());
                    }
                } catch (RuntimeException | LinkageError e) {
                    fail(source, e);
                }
            }
        }
        return bonuses;
    }

    /** Every stack's hand-only modifiers, not only the held ones, so a swap the mod has not caught up with leaks nothing. */
    private Set<UUID> heldModifierIds(List<ItemStack> mainInventory, ItemStack offHand, List<HeldItemBonuses> sources) {
        Set<UUID> ids = new HashSet<UUID>();
        for (HeldItemBonuses source : sources) {
            try {
                for (ItemStack stack : mainInventory) {
                    ids.addAll(source.heldModifierIds(stack, EnumHand.MAIN_HAND));
                }
                ids.addAll(source.heldModifierIds(offHand, EnumHand.OFF_HAND));
            } catch (RuntimeException | LinkageError e) {
                fail(source, e);
            }
        }
        return ids;
    }

    private void fail(HeldItemBonuses source, Throwable e) {
        if (failedSources.add(source)) {
            logger.warn("Critworks {} integration failed and is off until restart: {}", source.integrationKey(), e.toString());
        }
    }

    /** The global bonuses read from a player's attribute instances. */
    List<Bonus> globalBonuses(Function<IAttribute, IAttributeInstance> attributes) {
        return globalBonuses(attributes, Collections.<UUID>emptySet());
    }

    /** The global bonuses read from a player's attribute instances, without the modifiers in {@code excluded}. */
    List<Bonus> globalBonuses(Function<IAttribute, IAttributeInstance> attributes, Set<UUID> excluded) {
        List<Bonus> bonuses = new ArrayList<Bonus>();
        forEachCountedModifier(attributes, (stat, modifier) -> {
            if (!excluded.contains(modifier.getID())) {
                add(bonuses, stat, modifier.getAmount(), labels.apply(modifier));
            }
        });
        return bonuses;
    }

    /** Every attribute modifier that counts as a global bonus: operation 0 on the four Critworks attributes. */
    public static void forEachCountedModifier(Function<IAttribute, IAttributeInstance> attributes,
                                              BiConsumer<CritStat, AttributeModifier> action) {
        for (CritStat stat : CritStat.values()) {
            IAttributeInstance instance = attributes.apply(CritAttributes.attribute(stat));
            if (instance == null) {
                continue;
            }
            for (AttributeModifier modifier : instance.getModifiersByOperation(ADD)) {
                action.accept(stat, modifier);
            }
        }
    }

    private void add(List<Bonus> bonuses, CritStat stat, double amount, String source) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            if (reported.add(source + ":" + stat)) {
                logger.warn("Critworks ignores a {} bonus from {} that is not a finite number", stat.getLabel(), source);
            }
            return;
        }
        bonuses.add(new Bonus(stat, amount, source));
    }

    /** Sum of the bonuses on one stat. */
    public static double total(List<Bonus> bonuses, CritStat stat) {
        double total = 0.0D;
        for (Bonus bonus : bonuses) {
            if (bonus.getStat() == stat) {
                total += bonus.getAmount();
            }
        }
        return total;
    }
}
