package com.mahghuuuls.critworks.core;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;

/**
 * One pack rule from {@code weapons.json}: which stacks it matches and the values it sets. A value
 * that is null is not set by this rule, so a less specific rule or the next source decides it. A rule
 * names one item id (with optional metadata and item data) or one item type, such as a Spartan weapon
 * type; a type rule has no item.
 */
public final class WeaponRule {

    private final int index;
    private final Item item;
    private final ItemType type;
    private final Integer meta;
    private final NBTTagCompound nbt;
    private final Boolean eligible;
    private final Double chance;
    private final Double damage;

    public WeaponRule(int index, Item item, Integer meta, NBTTagCompound nbt, Boolean eligible, Double chance,
                      Double damage) {
        this(index, item, null, meta, nbt, eligible, chance, damage);
    }

    private WeaponRule(int index, Item item, ItemType type, Integer meta, NBTTagCompound nbt, Boolean eligible,
                       Double chance, Double damage) {
        this.index = index;
        this.item = item;
        this.type = type;
        this.meta = meta;
        this.nbt = nbt;
        this.eligible = eligible;
        this.chance = chance;
        this.damage = damage;
    }

    /** A rule for every item of a type; it sets values only, with no metadata or item data. */
    public static WeaponRule forType(int index, ItemType type, Boolean eligible, Double chance, Double damage) {
        return new WeaponRule(index, null, type, null, null, eligible, chance, damage);
    }

    /**
     * Higher is more specific: item data counts more than metadata, so id with metadata and item data
     * beats id with item data, which beats id with metadata, which beats id only, which beats a type.
     */
    public int specificity() {
        if (type != null) {
            return 0;
        }
        return 1 + (meta != null ? 1 : 0) + (nbt != null ? 2 : 0);
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (type != null) {
            return type.matches(stack.getItem());
        }
        if (stack.getItem() != item) {
            return false;
        }
        if (meta != null && stack.getMetadata() != meta) {
            return false;
        }
        return nbt == null || (stack.getTagCompound() != null && NBTUtil.areNBTEquals(nbt, stack.getTagCompound(), true));
    }

    /**
     * False when the rules can never match one stack, because they require different metadata,
     * different item data at the same place, or different types.
     */
    public boolean canMatchSameStackAs(WeaponRule other) {
        if (type != null || other.type != null) {
            return type != null && other.type != null && type.name().equals(other.type.name());
        }
        return item == other.item && (meta == null || other.meta == null || meta.equals(other.meta))
                && (nbt == null || other.nbt == null || canBothMatch(nbt, other.nbt));
    }

    /**
     * Mirrors the partial matching of {@link NBTUtil#areNBTEquals}: a compound needs only its own keys,
     * and a list needs each of its elements somewhere in the stack's list. A stack holds one tag per key,
     * so two required tags of different types or different values never both match. Lists are treated
     * as able to overlap, except an empty list, which only matches an empty list.
     */
    private static boolean canBothMatch(NBTBase a, NBTBase b) {
        if (a.getId() != b.getId()) {
            return false;
        }
        if (a instanceof NBTTagCompound) {
            NBTTagCompound left = (NBTTagCompound) a;
            NBTTagCompound right = (NBTTagCompound) b;
            for (String key : left.getKeySet()) {
                if (right.hasKey(key) && !canBothMatch(left.getTag(key), right.getTag(key))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof NBTTagList) {
            return ((NBTTagList) a).isEmpty() == ((NBTTagList) b).isEmpty();
        }
        return a.equals(b);
    }

    /** The rule's item, or null for a type rule. */
    public Item getItem() {
        return item;
    }

    /** The rule's type, or null for an id rule. */
    public ItemType getType() {
        return type;
    }

    /** Required metadata, or null for any. */
    public Integer getMeta() {
        return meta;
    }

    /** Required item data (a copy), or null for any. */
    public NBTTagCompound getNbt() {
        return nbt == null ? null : nbt.copy();
    }

    /** Position of the rule in the file, starting at 0. */
    public int getIndex() {
        return index;
    }

    public Boolean getEligible() {
        return eligible;
    }

    public Double getChance() {
        return chance;
    }

    public Double getDamage() {
        return damage;
    }

    /** How inspection names this rule as a source. */
    public String describe() {
        if (type != null) {
            return "pack rule #" + (index + 1) + " (" + type.describe() + ")";
        }
        StringBuilder text = new StringBuilder("pack rule #").append(index + 1).append(' ')
                .append(item.getRegistryName());
        if (meta != null) {
            text.append(" meta ").append(meta);
        }
        if (nbt != null) {
            text.append(" nbt ").append(nbt);
        }
        return text.toString();
    }
}
