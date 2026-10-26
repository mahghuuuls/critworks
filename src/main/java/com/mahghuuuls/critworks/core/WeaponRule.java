package com.mahghuuuls.critworks.core;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;

/**
 * One pack rule from {@code weapons.json}: which stacks it matches and the values it sets. A value
 * that is null is not set by this rule, so a less specific rule or the next source decides it.
 */
public final class WeaponRule {

    private final int index;
    private final Item item;
    private final Integer meta;
    private final NBTTagCompound nbt;
    private final Boolean eligible;
    private final Double chance;
    private final Double damage;

    public WeaponRule(int index, Item item, Integer meta, NBTTagCompound nbt, Boolean eligible, Double chance,
                      Double damage) {
        this.index = index;
        this.item = item;
        this.meta = meta;
        this.nbt = nbt;
        this.eligible = eligible;
        this.chance = chance;
        this.damage = damage;
    }

    /**
     * Higher is more specific: item data counts more than metadata, so id with metadata and item data
     * beats id with item data, which beats id with metadata, which beats id only.
     */
    public int specificity() {
        return 1 + (meta != null ? 1 : 0) + (nbt != null ? 2 : 0);
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != item) {
            return false;
        }
        if (meta != null && stack.getMetadata() != meta) {
            return false;
        }
        return nbt == null || (stack.getTagCompound() != null && NBTUtil.areNBTEquals(nbt, stack.getTagCompound(), true));
    }

    /**
     * False when the rules can never match one stack, because they require different metadata or
     * different item data at the same place.
     */
    public boolean canMatchSameStackAs(WeaponRule other) {
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

    public Item getItem() {
        return item;
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
