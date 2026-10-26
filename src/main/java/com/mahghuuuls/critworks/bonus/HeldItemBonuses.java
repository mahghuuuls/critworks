package com.mahghuuuls.critworks.bonus;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A mod that puts the bonuses of held items on the whole player as attribute modifiers, while Critworks
 * counts them only for attacks with their own item (ARC-006). {@link BonusCollector} leaves the held
 * items' modifiers out of the global bonuses and adds the attacking stack's own back. Internal: not part
 * of the addon API.
 */
public interface HeldItemBonuses {

    /** The integration setting key that switches this source on and off, for example "qualitytools". */
    String integrationKey();

    /**
     * The ids of the modifiers that the mod puts on the player for {@code stack} only while it is in a hand:
     * {@code MAIN_HAND} for a stack anywhere in the main inventory (the selected hotbar slot included),
     * {@code OFF_HAND} for the offhand stack. Asked for every stack, so a modifier the mod has not yet removed
     * after a swap is left out too.
     */
    Set<UUID> heldModifierIds(ItemStack stack, EnumHand hand);

    /** The bonuses {@code stack} gives an attack from {@code hand}, with their sources. */
    List<Bonus> ownBonuses(ItemStack stack, EnumHand hand);
}
