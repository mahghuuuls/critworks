package com.mahghuuuls.critworks.api;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Gives weapon-specific bonuses: bonuses that count only for attacks made with that stack, in either hand,
 * and for its tooltip. Critworks asks every provider whenever it resolves a stack, on the server and on the
 * client, so a provider must be cheap and must not change any state. Its bonuses are never written to the
 * player's attributes.
 */
public interface IWeaponBonusProvider {

    /**
     * @param player the attacker or the tooltip's player; null when the attacker is not a player
     * @param stack  the attacking or shown stack, never empty
     * @return the stack's bonuses, or an empty list
     */
    List<WeaponBonus> getBonuses(@Nullable EntityPlayer player, ItemStack stack);
}
