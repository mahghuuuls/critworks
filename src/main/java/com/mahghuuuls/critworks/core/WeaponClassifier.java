package com.mahghuuuls.critworks.core;

import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

/**
 * Owns what a "recognized weapon" is: an item that can crit with no configuration. Swords and axes
 * count for melee and bows for arrows, by item type, so modded subclasses count too.
 */
public final class WeaponClassifier {

    private WeaponClassifier() {
    }

    public static boolean isRecognizedWeapon(ItemStack stack, AttackKind kind) {
        if (stack.isEmpty()) {
            return false;
        }
        return kind == AttackKind.ARROW ? isRecognizedRangedItem(stack.getItem()) : isRecognizedMeleeItem(stack.getItem());
    }

    public static boolean isRecognizedMeleeItem(Item item) {
        return item instanceof ItemSword || item instanceof ItemAxe;
    }

    public static boolean isRecognizedRangedItem(Item item) {
        return item instanceof ItemBow;
    }

    /** Whether the item is recognized for any attack kind. */
    public static boolean isRecognizedItem(Item item) {
        return isRecognizedMeleeItem(item) || isRecognizedRangedItem(item);
    }
}
