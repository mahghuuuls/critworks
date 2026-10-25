package com.mahghuuuls.critworks.core;

import com.mahghuuuls.critworks.bonus.BonusCollector;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.EnumHand;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The three-tier base stat precedence with addon defaults (REQ-012), and attacker policies (REQ-015). */
class AddonApiRulesTest {

    private final List<String> warnings = new ArrayList<String>();

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    private StatResolver resolver(AddonDefaults defaults, WeaponRule... rules) {
        return new StatResolver(CritSettings.defaults(), new WeaponRules(Arrays.asList(rules), s -> { }),
                new BonusCollector(LogManager.getLogger("test")), defaults);
    }

    @Test
    void eachStatComesFromPackRuleThenAddonDefaultThenFallback() {
        AddonDefaults defaults = new AddonDefaults(warnings::add);
        defaults.register(Items.IRON_SWORD, null, 20.0D, 2.0D, "testmod");
        CritProfile profile = resolver(defaults, new WeaponRule(0, Items.IRON_SWORD, null, null, null, 8.0D, null))
                .resolve(null, new ItemStack(Items.IRON_SWORD), EnumHand.MAIN_HAND);
        assertEquals(8.0D, profile.getBaseChance());
        assertEquals("pack rule #1 minecraft:iron_sword", profile.getBaseChanceSource());
        assertEquals(2.0D, profile.getBaseMultiplier());
        assertEquals("addon default from testmod", profile.getBaseMultiplierSource());

        CritProfile axe = resolver(defaults).resolve(null, new ItemStack(Items.IRON_AXE), EnumHand.MAIN_HAND);
        assertEquals(StatResolver.SOURCE_FALLBACK, axe.getBaseChanceSource(), "a default for another item does not apply");
    }

    @Test
    void anAddonDefaultDoesNotMakeAnItemEligible() {
        AddonDefaults defaults = new AddonDefaults(warnings::add);
        defaults.register(Items.STICK, null, 50.0D, null, "testmod");
        assertFalse(resolver(defaults).resolve(null, new ItemStack(Items.STICK), EnumHand.MAIN_HAND).isEligible());
    }

    @Test
    void itemDefaultsBeatPredicateDefaultsAndTheFirstRegisteredWins() {
        AddonDefaults defaults = new AddonDefaults(warnings::add);
        defaults.register(null, stack -> stack.getItem() instanceof ItemSword, 30.0D, 3.0D, "predicates");
        defaults.register(Items.IRON_SWORD, null, 10.0D, null, "first");
        defaults.register(Items.IRON_SWORD, null, 15.0D, null, "second");
        CritProfile sword = resolver(defaults).resolve(null, new ItemStack(Items.IRON_SWORD), EnumHand.MAIN_HAND);
        assertEquals(10.0D, sword.getBaseChance());
        assertEquals("addon default from first", sword.getBaseChanceSource());
        assertEquals(3.0D, sword.getBaseMultiplier(), "a stat no item default sets comes from the predicate default");
        assertEquals("addon default from predicates", sword.getBaseMultiplierSource());
    }

    @Test
    void aPredicateThatThrowsIsReportedOnceAndSkipped() {
        AddonDefaults defaults = new AddonDefaults(warnings::add);
        defaults.register(null, stack -> {
            throw new IllegalStateException("broken");
        }, 30.0D, null, "badmod");
        StatResolver resolver = resolver(defaults);
        for (int i = 0; i < 3; i++) {
            assertEquals(StatResolver.SOURCE_FALLBACK,
                    resolver.resolve(null, new ItemStack(Items.IRON_SWORD), EnumHand.MAIN_HAND).getBaseChanceSource());
        }
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("badmod"), warnings.get(0));
    }

    @Test
    void otherAttackersNeedAPolicy() {
        AttackerPolicies policies = new AttackerPolicies(warnings::add);
        assertTrue(policies.isEmpty());
        assertFalse(policies.allows(null), "without a policy only players crit");
        policies.register(attacker -> true, "testmod");
        assertTrue(policies.allows(null));
    }

    @Test
    void aPolicyThatThrowsIsReportedOnceAndCountsAsNo() {
        AttackerPolicies policies = new AttackerPolicies(warnings::add);
        policies.register(attacker -> {
            throw new IllegalStateException("broken");
        }, "badmod");
        assertFalse(policies.allows(null));
        assertFalse(policies.allows(null));
        assertEquals(1, warnings.size());
    }
}
