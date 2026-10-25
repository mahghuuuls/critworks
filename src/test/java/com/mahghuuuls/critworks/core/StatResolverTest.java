package com.mahghuuuls.critworks.core;

import com.mahghuuuls.critworks.bonus.BonusCollector;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the production resolver: recognition, eligibility rules, and per-stat precedence. */
class StatResolverTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    private static StatResolver resolver(WeaponRule... rules) {
        return new StatResolver(CritSettings.defaults(), new WeaponRules(Arrays.asList(rules), s -> { }),
                new BonusCollector(LogManager.getLogger("test")));
    }

    private static CritProfile resolve(StatResolver resolver, ItemStack stack) {
        return resolver.resolve(null, stack, EnumHand.MAIN_HAND);
    }

    @Test
    void swordUsesTheFallbackWithNoRules() {
        CritProfile profile = resolve(resolver(), new ItemStack(Items.IRON_SWORD));
        assertTrue(profile.isEligible());
        assertEquals(5.0D, profile.getFinalChance());
        assertEquals(1.5D, profile.getFinalMultiplier());
        assertEquals(StatResolver.SOURCE_FALLBACK, profile.getBaseChanceSource());
        assertEquals(StatResolver.SOURCE_RECOGNIZED, profile.getEligibilitySource());
    }

    @Test
    void stickAndEmptyHandAreNotWeapons() {
        assertFalse(resolve(resolver(), new ItemStack(Items.STICK)).isEligible());
        assertFalse(resolve(resolver(), ItemStack.EMPTY).isEligible());
    }

    @Test
    void ruleCanMakeAStickEligibleWithItsOwnChance() {
        CritProfile profile = resolve(resolver(new WeaponRule(0, Items.STICK, null, null, true, 50.0D, null)),
                new ItemStack(Items.STICK));
        assertTrue(profile.isEligible());
        assertEquals(50.0D, profile.getFinalChance());
        assertEquals(1.5D, profile.getFinalMultiplier());
        assertEquals(StatResolver.SOURCE_FALLBACK, profile.getBaseMultiplierSource());
        assertTrue(profile.getEligibilitySource().startsWith("pack rule #1"));
    }

    @Test
    void ruleCanTurnASwordOff() {
        CritProfile profile = resolve(resolver(new WeaponRule(0, Items.DIAMOND_SWORD, null, null, false, null, null)),
                new ItemStack(Items.DIAMOND_SWORD));
        assertFalse(profile.isEligible());
        assertTrue(profile.getIneligibleReason().startsWith("turned off by pack rule #1"));
    }

    @Test
    void chanceOnlyRuleOnAStickDoesNotMakeItEligible() {
        assertFalse(resolve(resolver(new WeaponRule(0, Items.STICK, null, null, null, 50.0D, null)),
                new ItemStack(Items.STICK)).isEligible());
    }

    @Test
    void eachStatFallsBackOnItsOwn() {
        CritProfile profile = resolve(resolver(new WeaponRule(0, Items.IRON_SWORD, null, null, null, 8.0D, null)),
                new ItemStack(Items.IRON_SWORD));
        assertEquals(8.0D, profile.getBaseChance());
        assertEquals(1.5D, profile.getBaseMultiplier());
        assertEquals(StatResolver.SOURCE_FALLBACK, profile.getBaseMultiplierSource());
    }

    @Test
    void aBowIsRecognizedForArrowsOnly() {
        StatResolver resolver = resolver();
        ItemStack bow = new ItemStack(Items.BOW);
        assertTrue(resolver.resolve(null, bow, EnumHand.MAIN_HAND, AttackKind.ARROW).isEligible());
        assertTrue(resolve(resolver, bow).isEligible(), "a bow resolves for arrows by default");
        CritProfile melee = resolver.resolve(null, bow, EnumHand.MAIN_HAND, AttackKind.MELEE);
        assertFalse(melee.isEligible());
        assertEquals(StatResolver.REASON_NOT_A_WEAPON, melee.getIneligibleReason());
    }

    @Test
    void aSwordIsNotARangedWeapon() {
        assertFalse(resolver().resolve(null, new ItemStack(Items.IRON_SWORD), EnumHand.MAIN_HAND, AttackKind.ARROW)
                .isEligible());
    }

    @Test
    void bowRuleValuesApplyToItsArrows() {
        CritProfile profile = resolver(new WeaponRule(0, Items.BOW, null, null, null, 40.0D, 2.5D))
                .resolve(null, new ItemStack(Items.BOW), EnumHand.MAIN_HAND, AttackKind.ARROW);
        assertEquals(40.0D, profile.getFinalChance());
        assertEquals(2.5D, profile.getFinalMultiplier());
    }
}
