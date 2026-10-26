package com.mahghuuuls.critworks.core;

import com.mahghuuuls.critworks.api.CritStat;
import com.mahghuuuls.critworks.bonus.Bonus;
import com.mahghuuuls.critworks.bonus.BonusCollector;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import java.util.List;

/**
 * The single owner of stat resolution: weapon eligibility, base stat precedence (pack rule, then addon
 * default, then fallback; each stat on its own), bonus collection, and the formula. Every consumer (attacks,
 * inspection, tooltips, diagnostics) asks this class.
 */
public final class StatResolver {

    public static final String SOURCE_FALLBACK = "fallback";
    public static final String REASON_NOT_A_WEAPON = "not a recognized weapon";
    public static final String SOURCE_RECOGNIZED = "recognized weapon";

    private final CritSettings settings;
    private final WeaponRules rules;
    private final BonusCollector bonuses;
    private final AddonDefaults defaults;

    public StatResolver(CritSettings settings, WeaponRules rules, BonusCollector bonuses) {
        this(settings, rules, bonuses, new AddonDefaults(message -> { }));
    }

    public StatResolver(CritSettings settings, WeaponRules rules, BonusCollector bonuses, AddonDefaults defaults) {
        this.settings = settings;
        this.rules = rules;
        this.bonuses = bonuses;
        this.defaults = defaults;
    }

    /** Resolves the stats for the stack's own attack kind (arrows for a bow, melee otherwise). */
    public CritProfile resolve(EntityPlayer player, ItemStack stack, EnumHand hand) {
        return resolve(player, stack, hand, AttackKind.naturalFor(stack));
    }

    /**
     * Resolves the stats an attack of {@code kind} by {@code player} with {@code stack} in {@code hand} would
     * use now. Never changes game state.
     */
    public CritProfile resolve(EntityPlayer player, ItemStack stack, EnumHand hand, AttackKind kind) {
        WeaponRules.Match match = rules.match(stack);
        String eligibilitySource = SOURCE_RECOGNIZED;
        if (match.eligible != null) {
            if (!match.eligible.getEligible()) {
                return CritProfile.notEligible("turned off by " + match.eligible.describe());
            }
            eligibilitySource = match.eligible.describe();
        } else if (!WeaponClassifier.isRecognizedWeapon(stack, kind)) {
            return CritProfile.notEligible(REASON_NOT_A_WEAPON);
        }

        AddonDefaults.Match addon = defaults.match(stack);
        double baseChance = settings.getFallbackChance();
        String chanceSource = SOURCE_FALLBACK;
        if (match.chance != null) {
            baseChance = match.chance.getChance();
            chanceSource = match.chance.describe();
        } else if (addon.chance != null) {
            baseChance = addon.chance.getChance();
            chanceSource = addon.chance.describe();
        }
        double baseMultiplier = settings.getFallbackMultiplier();
        String damageSource = SOURCE_FALLBACK;
        if (match.damage != null) {
            baseMultiplier = match.damage.getDamage();
            damageSource = match.damage.describe();
        } else if (addon.damage != null) {
            baseMultiplier = addon.damage.getDamage();
            damageSource = addon.damage.describe();
        }

        List<Bonus> counted = bonuses.collect(player, stack, hand, settings::isIntegrationEnabled);
        double cap = settings.getDamageCap();
        double finalChance = CritFormula.finalChance(baseChance,
                BonusCollector.total(counted, CritStat.INCREASED_CHANCE),
                BonusCollector.total(counted, CritStat.ADDITIONAL_CHANCE));
        double finalMultiplier = CritFormula.finalMultiplier(baseMultiplier,
                BonusCollector.total(counted, CritStat.INCREASED_DAMAGE),
                BonusCollector.total(counted, CritStat.ADDITIONAL_DAMAGE), cap);
        return CritProfile.eligible(eligibilitySource, baseChance, chanceSource, baseMultiplier, damageSource,
                finalChance, finalMultiplier, cap, counted);
    }
}
