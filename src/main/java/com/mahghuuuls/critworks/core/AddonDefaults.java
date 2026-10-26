package com.mahghuuuls.critworks.core;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Addon default base stats, the middle tier of the precedence (pack rule, addon default, fallback). Item
 * defaults are checked before predicate defaults; within each, the first registered that sets a stat wins.
 * Registration may happen while the game runs, so the lists are safe to read during a registration.
 */
public final class AddonDefaults {

    private final List<Entry> itemDefaults;
    private final List<Entry> predicateDefaults;
    private final Set<Entry> broken = Collections.newSetFromMap(new ConcurrentHashMap<Entry, Boolean>());
    private final Consumer<String> warn;

    /** @param warn receives one warning when a predicate throws; that predicate is then skipped */
    public AddonDefaults(Consumer<String> warn) {
        this(new CopyOnWriteArrayList<Entry>(), new CopyOnWriteArrayList<Entry>(), warn);
    }

    private AddonDefaults(List<Entry> itemDefaults, List<Entry> predicateDefaults, Consumer<String> warn) {
        this.itemDefaults = itemDefaults;
        this.predicateDefaults = predicateDefaults;
        this.warn = warn;
    }

    /**
     * The same defaults with their own record of failures. The client's tooltips use it, so a predicate
     * that fails only for a tooltip still counts for attacks in the same game (REQ-095).
     */
    public AddonDefaults withOwnFailures() {
        return new AddonDefaults(itemDefaults, predicateDefaults, warn);
    }

    public void register(Item item, Predicate<ItemStack> stacks, Double chance, Double damage, String source) {
        Entry entry = new Entry(item, stacks, chance, damage, source);
        if (item != null) {
            itemDefaults.add(entry);
        } else {
            predicateDefaults.add(entry);
        }
    }

    /** The defaults that apply to a stack, per stat; a field is null when no default sets that stat. */
    public Match match(ItemStack stack) {
        Entry chance = null;
        Entry damage = null;
        if (stack.isEmpty()) {
            return new Match(null, null);
        }
        for (List<Entry> tier : itemDefaultsThenPredicates()) {
            for (Entry entry : tier) {
                if ((chance != null || entry.chance == null) && (damage != null || entry.damage == null)) {
                    continue;
                }
                if (!entry.matches(stack, broken, warn)) {
                    continue;
                }
                if (chance == null && entry.chance != null) {
                    chance = entry;
                }
                if (damage == null && entry.damage != null) {
                    damage = entry;
                }
            }
        }
        return new Match(chance, damage);
    }

    @SuppressWarnings("unchecked")
    private List<Entry>[] itemDefaultsThenPredicates() {
        return new List[] {itemDefaults, predicateDefaults};
    }

    /** One registered default. */
    public static final class Entry {
        private final Item item;
        private final Predicate<ItemStack> stacks;
        private final Double chance;
        private final Double damage;
        private final String source;

        Entry(Item item, Predicate<ItemStack> stacks, Double chance, Double damage, String source) {
            this.item = item;
            this.stacks = stacks;
            this.chance = chance;
            this.damage = damage;
            this.source = source;
        }

        boolean matches(ItemStack stack, Set<Entry> broken, Consumer<String> warn) {
            if (item != null) {
                return stack.getItem() == item;
            }
            if (broken.contains(this)) {
                return false;
            }
            try {
                return stacks.test(stack);
            } catch (RuntimeException | LinkageError e) {
                broken.add(this);
                warn.accept("Critworks: an item default predicate from " + source + " failed and is skipped: " + e);
                return false;
            }
        }

        public double getChance() {
            return chance;
        }

        public double getDamage() {
            return damage;
        }

        /** "addon default from <mod id>". */
        public String describe() {
            return "addon default from " + source;
        }
    }

    /** The winning default for each stat, or null. */
    public static final class Match {
        public final Entry chance;
        public final Entry damage;

        Match(Entry chance, Entry damage) {
            this.chance = chance;
            this.damage = damage;
        }
    }
}
