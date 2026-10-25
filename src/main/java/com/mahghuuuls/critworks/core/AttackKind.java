package com.mahghuuuls.critworks.core;

import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;

/** The supported attack kinds. A weapon is recognized for its own kind: swords and axes in melee, bows for arrows. */
public enum AttackKind {
    MELEE,
    ARROW;

    /** The kind a stack attacks with on its own: arrows for a bow, melee for everything else. */
    public static AttackKind naturalFor(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemBow ? ARROW : MELEE;
    }
}
