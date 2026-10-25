package com.mahghuuuls.critworks.bonus;

import com.mahghuuuls.critworks.api.IWeaponBonusProvider;
import com.mahghuuuls.critworks.api.WeaponBonus;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The registered weapon-specific bonus providers, asked for the attacking or shown stack only, so their
 * bonuses count only for that weapon. A provider that throws is reported once and then skipped.
 */
public final class WeaponBonusProviders {

    private final List<Registered> providers = new CopyOnWriteArrayList<Registered>();
    private final Consumer<String> warn;

    public WeaponBonusProviders(Consumer<String> warn) {
        this.warn = warn;
    }

    public void register(IWeaponBonusProvider provider, String source) {
        providers.add(new Registered(provider, source));
    }

    /** Every provider's bonuses for a stack; nothing for an empty stack. */
    List<WeaponBonus> bonusesFor(EntityPlayer player, ItemStack stack) {
        List<WeaponBonus> result = new ArrayList<WeaponBonus>();
        if (stack.isEmpty()) {
            return result;
        }
        for (Registered registered : providers) {
            registered.addTo(result, player, stack);
        }
        return result;
    }

    private final class Registered {
        private final IWeaponBonusProvider provider;
        private final String source;
        private volatile boolean broken;

        Registered(IWeaponBonusProvider provider, String source) {
            this.provider = provider;
            this.source = source;
        }

        void addTo(List<WeaponBonus> result, EntityPlayer player, ItemStack stack) {
            if (broken) {
                return;
            }
            try {
                List<WeaponBonus> bonuses = provider.getBonuses(player, stack);
                if (bonuses != null) {
                    for (WeaponBonus bonus : bonuses) {
                        if (bonus != null) {
                            result.add(bonus);
                        }
                    }
                }
            } catch (RuntimeException | LinkageError e) {
                broken = true;
                warn.accept("Critworks: a weapon bonus provider from " + source + " failed and is skipped: " + e);
            }
        }
    }
}
