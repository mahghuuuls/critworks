package com.mahghuuuls.critworks.bonus;

import com.mahghuuuls.critworks.api.IWeaponBonusProvider;
import com.mahghuuuls.critworks.api.WeaponBonus;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The registered weapon-specific bonus providers, asked for the attacking or shown stack only, so their
 * bonuses count only for that weapon. A provider that throws is reported once and then skipped.
 */
public final class WeaponBonusProviders {

    private final List<Registered> providers;
    private final Set<Registered> broken = Collections.newSetFromMap(new ConcurrentHashMap<Registered, Boolean>());
    private final Consumer<String> warn;

    public WeaponBonusProviders(Consumer<String> warn) {
        this(new CopyOnWriteArrayList<Registered>(), warn);
    }

    private WeaponBonusProviders(List<Registered> providers, Consumer<String> warn) {
        this.providers = providers;
        this.warn = warn;
    }

    /**
     * The same providers with their own record of failures. The client's tooltips use it, so a provider
     * that fails only for a tooltip is still asked for attacks in the same game (REQ-095).
     */
    public WeaponBonusProviders withOwnFailures() {
        return new WeaponBonusProviders(providers, warn);
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
            registered.addTo(result, player, stack, broken, warn);
        }
        return result;
    }

    private static final class Registered {
        private final IWeaponBonusProvider provider;
        private final String source;

        Registered(IWeaponBonusProvider provider, String source) {
            this.provider = provider;
            this.source = source;
        }

        void addTo(List<WeaponBonus> result, EntityPlayer player, ItemStack stack, Set<Registered> broken,
                   Consumer<String> warn) {
            if (broken.contains(this)) {
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
                broken.add(this);
                warn.accept("Critworks: a weapon bonus provider from " + source + " failed and is skipped: " + e);
            }
        }
    }
}
