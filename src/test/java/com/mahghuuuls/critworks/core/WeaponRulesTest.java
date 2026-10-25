package com.mahghuuuls.critworks.core;

import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Exercises production rule matching: metadata, item data, specificity, per-value winners, and ties. */
class WeaponRulesTest {

    /** ItemStack needs the vanilla item registry. */
    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    private final Item sword = new Item().setRegistryName("test:sword");
    private final List<String> warnings = new ArrayList<String>();

    private WeaponRules rules(WeaponRule... rules) {
        return new WeaponRules(Arrays.asList(rules), warnings::add);
    }

    private static NBTTagCompound nbt(String text) throws Exception {
        return JsonToNBT.getTagFromJson(text);
    }

    @Test
    void metadataRuleMatchesOnlyThatMetadataAndWins() {
        WeaponRule any = new WeaponRule(0, sword, null, null, null, 10.0D, null);
        WeaponRule meta2 = new WeaponRule(1, sword, 2, null, null, 30.0D, null);
        WeaponRules all = rules(any, meta2);
        assertSame(meta2, all.match(new ItemStack(sword, 1, 2)).chance);
        assertSame(any, all.match(new ItemStack(sword, 1, 0)).chance);
        assertEquals(0, warnings.size());
    }

    @Test
    void itemDataRuleMatchesAsASubset() throws Exception {
        WeaponRule named = new WeaponRule(0, sword, null, nbt("{display:{Name:\"Blade\"}}"), null, 100.0D, null);
        ItemStack plain = new ItemStack(sword);
        ItemStack blade = new ItemStack(sword);
        blade.setTagCompound(nbt("{display:{Name:\"Blade\",Lore:[\"x\"]},RepairCost:1}"));
        WeaponRules all = rules(named);
        assertNull(all.match(plain).chance);
        assertSame(named, all.match(blade).chance);
    }

    @Test
    void eachValueTakesTheMostSpecificRuleThatSetsIt() {
        WeaponRule generalBoth = new WeaponRule(0, sword, null, null, null, 10.0D, 2.0D);
        WeaponRule specificChance = new WeaponRule(1, sword, 0, null, null, 40.0D, null);
        WeaponRules.Match match = rules(generalBoth, specificChance).match(new ItemStack(sword));
        assertSame(specificChance, match.chance);
        assertSame(generalBoth, match.damage);
        assertNull(match.eligible);
    }

    @Test
    void identicalRulesFirstWinsAndOneWarningAtLoad() {
        WeaponRule first = new WeaponRule(0, sword, null, null, null, 10.0D, null);
        WeaponRule second = new WeaponRule(1, sword, null, null, null, 20.0D, null);
        WeaponRules all = rules(first, second);
        assertEquals(1, warnings.size());
        assertSame(first, all.match(new ItemStack(sword)).chance);
        all.match(new ItemStack(sword));
        assertEquals(1, warnings.size());
    }

    @Test
    void differentMetadataRulesNeverTie() {
        rules(new WeaponRule(0, sword, 14, null, null, 20.0D, null),
                new WeaponRule(1, sword, 5, null, null, 30.0D, null));
        assertEquals(0, warnings.size());
    }

    @Test
    void rulesSettingDifferentValuesDoNotWarn() {
        rules(new WeaponRule(0, sword, null, null, null, 50.0D, null),
                new WeaponRule(1, sword, null, null, null, null, 2.0D));
        assertEquals(0, warnings.size());
    }

    @Test
    void equalItemDataRulesThatMayOverlapWarnAtLoadAndFirstWins() throws Exception {
        WeaponRule a = new WeaponRule(0, sword, null, nbt("{a:1b}"), null, 10.0D, null);
        WeaponRule b = new WeaponRule(1, sword, null, nbt("{b:1b}"), null, 20.0D, null);
        WeaponRules all = rules(a, b);
        assertEquals(1, warnings.size());
        ItemStack stack = new ItemStack(sword);
        stack.setTagCompound(nbt("{a:1b,b:1b}"));
        assertSame(a, all.match(stack).chance);
        assertEquals(1, warnings.size());
    }

    @Test
    void ruleOnANonWeaponWithoutEligibilityHasNoEffect() {
        Item stick = new Item().setRegistryName("test:stick");
        WeaponRule chanceOnly = new WeaponRule(0, stick, null, null, null, 50.0D, null);
        assertEquals(1, WeaponRules.rulesWithoutEffect(Arrays.asList(chanceOnly), item -> false).size());
        WeaponRule enabling = new WeaponRule(1, stick, null, null, true, null, null);
        assertEquals(0, WeaponRules.rulesWithoutEffect(Arrays.asList(chanceOnly, enabling), item -> false).size());
    }

    @Test
    void itemDataCountsMoreThanMetadata() throws Exception {
        WeaponRule meta = new WeaponRule(0, sword, 0, null, null, 10.0D, null);
        WeaponRule data = new WeaponRule(1, sword, null, nbt("{a:1b}"), null, 20.0D, null);
        WeaponRule both = new WeaponRule(2, sword, 0, nbt("{a:1b}"), null, 30.0D, null);
        ItemStack stack = new ItemStack(sword);
        stack.setTagCompound(nbt("{a:1b}"));
        assertSame(data, rules(meta, data).match(stack).chance);
        assertSame(both, rules(meta, data, both).match(stack).chance);
    }

    @Test
    void aChanceRuleOnABowHasAnEffect() {
        WeaponRule bowChance = new WeaponRule(0, net.minecraft.init.Items.BOW, null, null, null, 40.0D, null);
        assertEquals(0, WeaponRules.rulesWithoutEffect(Arrays.asList(bowChance), WeaponClassifier::isRecognizedItem).size());
        WeaponRule stickChance = new WeaponRule(1, net.minecraft.init.Items.STICK, null, null, null, 40.0D, null);
        assertEquals(1, WeaponRules.rulesWithoutEffect(Arrays.asList(stickChance), WeaponClassifier::isRecognizedItem).size());
    }
}
