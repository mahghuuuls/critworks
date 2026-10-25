package com.mahghuuuls.critworks.bonus;

import com.mahghuuuls.critworks.api.CritStat;
import com.mahghuuuls.critworks.api.WeaponBonus;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Owns which bonuses count for one attack: the player's global bonuses (attribute modifiers with operation
 * 0) and the attacking stack's own weapon-specific bonuses from the registered providers. A bonus that is
 * not a finite number is left out and reported once per source.
 */
public final class BonusCollector {

    private static final int ADD = 0;

    private final Logger logger;
    private final WeaponBonusProviders providers;
    private final Set<String> reported = Collections.synchronizedSet(new HashSet<String>());

    public BonusCollector(Logger logger) {
        this(logger, new WeaponBonusProviders(logger::warn));
    }

    public BonusCollector(Logger logger, WeaponBonusProviders providers) {
        this.logger = logger;
        this.providers = providers;
    }

    /**
     * Every bonus that counts for an attack by {@code player} (null for another attacker) with {@code stack}:
     * the player's global bonuses, then the stack's own weapon-specific bonuses.
     */
    public List<Bonus> collect(EntityPlayer player, ItemStack stack, EnumHand hand) {
        List<Bonus> bonuses = player == null ? new ArrayList<Bonus>() : globalBonuses(player::getEntityAttribute);
        for (WeaponBonus bonus : providers.bonusesFor(player, stack)) {
            add(bonuses, bonus.getStat(), bonus.getAmount(), bonus.getSource());
        }
        return bonuses;
    }

    /** The global bonuses read from a player's attribute instances. */
    List<Bonus> globalBonuses(Function<IAttribute, IAttributeInstance> attributes) {
        List<Bonus> bonuses = new ArrayList<Bonus>();
        for (CritStat stat : CritStat.values()) {
            IAttributeInstance instance = attributes.apply(CritAttributes.attribute(stat));
            if (instance == null) {
                continue;
            }
            for (AttributeModifier modifier : instance.getModifiersByOperation(ADD)) {
                add(bonuses, stat, modifier.getAmount(), modifier.getName());
            }
        }
        return bonuses;
    }

    private void add(List<Bonus> bonuses, CritStat stat, double amount, String source) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            if (reported.add(source + ":" + stat)) {
                logger.warn("Critworks ignores a {} bonus from {} that is not a finite number", stat.getLabel(), source);
            }
            return;
        }
        bonuses.add(new Bonus(stat, amount, source));
    }

    /** Sum of the bonuses on one stat. */
    public static double total(List<Bonus> bonuses, CritStat stat) {
        double total = 0.0D;
        for (Bonus bonus : bonuses) {
            if (bonus.getStat() == stat) {
                total += bonus.getAmount();
            }
        }
        return total;
    }
}
