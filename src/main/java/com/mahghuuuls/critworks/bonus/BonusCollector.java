package com.mahghuuuls.critworks.bonus;

import com.mahghuuuls.critworks.api.CritStat;
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
 * Owns which bonuses count for one attack. Global bonuses are the player's attribute modifiers with
 * operation 0; a modifier that is not a finite number is left out and reported once per source.
 */
public final class BonusCollector {

    private static final int ADD = 0;

    private final Logger logger;
    private final Set<String> reported = Collections.synchronizedSet(new HashSet<String>());

    public BonusCollector(Logger logger) {
        this.logger = logger;
    }

    /** Every bonus that counts for an attack by {@code player} with {@code stack}; empty for no player. */
    public List<Bonus> collect(EntityPlayer player, ItemStack stack, EnumHand hand) {
        if (player == null) {
            return new ArrayList<Bonus>();
        }
        return globalBonuses(player::getEntityAttribute);
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
