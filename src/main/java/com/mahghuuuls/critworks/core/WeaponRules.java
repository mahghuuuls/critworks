package com.mahghuuuls.critworks.core;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Owns rule matching. For each value (eligibility, chance, damage) the most specific matching rule
 * that sets it wins; between equally specific rules the first in the file wins. Possible ties are
 * found and reported once at load, so matching during play changes nothing and is safe from any
 * thread. Rules are grouped by item, so a lookup only looks at the rules of the stack's item.
 */
public final class WeaponRules {

    private static final WeaponRules EMPTY = new WeaponRules(Collections.<WeaponRule>emptyList(), s -> { });

    private final List<WeaponRule> rules;
    private final Map<Item, List<WeaponRule>> byItem = new IdentityHashMap<Item, List<WeaponRule>>();

    /** Rules in file order. */
    public WeaponRules(List<WeaponRule> rules, Consumer<String> warnings) {
        this.rules = Collections.unmodifiableList(new ArrayList<WeaponRule>(rules));
        for (WeaponRule rule : this.rules) {
            List<WeaponRule> list = byItem.get(rule.getItem());
            if (list == null) {
                list = new ArrayList<WeaponRule>();
                byItem.put(rule.getItem(), list);
            }
            list.add(rule);
        }
        reportPossibleTies(warnings);
    }

    public static WeaponRules empty() {
        return EMPTY;
    }

    public List<WeaponRule> all() {
        return rules;
    }

    /** The values the rules set for this stack; each part may be absent. */
    public Match match(ItemStack stack) {
        WeaponRule eligible = null;
        WeaponRule chance = null;
        WeaponRule damage = null;
        List<WeaponRule> candidates = stack.isEmpty() ? null : byItem.get(stack.getItem());
        if (candidates != null) {
            for (WeaponRule rule : candidates) {
                if (!rule.matches(stack)) {
                    continue;
                }
                if (rule.getEligible() != null) {
                    eligible = better(eligible, rule);
                }
                if (rule.getChance() != null) {
                    chance = better(chance, rule);
                }
                if (rule.getDamage() != null) {
                    damage = better(damage, rule);
                }
            }
        }
        return new Match(eligible, chance, damage);
    }

    /** Earlier rules win ties, because candidates are in file order and only a higher specificity replaces. */
    private static WeaponRule better(WeaponRule current, WeaponRule candidate) {
        return current == null || candidate.specificity() > current.specificity() ? candidate : current;
    }

    /**
     * Two rules for the same item with the same specificity that set the same value can both match one
     * stack (always when their conditions are identical, sometimes when their item data differs).
     */
    private void reportPossibleTies(Consumer<String> warnings) {
        for (List<WeaponRule> list : byItem.values()) {
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    WeaponRule first = list.get(i);
                    WeaponRule second = list.get(j);
                    if (first.specificity() != second.specificity() || !first.canMatchSameStackAs(second)) {
                        continue;
                    }
                    String shared = sharedValues(first, second);
                    if (!shared.isEmpty()) {
                        warnings.accept("Critworks config weapons.json: rules #" + (first.getIndex() + 1) + " and #"
                                + (second.getIndex() + 1) + " can match the same item equally for " + shared
                                + "; rule #" + (first.getIndex() + 1) + " wins");
                    }
                }
            }
        }
    }

    private static String sharedValues(WeaponRule a, WeaponRule b) {
        List<String> shared = new ArrayList<String>();
        add(shared, "eligible", a.getEligible(), b.getEligible());
        add(shared, "chance", a.getChance(), b.getChance());
        add(shared, "damage", a.getDamage(), b.getDamage());
        return String.join(", ", shared);
    }

    private static void add(List<String> shared, String name, Object a, Object b) {
        if (a != null && b != null) {
            shared.add(name);
        }
    }

    /** Rules that set chance or damage for an item that is neither a recognized weapon nor made eligible by a rule. */
    public static List<WeaponRule> rulesWithoutEffect(List<WeaponRule> rules, Function<Item, Boolean> recognized) {
        List<WeaponRule> result = new ArrayList<WeaponRule>();
        for (WeaponRule rule : rules) {
            if (rule.getEligible() == null && !recognized.apply(rule.getItem()) && !hasEnablingRule(rules, rule)) {
                result.add(rule);
            }
        }
        return result;
    }

    private static boolean hasEnablingRule(List<WeaponRule> rules, WeaponRule rule) {
        for (WeaponRule other : rules) {
            if (other.getItem() == rule.getItem() && Boolean.TRUE.equals(other.getEligible())) {
                return true;
            }
        }
        return false;
    }

    /** The winning rule for each value, or null when no matching rule sets it. */
    public static final class Match {
        public final WeaponRule eligible;
        public final WeaponRule chance;
        public final WeaponRule damage;

        Match(WeaponRule eligible, WeaponRule chance, WeaponRule damage) {
            this.eligible = eligible;
            this.chance = chance;
            this.damage = damage;
        }
    }
}
