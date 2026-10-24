package com.mahghuuuls.critworks.core;

import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

/**
 * Owns what a "recognized weapon" is: an item that can crit with no configuration. Swords and axes
 * count by item type, so modded subclasses count too.
 */
public final class WeaponClassifier {

    private WeaponClassifier() {
    }

    public static boolean isRecognizedMeleeWeapon(ItemStack stack) {
        return !stack.isEmpty() && isRecognizedMeleeItem(stack.getItem());
    }

    public static boolean isRecognizedMeleeItem(Item item) {
        return item instanceof ItemSword || item instanceof ItemAxe;
    }
}
