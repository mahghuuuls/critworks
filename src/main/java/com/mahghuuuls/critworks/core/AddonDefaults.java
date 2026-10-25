package com.mahghuuuls.critworks.core;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Addon default base stats, the middle tier of the precedence (pack rule, addon default, fallback). Item
 * defaults are checked before predicate defaults; within each, the first registered that sets a stat wins.
 * Registration may happen while the game runs, so the lists are safe to read during a registration.
 */
public final class AddonDefaults {

    private final List<Entry> itemDefaults = new CopyOnWriteArrayList<Entry>();
    private final List<Entry> predicateDefaults = new CopyOnWriteArrayList<Entry>();
    private final Consumer<String> warn;

    /** @param warn receives one warning when a predicate throws; that predicate is then skipped */
    public AddonDefaults(Consumer<String> warn) {
        this.warn = warn;
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
                if (!entry.matches(stack)) {
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
    public final class Entry {
        private final Item item;
        private final Predicate<ItemStack> stacks;
        private final Double chance;
        private final Double damage;
        private final String source;
        private volatile boolean broken;

        Entry(Item item, Predicate<ItemStack> stacks, Double chance, Double damage, String source) {
            this.item = item;
            this.stacks = stacks;
            this.chance = chance;
            this.damage = damage;
            this.source = source;
        }

        boolean matches(ItemStack stack) {
            if (item != null) {
                return stack.getItem() == item;
            }
            if (broken) {
                return false;
            }
            try {
                return stacks.test(stack);
            } catch (RuntimeException | LinkageError e) {
                broken = true;
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
