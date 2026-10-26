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
    void itemDataRulesThatRequireDifferentValuesNeverTie() throws Exception {
        WeaponRule zero = new WeaponRule(0, sword, null, nbt("{display:{Name:\"Zero\"}}"), null, 0.0D, null);
        WeaponRule ten = new WeaponRule(1, sword, null, nbt("{display:{Name:\"Ten\"}}"), null, 10.0D, null);
        WeaponRules all = rules(zero, ten);
        assertEquals(0, warnings.size());
        ItemStack stack = new ItemStack(sword);
        stack.setTagCompound(nbt("{display:{Name:\"Ten\"}}"));
        assertSame(ten, all.match(stack).chance);
    }

    @Test
    void itemDataRulesThatRequireDifferentTypesNeverTie() throws Exception {
        rules(new WeaponRule(0, sword, null, nbt("{a:1b}"), null, 10.0D, null),
                new WeaponRule(1, sword, null, nbt("{a:{b:1b}}"), null, 20.0D, null));
        assertEquals(0, warnings.size());
    }

    @Test
    void itemDataRulesThatRequireTheSameValueMayOverlapAndWarn() throws Exception {
        WeaponRule a = new WeaponRule(0, sword, null, nbt("{display:{Name:\"X\"}}"), null, 10.0D, null);
        WeaponRule b = new WeaponRule(1, sword, null, nbt("{display:{Name:\"X\"},RepairCost:1}"), null, 20.0D, null);
        WeaponRules all = rules(a, b);
        assertEquals(1, warnings.size());
        ItemStack stack = new ItemStack(sword);
        stack.setTagCompound(nbt("{display:{Name:\"X\"},RepairCost:1}"));
        assertSame(a, all.match(stack).chance);
    }

    @Test
    void anEmptyListOnlyOverlapsAnEmptyList() throws Exception {
        rules(new WeaponRule(0, sword, null, nbt("{display:{Lore:[]}}"), null, 10.0D, null),
                new WeaponRule(1, sword, null, nbt("{display:{Lore:[\"a\"]}}"), null, 20.0D, null));
        assertEquals(0, warnings.size());
        rules(new WeaponRule(0, sword, null, nbt("{display:{Lore:[]}}"), null, 10.0D, null),
                new WeaponRule(1, sword, null, nbt("{display:{Lore:[]}}"), null, 20.0D, null));
        assertEquals(1, warnings.size());
    }

    @Test
    void itemDataRulesWithListsMayOverlapAndWarn() throws Exception {
        WeaponRule a = new WeaponRule(0, sword, null, nbt("{display:{Lore:[\"a\"]}}"), null, 10.0D, null);
        WeaponRule b = new WeaponRule(1, sword, null, nbt("{display:{Lore:[\"b\"]}}"), null, 20.0D, null);
        WeaponRules all = rules(a, b);
        assertEquals(1, warnings.size());
        ItemStack stack = new ItemStack(sword);
        stack.setTagCompound(nbt("{display:{Lore:[\"a\",\"b\"]}}"));
        assertSame(a, all.match(stack).chance);
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

    /** A type that matches one item class, as a Spartan type does. */
    private static ItemType type(String name, Class<? extends Item> itemClass) {
        return new ItemType() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String describe() {
                return "spartan type " + name;
            }

            @Override
            public boolean matches(Item item) {
                return itemClass.isInstance(item);
            }
        };
    }

    /** Stands in for two Spartan daggers of one type. */
    public static final class Dagger extends net.minecraft.item.ItemSword {
        public Dagger() {
            super(Item.ToolMaterial.IRON);
        }
    }

    @Test
    void aTypeRuleCoversEveryItemOfTheTypeAndAnIdRuleWinsForItsItem() {
        Dagger iron = new Dagger();
        Dagger netherite = new Dagger();
        iron.setRegistryName("spartanweaponry:dagger_iron");
        netherite.setRegistryName("spartanetherite:dagger_netherite");
        ItemType dagger = type("dagger", Dagger.class);
        WeaponRule typeRule = WeaponRule.forType(0, dagger, null, 25.0D, null);
        WeaponRule idRule = new WeaponRule(1, iron, null, null, null, 10.0D, null);
        WeaponRules all = rules(typeRule, idRule);
        assertEquals(0, warnings.size());
        assertSame(idRule, all.match(new ItemStack(iron)).chance, "REQ-086: the id rule wins for the iron dagger");
        assertSame(typeRule, all.match(new ItemStack(netherite)).chance, "the netherite dagger keeps the type rule");
        assertNull(all.match(new ItemStack(sword)).chance, "a sword of another type gets nothing");
        assertEquals("pack rule #1 (spartan type dagger)", typeRule.describe());
        assertEquals(0, typeRule.specificity());
    }

    @Test
    void twoRulesForOneTypeWarnAndRulesForDifferentTypesDoNot() {
        rules(WeaponRule.forType(0, type("dagger", Dagger.class), null, 25.0D, null),
                WeaponRule.forType(1, type("rapier", Dagger.class), null, 30.0D, null));
        assertEquals(0, warnings.size());
        rules(WeaponRule.forType(0, type("dagger", Dagger.class), null, 25.0D, null),
                WeaponRule.forType(1, type("dagger", Dagger.class), null, 30.0D, null));
        assertEquals(1, warnings.size());
    }

    @Test
    void aTypeRuleIsNeverReportedAsWithoutEffect() {
        WeaponRule typeRule = WeaponRule.forType(0, type("dagger", Dagger.class), null, 25.0D, null);
        assertEquals(0, WeaponRules.rulesWithoutEffect(Arrays.asList(typeRule), item -> false).size());
    }
}
